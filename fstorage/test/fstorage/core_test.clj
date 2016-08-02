(ns fstorage.core-test
  (:require [clojure.test  :refer :all]
            [jepsen.core      :as jepsen]
            [jepsen.generator :as gen]
            [fstorage.core    :as fs]))

; fstorage consistency testing
(deftest fscp-test
  (is (:valid? (:results (jepsen/run! (fs/fstorage-test))))))

; fstorage availability testing
(deftest fsap-test
  ())

(defn run-test
  [test t]
  (let [test (assoc test
               :nemesis (fs/partition-node-n2)
               :generator (->> fs/w
                               (gen/stagger 1)
                               (gen/nemesis
                                 (gen/seq (cycle [(gen/sleep t)
                                                  {:type :info, :f :start}
                                                  (gen/sleep t)
                                                  {:type :info, :f :stop}])))
                               (gen/time-limit 15)))]
       (jepsen/run! test)))

; fstorage performance testing
; testcase 1: n2 out for 2s
(deftest fsperf-test-1
  (is (:valid? (:results (fs/perf-test 2)))))

; testcase 2: n2 out for 6s, which causes downgrade
(deftest fsperf-test-2
  (is (:valid? (:results (fs/perf-test 6)))))

; fstorage client-server consistency testing
(deftest fscscp-test
  ())