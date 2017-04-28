(ns block-s.perf-test
  (:require [clojure.test :refer :all]
            [block-s.core :refer :all]
            [jepsen.core :as jepsen]
            [jepsen.checker :as checker]
            [jepsen.checker.timeline :as timeline]
            [jepsen.generator :as gen]
            [jepsen.tests :as tests])
  (:use     clojure.tools.logging))

(defn fsperf-map
  [t1 t2]
  ;tests/noop-test
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
                    (op-limit 20))
    :checker   (checker/compose
               {:timeline    (timeline/html)
                :perf        (checker/perf)
                :counter     perf-checker})))

; block performance testing
; testcase 0: no network partition
(deftest fsperf-test-0
  (info "performance test #0\n")
  (let [test (assoc (fsperf-map 0 0)
               :generator (->> w
                               (gen/stagger 1)
                               (gen/clients)
                               (op-limit 20)))]
    (is (:valid? (:results (jepsen/run! test))))))

; testcase 1: n2 out for 2s
(deftest fsperf-test-1
  (info "performance test #1\n")
  (is (:valid? (:results (jepsen/run! (fsperf-map 2 2))))))

; testcase 2: n2 out for 6s, which causes downgrade
(deftest fsperf-test-2
  (info "performance test #2\n")
  (is (:valid? (:results (jepsen/run! (fsperf-map 6 6))))))

; testcase 3: n2, n3 out for 5s in turn
(deftest fsperf-test-3
  (info "performance test #3\n")
  (let [test (assoc (fsperf-map 1 5)
               :nemesis (partition-node-seq))]
    (is (:valid? (:results (jepsen/run! test))))))

; testcase 4: n2 out for 5s and then n3 out
(deftest fsperf-test-4
  (info "performance test #4\n")
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
                               (op-limit 20)))]
    (is (:valid? (:results (jepsen/run! test))))))

; testcase 5: n2 out for 5s and then control out
(deftest fsperf-test-5
  (info "performance test #5\n")
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
                               (op-limit 20)))]
    (is (:valid? (:results (jepsen/run! test))))))
