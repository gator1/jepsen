(ns ostor-jl.core-test
  (:require [clojure.test :refer :all]
            [ostor-jl.core :refer :all]
            [jepsen.core :as jepsen]
            [jepsen.generator :as gen]
            [jepsen.checker :as checker]
            [jepsen.tests :as tests]
            [knossos.model :refer [multi-register]])
  (:use     clojure.tools.logging))

(deftest oscp-test
  (info "consistency test\n")
  (let [test (assoc tests/noop-test
               :nodes [:n1 :n2 :n3 :n4 :n5]
               :name "osuni-cap-test"
               :concurrency 5
               :client (ostor-simulator)
               :nemesis (nemesis)
               :generator (->> (gen/mix [w r])
                               (gen/stagger 1)
                               (gen/nemesis
                                 (gen/seq (cycle [(gen/sleep 5)
                                                  {:type :info, :f :start}
                                                  (gen/sleep 5)
                                                  {:type :info, :f :stop}])))
                               (gen/time-limit 30))
               :model (multi-register {})
               :checker (checker/compose
                          {:perf   (checker/perf)
                           :linear checker/linearizable}))]
    (is (:valid? (:results (jepsen/run! test))))))
