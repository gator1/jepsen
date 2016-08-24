(ns fstorage.perf-test
  (:require [clojure.test :refer :all]
            [fstorage.core :refer :all]
            [jepsen.core :as jepsen]
            [jepsen.generator :as gen]
            [jepsen.tests :as tests]))

(defn fsperf-map
  [t1 t2]
  tests/noop-test
  (assoc tests/noop-test
    :name "fsperf-test"
    :nodes [:n1 :n2 :n3]
    :concurrency 1
    :client (client)
    :nemesis (partition-node :n2)
    :generator (->> w
                    (gen/stagger 1)
                    (gen/nemesis
                      (gen/seq (cycle [(gen/sleep t1)
                                       {:type :info, :f :start}
                                       (gen/sleep t2)
                                       {:type :info, :f :stop}])))
                    (oper-limit 20))
    :checker perf-checker)
  )

; fstorage performance testing
; testcase 0: no network partition
(deftest fsper-test-0
  (let [test (assoc (fsperf-map 0 0)
               :generator (->> w
                               (gen/stagger 1)
                               (gen/clients)
                               (oper-limit 20)))]
    (is (:valid? (:results (jepsen/run! test))))))

; testcase 1: n2 out for 2s
(deftest fsperf-test-1
  (is (:valid? (:results (jepsen/run! (fsperf-map 2 2))))))

; testcase 2: n2 out for 6s, which causes downgrade
(deftest fsperf-test-2
  (is (:valid? (:results (jepsen/run! (fsperf-map 6 6))))))

; testcase 3: n2, n3 out for 5s in turn
(deftest fsperf-test-3
  (let [test (assoc (fsperf-map 1 5)
               :nemesis (partition-node-seq))]
    (is (:valid? (:results (jepsen/run! test))))))

; testcase 4: n2 out for 5s and then n3 out
(deftest fsperf-test-4
  (let [test (assoc (fsperf-map 5 5)
               :nemesis (partition-node-seq)
               :generator (->> w
                               (gen/stagger 1)
                               (gen/nemesis
                                 (gen/seq (cycle [(gen/sleep 5)
                                                  {:type :info, :f :start}
                                                  (gen/sleep 5)
                                                  {:type :info, :f :start}
                                                  (gen/sleep 5)
                                                  {:type :info, :f :stop}])))
                               (oper-limit 20)))]
    (is (:valid? (:results (jepsen/run! test))))))

; testcase 5: n2 out for 5s and then control out
(deftest fsperf-test-5
  (let [test (assoc (fsperf-map 5 5)
               :nodes [:n1 :n2 :n3 :n4]
               :nemesis (partition-node-ctrl)
               :generator (->> w
                               (gen/stagger 1)
                               (gen/nemesis
                                 (gen/seq (cycle [(gen/sleep 5)
                                                  {:type :info, :f :start}
                                                  (gen/sleep 5)
                                                  {:type :info, :f :start}
                                                  (gen/sleep 5)
                                                  {:type :info, :f :stop}])))
                               (oper-limit 20)))]
    (is (:valid? (:results (jepsen/run! test))))))
