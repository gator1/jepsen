(ns file-r.core
  (:require [clojure.tools.logging :refer :all]
            [jepsen [core :as jepsen]
             [client :as client]
             [nemesis :as nemesis]
             [generator :as gen]
             [checker :as checker]
             [control :as c]
             [tests :as tests]
             [independent :as independent]
             [util :refer [timeout]]]
            [knossos.model :refer [cas-register]]
            [knossos.history :as history]
            [clojure.edn :as edn])
  (:use     [clojure.java.shell :only [sh]]) )

; define path
(def ^:private file-path "/home/gary/jepsen/file-s/mount/n")
(def ^:private temp-path "/home/gary/jepsen/file-s/data/")

; define operations
(defn r   [_ _] {:type :invoke, :f :read, :value nil})
(defn w   [_ _] {:type :invoke, :f :write, :value (rand-int 5)})
(defn cas [_ _] {:type :invoke, :f :cas, :value [(rand-int 5)(rand-int 5)]})

(defn nid
  [n]
  (->> (str n)
       (re-find #"\d+")
       (Integer. )))

(defn get-reg
  [loc]
  (info (str "get-reg" loc "\n"))
  (->> (sh "cat" loc)
       :out
       edn/read-string))

(defn set-reg
  [loc val]
  (sh "sh" "-c" (str "echo " val " > " loc)))

(defn location
  [n]
  (str file-path (nid n) "/data"))

; client for nfs based
(defn client-nfs
  [loc]
  (reify client/Client
    (setup! [_ test node]
      (let [loc (location node)]
        (set-reg loc 0)
        (client-nfs loc)))

    (invoke! [this test op]
      (timeout 5000 (assoc op :type :info, :error :timeout)
               (case (:f op)
                 :read  (assoc op :type :ok, :value (get-reg loc))

                 :write (do (set-reg loc (:value op))
                            (assoc op :type :ok))

                 :cas   (let [[value value'] (:value op)]
                          (if (= (get-reg loc) value)
                            (do (set-reg loc value')
                                (assoc op :type :ok))
                            (assoc op :type :fail))))))

    (teardown! [_ test])))

(def ^:private key-list [10 20 30 40 50])
(def ^:private skip-size 512) ; 256k=512*512b

; operations for multi-register
(defn rm
  [_ _]
  (let [k (rand-nth key-list)]
    {:type :invoke :f :txn :value (independent/tuple k [[:read k nil]])}))

(defn wm
  [_ _]
  (let [k (rand-nth key-list)
        v (+ 1 (rand-int 255))]
    {:type :invoke :f :txn :value (independent/tuple k [[:write k v]])}))

(defn get-multi-data
  [n key]
  (let [temp (str temp-path "temp" n)
        data (str file-path n "/data")
        pos (* skip-size (- (/ key 10) 1))]

    (let [ret (->> (str "cat " temp)
;                   (str "dd if=" data " skip=" pos " of=" temp " count=1 iflag=direct;")
                   (str "dd if=" data " skip=" pos " of=" temp " count=1;")
                   (sh "sh" "-c"))]
      (if (= 0 (:exit ret)) (edn/read-string (:out ret)) -1))))

(defn set-multi-data
  [n key val]
  (let [temp (str temp-path "temp" n)
        data (str file-path n "/data")
        pos (* skip-size (- (/ key 10) 1))]
;    (let [ret (->> (str "dd if=" temp " of=" data " seek=" pos " count=1 oflag=direct conv=notrunc")
    (let [ret (->> (str "dd if=" temp " of=" data " seek=" pos " count=1 conv=notrunc")
                   (str "echo " val " > " temp ";")
                   (sh "sh" "-c"))]
      (if (= 0 (:exit ret)) 0 -1))))

(defn init-multi-data
  [n count]
  (let [data (str file-path n "/data")]
    (sh "sh" "-c" (str "dd if=/dev/zero of=" data " count=" count))
    (doseq [k key-list] (set-multi-data n k 0))))

; client for nfs multi-register
(defn client-multi
  [n]
  (reify client/Client
    (setup! [this test node]
      (init-multi-data (nid node) 4096) ;move to core_test in real
      (client-multi (nid node)))

    (invoke! [this test op]
      (timeout 5000 (assoc op :type :info :error :timeout)
               (case (:f op)
                 :txn (let [[key [[f k v]]] (:value op)]
                        (case f
                          :read (let [v (get-multi-data n key)
                                      t (independent/tuple key [[:read k v]])]
                                  (if (> 0 v) (assoc op :type :fail)
                                              (assoc op :type :ok, :value t)))

                          :write (let [r (set-multi-data n key v)]
                                   (if (> 0 r) (assoc op :type :fail)
                                               (assoc op :type :ok)))
                          )))))

    (teardown! [_ test])))

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

