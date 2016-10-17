(ns ostor-jl.core-test
  (:require [clojure.test :refer :all]
            [ostor-jl.core :refer :all]
            [jepsen.core :as jepsen]
            [jepsen.generator :as gen]
            [jepsen.checker :as checker]
            [jepsen.tests :as tests]
            [knossos.model :refer [multi-register]])
  (:use     clojure.tools.logging)
  (:refer-clojure :exclude [read write set]))

(deftest oscp-test
  (info "consistency test\n")
  (let [time-limit 60
        test (assoc tests/noop-test
               :nodes [:n1 :n2 :n3 :n4 :n5]
               :name "osuni-cap-test"
               :concurrency 5
               :client (ostor-simulator)
               :nemesis (nemesis)
               :generator (->> (gen/mix [w])
                               (gen/stagger 5)
                               (gen/nemesis (gen/seq (shuffle
                                                      (mapcat identity
                                                              (repeatedly time-limit
                                                                          #(let [sleep (+ 1 (rand-int 5))]
                                                                             [{:type :info :f :stop}
                                                                              (gen/sleep sleep)
                                                                              {:type :info :f :start}]))))))
                               (gen/time-limit time-limit))
               :model (multi-register {})
               :checker (checker/compose
                          {:perf   (checker/perf)
                           :linear checker/linearizable}))]
    (is (:valid? (:results (jepsen/run! test))))))
