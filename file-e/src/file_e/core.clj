(ns file-e.core
  (:gen-class)
  (:require [clojure.tools.logging :refer :all]
            [jepsen [core :as jepsen]
             [client :as client]
             [nemesis :as nemesis]
             [generator :as gen]
             [checker :as checker]
             [cli        :as cli]
             [control :as c]
             [tests :as tests]
             [independent :as independent]
             [util :refer [timeout]]]
            [knossos.model :refer [cas-register]]
            [knossos.history :as history]
            [knossos.model :refer [cas-register, multi-register]]
            [jepsen.checker.timeline :as timeline]
            [file-e.net :as net]
            [file-e.nemesis :as nemesis0]
            [file-e.util :as util]
            [clojure.edn :as edn])
  (:use     [clojure.java.shell :only [sh]]) )

(def ^:private temp-path "/home/gary/mike/gator1/jepsen/file-e/data/")

; define operations
(defn r   [_ _] {:type :invoke, :f :read, :value nil})
(defn w   [_ _] {:type :invoke, :f :write, :value (rand-int 5)})
(defn cas [_ _] {:type :invoke, :f :cas, :value [(rand-int 5)(rand-int 5)]})

(defn get-reg
  [loc]
  (Integer. (re-find  #"\d+" (slurp loc))))

;    (->> (sh "cat" loc)
;      :out
;      edn/read-string)))

; client for nfs based
(defn client-nfs
  [loc]
  (reify client/Client
    (setup! [_ test node]
      (let [loc (util/location node)]
        (util/set-reg loc 0)
        (client-nfs loc)))
    
    (invoke! [this test op]
      (timeout 5000 (assoc op :type :info, :error :timeout)
               (case (:f op)
                 :read  (assoc op :type :ok, :value (get-reg loc))
                 
                 :write (do (util/set-reg loc (:value op))
                          (assoc op :type :ok))
                 
                 :cas   (let [[value value'] (:value op)]
                          (if (= (get-reg loc) value)
                            (do (util/set-reg loc value')
                              (assoc op :type :ok))
                            (assoc op :type :fail))))))
    
    (teardown! [_ test])))

(def key-list [10 20 30 40 50])
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
        data (str util/file-path n "/data")
        pos (* skip-size (- (/ key 10) 1))]
    
    (let [ret (->> (str "cat " temp)
                ;                   (str "dd if=" data " skip=" pos " of=" temp " count=1 iflag=direct;")
                (str "dd if=" data " skip=" pos " of=" temp " count=1;")
                (sh "sh" "-c"))]
      (if (= 0 (:exit ret)) (edn/read-string (:out ret)) -1))))

(defn set-multi-data
  [n key val]
  (let [temp (str temp-path "temp" n)
        data (str util/file-path n "/data")
        pos (* skip-size (- (/ key 10) 1))]
    ;    (let [ret (->> (str "dd if=" temp " of=" data " seek=" pos " count=1 oflag=direct conv=notrunc")
    (let [ret (->> (str "dd if=" temp " of=" data " seek=" pos " count=1 conv=notrunc")
                (str "echo " val " > " temp ";")
                (sh "sh" "-c"))]
      (if (= 0 (:exit ret)) 0 -1))))

(defn init-multi-data
  [n count]
  (let [data (str util/file-path n "/data")]
    (sh "sh" "-c" (str "dd if=/dev/zero of=" data " count=" count))
    (doseq [k key-list] (set-multi-data n k 0))))

; client for nfs multi-register
(defn client-multi
  [n]
  (reify client/Client
    (setup! [this test node]
      (init-multi-data (util/nid node) 4096) ;move to core_test in real
      (client-multi (util/nid node)))
    
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

(defn file-nemesis-test [opts]
  (info "noop test\n")
  (merge tests/noop-test
         opts
         {:nodes [:n1 :n2 :n3]
          :name "file-nemesis-test"
          :net net/file-iptables
          :client (client-nfs nil)
          ; :nemesis (nemesis/partition-random-node)
          ;:nemesis (nemesis-test-2)  ; reboot node
          ;:nemesis (nemesis-test-3)  ; reboot node
          ;:nemesis (nemesis-test-7)  ; block node's port
          :nemesis (nemesis0/nemesis-test-9)  ; kill process
          :generator (->> (gen/mix [r w cas])
                         (gen/stagger 1)
                         (gen/nemesis
                           (gen/seq (cycle [(gen/sleep 5)
                                            {:type :info, :f :start}
                                            (gen/sleep 5)
                                            {:type :info, :f :stop}])))
                         (gen/time-limit 20))
          :model (cas-register 0)
          :checker (checker/compose
                    {:perf   (checker/perf)
                     :linear checker/linearizable})}))

(defn file-cap-multi-test [opts]
  (info "consistency mutli-register test\n")
  (init-multi-data 1 4096)
  (merge tests/noop-test
         opts
         {:nodes [:n1 :n2 :n3]
          :name "file-cap-multi-test"
          :client (client-multi nil)
          :nemesis (nemesis/partition-random-halves)
          :generator (->> (gen/mix [rm wm])
                          (gen/stagger 1)
                          ;(gen/clients)
                          (gen/nemesis
                            (gen/seq (cycle [(gen/sleep 5)
                                             {:type :info, :f :start}
                                             (gen/sleep 5)
                                             {:type :info, :f :stop}])))
                          (gen/time-limit 10))
          :model (multi-register (zipmap key-list (repeat 0)))
          :checker (checker/compose {:timeline     (timeline/html)
                                     :perf   (checker/perf)
                                     :linear (independent/checker checker/linearizable)
                                     })
          }))

(defn -main
      "Handles command line arguments. Can either run a test, or a web server for
      browsing results."
      [& args]
      (cli/run! (merge (cli/single-test-cmd {:test-fn file-cap-multi-test})
                       (cli/serve-cmd))
                args))

