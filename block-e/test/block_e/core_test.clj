(ns block-e.core-test
  (:require [clojure.test :refer :all]
            [block-e.core :refer :all]
            [block-e.nemesis :refer :all]
            [jepsen.core :as jepsen]
            [jepsen.generator :as gen]
            [jepsen.checker :as checker]
            [jepsen.tests :as tests]
            [jepsen.nemesis :as nemesis]
            [knossos.model :refer [cas-register]])
  (:use     clojure.tools.logging))

(def ^:private test-1
  (assoc tests/noop-test
    :nodes [:n1 :n2 :n3 :n4]
    :name "test-1"
    :concurrency 3
    :client (client)
    :nemesis (test-nemesis-1)
    :generator (->> (gen/mix [r w cas])
                    (gen/stagger 1)
                    (gen/nemesis
                      (gen/seq (cycle [(gen/sleep 2)
                                       {:type :info, :f :start}
                                       (gen/sleep 2)
                                       {:type :info, :f :stop}])))
                    (gen/time-limit 20))
    :model (cas-register 0)
    :checker checker/linearizable)
  )

(def ^:private test-2
  (assoc tests/noop-test
    :nodes [:n1 :n2 :n3 :n4]
    :name "test-2"
    :concurrency 3
    :client (client)
    :nemesis (test-nemesis-2)
    :generator (->> (gen/mix [r w cas])
                    (gen/stagger 1)
                    (gen/nemesis
                      (gen/seq (cycle [(gen/sleep 2)
                                       {:type :info, :f :start}
                                       (gen/sleep 2)
                                       {:type :info, :f :stop}])))
                    (gen/time-limit 20))
    :model (cas-register 0)
    :checker checker/linearizable)
  )

(deftest nemesis-test-1
  (info "consistency test 1\n")
  (set-reg 0 0)
  (is (:valid? (:results (jepsen/run! test-1)))))

(deftest nemesis-test-2
  (info "consistency test 2\n")
  (set-reg 0 0)
  (is (:valid? (:results (jepsen/run! test-2)))))

