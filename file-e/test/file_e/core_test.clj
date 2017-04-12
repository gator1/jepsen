(ns file-e.core-test
  (:require [clojure.test :refer :all]
            [file-e.core :refer :all]
            [file-e.nemesis :refer :all]
            [jepsen.core :as jepsen]
            [jepsen.generator :as gen]
            [jepsen.nemesis :as nemesis]
            [jepsen.checker :as checker]
            [jepsen.tests :as tests]
            [jepsen.independent :as independent]
            [knossos.model :refer [cas-register, multi-register]])
  (:use     clojure.tools.logging))

(deftest file-nemesis-test
  (info "noop test\n")
  (let [test (assoc tests/noop-test
               :nodes [:n1 :n2 :n3]
               :name "file-nemesis-test"
               :client (client-nfs nil)
               :nemesis (nemesis-test-3)
               :generator (->> (gen/mix [r w cas])
                               (gen/stagger 1)
                               (gen/nemesis
                                 (gen/seq (cycle [(gen/sleep 5)
                                                  {:type :info, :f :start}
                                                  (gen/sleep 9)
                                                  {:type :info, :f :stop}])))
                               (gen/time-limit 30))
               :model (cas-register 0)
               :checker (checker/compose
                          {:perf   (checker/perf)
                           :linear checker/linearizable}))]
    (is (:valid? (:results (jepsen/run! test))))))

