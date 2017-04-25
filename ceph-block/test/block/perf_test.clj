(ns block.perf-test
  (:require [clojure.test :refer :all]
            [block.core :refer :all]
            [jepsen.core :as jepsen]
            [jepsen.generator :as gen]
            [jepsen.tests :as tests])
  (:use     clojure.tools.logging))

(defn fsperf-map
  [t1 t2]
  ;tests/noop-test
  (assoc tests/noop-test
    :name "fsperf-test"
    ;:nodes [:osd-1 :osd-2 :osd-3]
    :nodes [:n1 :n2 :n3]
    :concurrency 1
    :client (client)
    ;:nemesis (partition-node :osd-2)
    :nemesis (partition-node :n2)
    :generator (->> w
                    (gen/stagger 1)
                    (gen/nemesis
                      (gen/seq (cycle [(gen/sleep t1)
                                       {:type :info, :f :start}
                                       (gen/sleep t2)
                                       {:type :info, :f :stop}])))
                    (op-limit 200))
    :checker perf-checker)
  )

; block performance testing
; testcase 0: no network partition
(deftest fsperf-test-0
  (info "performance test #0\n")
  (let [test (assoc (fsperf-map 0 0)
               :generator (->> w
                               (gen/stagger 1)
                               (gen/clients)
                               (op-limit 200)))]
    (is (:valid? (:results (jepsen/run! test))))))

; testcase 1: n2 out for 2s
(deftest fsperf-test-1
  (info "performance test #1\n")
  (is (:valid? (:results (jepsen/run! (fsperf-map 2 2))))))

; testcase 2: n2 out for 100s, which causes no response
(deftest fsperf-test-2
  (info "performance test #2\n")
  (is (:valid? (:results (jepsen/run! (fsperf-map 0 100))))))
