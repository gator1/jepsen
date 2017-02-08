(ns file.core
  (:require [clojure.tools.logging :refer :all]
            [jepsen [core :as jepsen]
             [client :as client]
             [nemesis :as nemesis]
             [generator :as gen]
             [checker :as checker]
             [control :as c]
             [independent :as independent]
             [tests :as tests]
             [util :refer [timeout]]]
            [knossos.model :refer [cas-register]]
            [knossos.history :as history]
            [clojure.edn :as edn])
  (:use     [clojure.java.shell :only [sh]]) )

; define path
(def op-cmd "python /home/jepsen/bin/cas2.0.py -p ")
(def data-path "/home/jepsen/ostor/")
(def temp-path "/home/jepsen/jepsen/file/temp/")

; define operations
(defn r   [_ _] {:type :invoke, :f :read, :value nil})
(defn w   [_ _] {:type :invoke, :f :write, :value (rand-int 5)})
(defn cas [_ _] {:type :invoke, :f :cas, :value [(rand-int 5)(rand-int 5)]})

(defn nid
  [n]
  (->> (str n)
       (re-find #"\d+")
       (Integer. )))

(defn location
  [n]
  (str data-path "n" (nid n) "/data"))

(defn read-data
  [loc]
  (let [ret (sh "sh" "-c" (str op-cmd loc " -t r"))]
(println "exit=" (:exit ret) "out=" (:out ret))
    (if (= (:exit ret) 0) (edn/read-string (:out ret)) -1)))

(defn write-data
  [loc v]
  (let [ret (sh "sh" "-c" (str op-cmd loc " -t w -w " v))]
    (if (= (:exit ret) 0) 0 -1)))

(defn cas-data
  [loc v v']
  (let [ret (sh "sh" "-c" (str op-cmd loc " -t c -r " v " -w " v'))]
    (if (not= (:exit ret) 0) -1
      (if (< (edn/read-string (:out ret)) 0) -1 0))))

(defn init-data
  []
  (write-data (location :n1) 0))

; client for nfs based
(defn client-nfs
  [loc]
  (reify client/Client
    (setup! [_ test node]
      (let [loc (location node)]
;        (set-reg loc 0)
        (client-nfs loc)))

    (invoke! [this test op]
      (timeout 10000 (assoc op :type :info, :error :timeout)
               (case (:f op)
                 :read  (let [ret (read-data loc)]
                          (if (< ret 0) (assoc op :type :fail, :value (:value op))
                                        (assoc op :type :ok, :value ret)))

                 :write (let [ret (write-data loc (:value op))]
                          (if (< ret 0) (assoc op :type :fail)
                                        (assoc op :type :ok)))

                 :cas   (let [[value value'] (:value op)
                              ret (cas-data loc value value')]
                          (if (< ret 0) (assoc op :type :fail)
                                        (assoc op :type :ok))))))

    (teardown! [_ test]))
  )

; definitions for multi-register
(def key-list [1 2 3 4 5 6 7 8])
(def ^:private pos-list [0 255 256 257 1023 1024 1025 4095])

; operations for multi-register
(defn rm [_ _]
  (let [k (rand-nth key-list)]
    {:type :invoke :f :txn :value (independent/tuple k [[:read k nil]])}))

(defn wm [_ _]
  (let [k (rand-nth key-list)
        v (+ 1 (rand-int 9))]
    {:type :invoke :f :txn :value (independent/tuple k [[:write k v]])}))

; get data on specific position from disk
(defn get-multi-data
  [n key proc]
  (let [temp (str temp-path "temp" key "-" proc)
        data (str data-path "n" n "/data")
        pos (nth pos-list (dec key))
        ret (sh "sh" "-c" (str "dd if=" data " skip=" pos " of=" temp " count=1 iflag=direct"))]
    (if (= 0 (:exit ret)) (edn/read-string (:out (sh "sh" "-c" (str "cat " temp)))) -1)))

(defn set-multi-data
  [n key proc val]
  (let [temp (str temp-path "temp" key "-" proc)
        data (str data-path "n" n "/data")
        pos (nth pos-list (dec key))
        ret (->> (str "dd if=" temp " of=" data " seek=" pos " count=1 oflag=direct conv=notrunc")
                 (str "cat <(echo " val ") <(dd if=/dev/zero bs=1 count=510) > " temp " ;")
                 (sh "bash" "-c"))]
    (if (= 0 (:exit ret)) 0 -1)))

(defn init-multi-data
  [n count]
  (let [data (str data-path "n" n "/data")]
    (sh "sh" "-c" (str "dd if=/dev/zero of=" data " count=" count))
    (doseq [k key-list] (set-multi-data n k 0 0))))

(defn get-multi-all
  [n]
  (doseq [k key-list]
    (let [v (get-multi-data n k 0)]
      (println "key: " k, "value: " v))))

; client for nfs multi-register
(defn client-multi
  [n]
  (reify client/Client
    (setup! [this test node]
      (client-multi (nid node)))

    (invoke! [this test op]
      (timeout 5000 (assoc op :type :info :error :timeout)
               (case (:f op)
                 :txn (let [[key [[f k v]]] (:value op)
                            proc (:process op)]
                        (case f
                          :read (let [r (get-multi-data n key proc)]
                                  (if (> 0 r) (assoc op :type :fail, :value (independent/tuple key [[:read k v]]))
                                              (assoc op :type :ok,   :value (independent/tuple key [[:read k r]]))))

                          :write (let [r (set-multi-data n key proc v)
                                       t (independent/tuple key [[:write k v]])]
                                   (if (> 0 r) (assoc op :type :fail :value t)
                                               (assoc op :type :ok   :value t)))
                          )))))

    (teardown! [_ test])
  ))

; partition node for perf test
(defn split-node
  [n nodes]
  (let [coll (remove (fn [x] (= x n)) nodes)]
    [[n], coll]))

; partition specific node
(defn partition-node
  [n]
  (nemesis/partitioner (comp nemesis/complete-grudge (partial split-node n))))

; generate n normal operations
(defn op-limit
  [n gen]
  (let [life (atom (inc n))]
    (reify gen/Generator
      (op [_ test process]
        (if (= process :nemesis)
          (when (pos? @life)
            (gen/op gen test process))
          (when (pos? (swap! life dec))
            (gen/op gen test process)))))))

; checker for perf test
(defn total-time
  [history]
  (loop [pairs (history/pairs history)
         cnt   0
         total 0]
    (if (nil? pairs)
      {:writes cnt :total-time total}
      (let [[invoke complete] (first pairs)
            pairs (next pairs)]
        (if (= :invoke (:type invoke))
          (recur pairs (inc cnt) (+ total (- (:time complete) (:time invoke))))
          (recur pairs cnt total))))))

(def perf-checker
  (reify checker/Checker
    (check [_ test model history opts]
      (merge {:valid? true} (total-time history)))))
