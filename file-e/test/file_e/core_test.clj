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

(deftest file-noop-test
  (info "noop test\n")
  (let [test (assoc tests/noop-test
               :nodes [:n1 :n2 :n3]
               :name "file-noop-test"
               :client (client-nfs nil)
               :nemesis (noop-nemesis)
               :generator (->> (gen/mix [r w cas])
                               (gen/stagger 1)
                               (gen/nemesis
                                 (gen/seq (cycle [(gen/sleep 2)
                                                  {:type :info, :f :start}
                                                  (gen/sleep 2)
                                                  {:type :info, :f :stop}])))
                               (gen/time-limit 10))
               :model (cas-register 0)
               :checker (checker/compose
                          {:perf   (checker/perf)
                           :linear checker/linearizable}))]
    (is (:valid? (:results (jepsen/run! test))))))

