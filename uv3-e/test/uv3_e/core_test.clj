(ns uv3-e.core-test
  (:require [clojure.test :refer :all]
            [uv3-e.core :refer :all]
            [uv3-e.nemesis :refer :all]
            [jepsen.core :as jepsen]
            [jepsen.generator :as gen]
            [jepsen.checker :as checker]
            [jepsen.tests :as tests]
            [knossos.model :refer [cas-register]])
  (:use     clojure.tools.logging))


(deftest uv3-consistency-test-1
  (info "consistency test 1\n")
  (set-reg 0)
  (let [test (assoc tests/noop-test
               :nodes [:n1 :n2]
               :name "uv3-cap-test"
               :concurrency 3
               :client (client)
               :nemesis (partition-uni)
               :generator (->> (gen/mix [r w cas])
                               (gen/stagger 1)
                               (gen/nemesis
                                 (gen/seq (cycle [(gen/sleep 5)
                                                  {:type :info, :f :start}
                                                  (gen/sleep 5)
                                                  {:type :info, :f :stop}])))
                               (gen/time-limit 20))
               :model (cas-register 0)
               :checker (checker/compose
                          {:perf   (checker/perf)
                           :linear checker/linearizable}))]
    (is (:valid? (:results (jepsen/run! test))))))

; write incorrect value into reg
(deftest uv3-consistency-test-2
  (info "consistency test 2\n")
  (set-reg 0)
  (let [test (assoc tests/noop-test
               :nodes [:n1 :n2]
               :name "incorrect-write-test"
               :concurrency 3
               :client (client)
               :nemesis (write-wrong-value)
               :generator (->> (gen/mix [r w cas])
                               (gen/stagger 1)
                               (gen/nemesis
                                 (gen/seq (cycle [(gen/sleep 5)
                                                  {:type :info, :f :start}
                                                  (gen/sleep 5)
                                                  {:type :info, :f :stop}])))
                               (gen/time-limit 20))
               :model (cas-register 0)
               :checker (checker/compose
                          {:perf   (checker/perf)
                           :linear checker/linearizable}))]
    (is (:valid? (:results (jepsen/run! test))))))

