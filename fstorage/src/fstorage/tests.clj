(ns fstorage.tests
  (:require [fstorage.core :refer :all]
            [clojure.tools.logging :refer :all]
            [clojure.java.io :as io]
            [clojure.edn :as edn]
            [clojure.string :as str]
            [jepsen [core :as jepsen]
             [nemesis :as nemesis]
             [generator :as gen]
             [checker :as checker]
             [tests :as tests]]
;             [util :refer [timeout local-time real-pmap with-thread-name]]]
            [knossos.model :refer [register cas-register]]
            [knossos.linear :as linear]
            [knossos.linear.report :as linear.report]
            [knossos.history :as history])
  (:use     [clojure.java.shell :only [sh]])
  (:import (java.io PushbackReader)))


(defn node-ids
  [test]
  (->> test
       :nodes
       (map-indexed (fn [i node][node i]))
       (into {})))

(defn node-id
  [test node]
  ((node-ids test) node))

(defn read-history
  "Reads a history file of [process type f value] tuples, or maps."
  [f]
  (with-open [r (PushbackReader. (io/reader f))]
    (->> (repeatedly #(edn/read {:eof nil} r))
         (take-while identity)
         (map (fn [op]
                (if (map? op)
                  op
                  (let [[process type f value] op]
                       {:process process
                        :type    type
                        :f       f
                        :value   value}))))
         vec)))

; Convert history.txt file to history.edn in order to use read-history
(defn convert-to-edn
  [f]
  (let [n (str/replace f ".txt" ".edn")]
       (with-open [r (io/reader f)]
         (with-open [w (io/writer n)]
           (doseq [l (line-seq r)]
             ; fix for zookeeper test history file
             ;(let [[w1 w2 w3 w4 w5] (str/split l #"\s+")]
             ;     (->> (if (= w1 ":nemesis")
             ;            l
             ;            (if (= (first w4) \[)
             ;              (str/join " " [w1 w2 w3 w4 w5])
             ;              (str/join " " [w1 w2 w3 w4])))
             ;          ((fn [s]
             ;             (.write w (str "[" s "]\n"))))))))))
             (.write w (str "[" l "]\n"))))))
  )

(defn linear-test
  [file]
  (let [history (read-history (str file ".edn"))
        model (cas-register 0)
        analysis (linear/analysis model history)]
    (println "linear-test analysis =" analysis)
    (if (= false (:valid? analysis))
      (do (println "Analysis failed, generating .svg")
          (linear.report/render-analysis! history analysis (str file ".svg")))
      (println "Analysis passed"))
    ))

(defn pair-test
  [file]
  (let [history (read-history (str file ".edn"))]
    (println (history/pairs history))))

; analyse history
(defn analyse
  [file]
   (convert-to-edn (str file ".txt"))
   (linear-test file)
   )

(defn fstorage-test
  []
  tests/noop-test
  (assoc tests/noop-test
    :nodes [:n1 :n2 :n3]
    :name "fstorage"
    ;:db (db "")
    :concurrency 1
    :client (client)
    :nemesis (nemesis/partition-random-halves)
    ;:nemesis (nemesis/partition-random-node)
    :generator (->> (gen/mix [r w cas])
                    (gen/stagger 1)
                    ;(gen/clients)
                    (gen/nemesis
                      (gen/seq (cycle [(gen/sleep 5)
                                       {:type :info, :f :start}
                                       (gen/sleep 5)
                                       {:type :info, :f :stop}])))
                    (gen/time-limit 15))
    :model (cas-register 0)
    :checker (checker/compose
               {:perf   (checker/perf)
                :linear checker/linearizable}))
  ;:checker checker/linearizable)
  )

(defn perf-test
  [t]
  (let [test (assoc (fstorage-test)
               :nemesis (partition-node :n2)
               ;:nemesis (partition-node-seq)
               :generator (->> w
                               (gen/stagger 1)
                               ;(gen/clients)
                               (gen/nemesis
                                 (gen/seq (cycle [(gen/sleep t)
                                                  {:type :info, :f :start}
                                                  ;(gen/sleep t)
                                                  ;{:type :info, :f :start}
                                                  (gen/sleep t)
                                                  {:type :info, :f :stop}])))
                               (real-limit 20))
               :checker perf-checker)]
    (jepsen/run! test)))

; test cap
(defn cap-test
  []
  (set-reg 0)
  (let [test (assoc tests/noop-test
               :nodes [:n1 :n2 :n3 :n4]
               :name "fscap-test"
               ;:db (db "")
               :concurrency 3
               :client (client)
               ;:nemesis (nemesis/partition-random-halves)
               :nemesis (nemesis/partition-random-node)
               :generator (->> (gen/mix [r w cas])
                               (gen/stagger 1)
                               ;(gen/clients)
                               (gen/nemesis
                                 (gen/seq (cycle [(gen/sleep 5)
                                                  {:type :info, :f :start}
                                                  (gen/sleep 5)
                                                  {:type :info, :f :stop}])))
                               (gen/time-limit 15))
               :model (cas-register 0)
               ;:checker checker/linearizable)
               :checker (checker/compose
                          {:perf   (checker/perf)
                           :linear checker/linearizable}))]
    (jepsen/run! test)))

; test cscp
(defn cscp-test
  []
  (set-reg 0)
  (let [test (assoc tests/noop-test
               :name "fscscp-test"
               :nodes [:n1 :n2 :n3]
               :concurrency 5
               :client (client)
               :nemesis (partition-clients)
               :generator (gen/phases
                            (->> add
                                 (gen/stagger 1)
                                 ;(gen/clients)
                                 (gen/nemesis
                                   (gen/seq (cycle [(gen/sleep 2)
                                                    {:type :info, :f :start}
                                                    (gen/sleep 2)
                                                    {:type :info, :f :stop}])))
                                 ;(gen/time-limit 15))
                                 (gen/limit 20))
                            (gen/nemesis
                              (gen/once {:type :info :f :stop}))
                            (gen/log "waiting for recover")
                            (gen/sleep 5)
                            (gen/clients (gen/once r)))
               :checker checker/counter)]
    (jepsen/run! test)))