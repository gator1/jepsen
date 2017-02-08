(ns file.core-test
  (:require [clojure.test :refer :all]
            [file.core :refer :all]
            [jepsen.core :as jepsen]
            [jepsen.generator :as gen]
            [jepsen.nemesis :as nemesis]
            [jepsen.checker :as checker]
            [jepsen.independent :as independent]
            [jepsen.tests :as tests]
            [knossos.model :refer [cas-register, multi-register]])
  (:use     clojure.tools.logging))

(deftest file-cap-test
  (info "consistency test\n")
  (init-data)
  (let [test (assoc tests/noop-test
               :nodes [:n1 :n2 :n3]
               :name "file-cap-test"
               :client (client-nfs nil)
               :nemesis (nemesis/partition-random-node)
               :generator (->> (gen/mix [r w cas])
                               (gen/stagger 1)
                               (gen/nemesis
                                 (gen/seq (cycle [(gen/sleep 5)
                                                  {:type :info, :f :start}
                                                  (gen/sleep 5)
                                                  {:type :info, :f :stop}])))
                               (gen/time-limit 100))
               :model (cas-register 0)
               :checker checker/linearizable)]
    (is (:valid? (:results (jepsen/run! test))))))


(deftest file-cap-multi-test
  (info "consistency mutli-register test\n")
  (init-multi-data 1 4096)
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
                               (gen/time-limit 100))
               :model (multi-register (zipmap key-list (repeat 0)))
               :checker (independent/checker checker/linearizable))]
    (is (:valid? (:results (jepsen/run! test))))))


