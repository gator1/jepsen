(ns file-s.core-test
  (:require [clojure.test :refer :all]
            [file-s.core :refer :all]
            [jepsen.core :as jepsen]
            [jepsen.generator :as gen]
            [jepsen.nemesis :as nemesis]
            [jepsen.checker :as checker]
            [jepsen.tests :as tests]
            [jepsen.independent :as independent]
            [knossos.model :refer [cas-register, multi-register]])
  (:use     clojure.tools.logging))

(deftest file-cap-test
  (info "consistency test\n")
  (let [test (assoc tests/noop-test
               :nodes [:n1 :n2 :n3]
               :name "file-cap-test"
               :client (client-nfs nil)
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

(deftest file-cap-multi-test
  (info "consistency mutli-register test\n")
  ;(init-multi-data 1 4096)
  (let [test (assoc tests/noop-test
               :nodes [:n1 :n2 :n3]
               :name "file-cap-multi-test"
               :client (client-multi nil)
               :nemesis (nemesis/partition-random-halves)
               :generator (->> (gen/mix [rm wm])
                               (gen/stagger 1)
                               ;(gen/clients)
                               (gen/nemesis
                                 (gen/seq (cycle [(gen/sleep 5)
                                                  {:type :info, :f :start}
                                                  (gen/sleep 5)
                                                  {:type :info, :f :stop}])))
                               (gen/time-limit 20))
               :model (multi-register (zipmap [10 20 30 40 50] (repeat 0)))
               :checker (independent/checker checker/linearizable))]
               ;:checker checker/linearizable)]
    (is (:valid? (:results (jepsen/run! test))))))
