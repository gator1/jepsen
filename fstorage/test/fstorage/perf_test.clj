(ns fstorage.perf-test
  (:require [clojure.test :refer :all]
            [fstorage.core :refer :all]
            [jepsen.core :as jepsen]
            [jepsen.generator :as gen]
            [jepsen.tests :as tests]))

(defn fsperf-map
  [t]
  tests/noop-test
  (assoc tests/noop-test
    :name "fsperf-test"
    :concurrency 1
    :client (client)
    :nemesis (partition-node-n2)
    :generator (->> w
                    (gen/stagger 1)
                    (gen/nemesis
                      (gen/seq (cycle [(gen/sleep t)
                                       {:type :info, :f :start}
                                       (gen/sleep t)
                                       {:type :info, :f :stop}])))
                    (gen/limit 10))
    :checker perf-checker)
  )

; fstorage performance testing
; testcase 1: n2 out for 2s
(deftest fsperf-test-1
  (is (:valid? (:results (jepsen/run! (fsperf-map 2))))))

; testcase 2: n2 out for 6s, which causes downgrade
(deftest fsperf-test-2
  (is (:valid? (:results (jepsen/run! (fsperf-map 6))))))