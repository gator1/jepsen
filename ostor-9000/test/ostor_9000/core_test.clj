(ns ostor-9000.core-test
  (:require [clojure.test :refer :all]
            [ostor-9000.core :refer :all]
            [jepsen.core :as jepsen]
            [jepsen.generator :as gen]
            [jepsen.nemesis :as nemesis]
            [jepsen.checker :as checker]
            [jepsen.tests :as tests]
            [knossos.model :refer [cas-register]])
  (:use     clojure.tools.logging))

(deftest os9000-cap-test
  (info "consistency test\n")
  (let [test (assoc tests/noop-test
               :nodes [:n1 :n2 :n3]
               :name "os9000-cap-test"
               :client (client nil)
               :nemesis (nemesis/partition-random-halves)
               :generator (->> (gen/mix [r w cas])
                               (gen/stagger 1)
                               (gen/nemesis
                                 (gen/seq (cycle [(gen/sleep 5)
                                                  {:type :info, :f :start}
                                                  (gen/sleep 5)
                                                  {:type :info, :f :stop}])))
                               (gen/time-limit 10))
               :model (cas-register 0)
               :checker (checker/compose
                          {:perf   (checker/perf)
                           :linear checker/linearizable}))]
    (is (:valid? (:results (jepsen/run! test))))))