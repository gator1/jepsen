(ns file.perf-test
  (:require [clojure.test :refer :all]
            [file.core :refer :all]
            [jepsen.core :as jepsen]
            [jepsen.generator :as gen]
            [jepsen.tests :as tests])
  (:use     clojure.tools.logging))

; testcase 0: no network partition
(deftest file-perf-test-0
  (info "performance test #0\n")
  (let [test (assoc tests/noop-test
               :nodes [:n1 :n2 :n3]
               :name "file-perf-test"
               :concurrency 1
               :client (client-nfs nil)
               :generator (->> w
                               (gen/stagger 1)
                               (gen/clients)
                               (op-limit 20))
               :checker perf-checker)]
    (is (:valid? (:results (jepsen/run! test))))))

; testcase 1: n2 out for 2s
(deftest file-perf-test-1
  (info "performance test #1\n")
  (let [test (assoc tests/noop-test
               :nodes [:n1 :n2 :n3]
               :name "file-perf-test"
               :concurrency 1
               :client (client-nfs nil)
               :nemesis (partition-node :n2)
               :generator (->> w
                               (gen/stagger 1)
                               (gen/nemesis
                                 (gen/seq (cycle [(gen/sleep 2)
                                                  {:type :info, :f :start}
                                                  (gen/sleep 2)
                                                  {:type :info, :f :stop}])))
                               (op-limit 20))
               :checker perf-checker)]
    (is (:valid? (:results (jepsen/run! test))))))

; testcase 2: n2, n3 out for 5s
(deftest file-perf-test-2
  (info "performance test #2\n")
  (let [test (assoc tests/noop-test
               :nodes [:n1 :n2 :n3]
               :name "file-perf-test"
               :concurrency 1
               :client (client-nfs nil)
               :nemesis (partition-node :n1)
               :generator (->> w
                               (gen/stagger 1)
                               (gen/nemesis
                                 (gen/seq (cycle [(gen/sleep 5)
                                                  {:type :info, :f :start}
                                                  (gen/sleep 5)
                                                  {:type :info, :f :stop}])))
                               (op-limit 20))
               :checker perf-checker)]
    (is (:valid? (:results (jepsen/run! test))))))
