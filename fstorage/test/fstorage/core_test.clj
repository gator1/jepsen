(ns fstorage.core-test
  (:require [clojure.test :refer :all]
            [fstorage.core :refer :all]
            [jepsen.core :as jepsen]
            [jepsen.generator :as gen]
            [jepsen.checker :as checker]
            [jepsen.tests :as tests]
            [jepsen.nemesis :as nemesis]
            [knossos.model :refer [cas-register]])
  (:use     clojure.tools.logging))

(defn fscap-map
  []
  tests/noop-test
  (assoc tests/noop-test
    :nodes [:n1 :n2 :n3 :n4]
    :name "fscap-test"
    :concurrency 3
    :client (client)
    :nemesis (nemesis/partition-random-halves)
    :generator (->> (gen/mix [r w cas])
                    (gen/stagger 1)
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
  )

; fstorage consistency testing
(deftest fscp-test
  (info "consistency test\n")
  (set-reg 0)
  (is (:valid? (:results (jepsen/run! (fscap-map))))))

; fstorage availability testing
;(deftest fsap-test
;  (info "availability test\n"))
