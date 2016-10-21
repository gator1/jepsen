(ns ostor-9000.core
  (:require [clojure.tools.logging :refer :all]
            [jepsen [core :as jepsen]
             [client :as client]
             [nemesis :as nemesis]
             [generator :as gen]
             [checker :as checker]
             [control :as c]
             [tests :as tests]
             [util :refer [timeout]]]
            [knossos.model :refer [cas-register]]
            [knossos.history :as history]
            [clojure.edn :as edn])
  (:use     [clojure.java.shell :only [sh]]) )


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
  (->> (sh "cat" loc)
       :out
       edn/read-string))

(defn set-reg
  [loc val]
  (sh "sh" "-c" (str "echo " val " > " loc)))

(defn location
  [n]
  (str "dev" (nid n) "/data"))

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

    (teardown! [_ test]))
  )

; client for object based
(defn client-obj
  [loc]
  (reify client/Client
    (setup! [_ test node])
    (invoke! [_ test op])
    (teardown! [_ test]))
  )

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


; test cap
(defn cap-test
  []
  (let [test (assoc tests/noop-test
               :nodes [:n1 :n2 :n3]
               :name "cap-test"
               ;:client (client-obj nil)
               :client (client-nfs nil)
               :nemesis (nemesis/partition-random-halves)
               :generator (->> (gen/mix [r w cas])
                               (gen/stagger 1)
                               ;(gen/clients)
                               (gen/nemesis
                                 (gen/seq (cycle [(gen/sleep 5)
                                                  {:type :info, :f :start}
                                                  (gen/sleep 5)
                                                  {:type :info, :f :stop}])))
                               (gen/time-limit 10))
               :model (cas-register 0)
               ;:checker checker/linearizable)]
               :checker (checker/compose
                          {:perf   (checker/perf)
                           :linear checker/linearizable}))]
    (jepsen/run! test)))
