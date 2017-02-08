(ns object.core-test
  (:require [clojure.test :refer :all]
            [object.core :refer :all]
            [jepsen.core :as jepsen]
            [jepsen.generator :as gen]
            [jepsen.nemesis :as nemesis]
            [jepsen.checker :as checker]
            [jepsen.independent :as independent]
            [jepsen.tests :as tests]
            [knossos.model :refer [cas-register, multi-register]])
  (:use     clojure.tools.logging))


(deftest object-cap-test
  (info "consistency object mutli-register test\n")
  (init-objs 1)
  (let [test (assoc tests/noop-test
               :nodes [:n1 :n2 :n3 :n4]
               :name "object-cap-multi-test"
               :db (db "")
               :client (client-obj nil)
               :nemesis (nemesis/partition-random-node)
               :generator (->> (gen/mix [mr mw])
                               (gen/stagger 1)
                               (gen/clients)
                               (gen/nemesis
                                 (gen/seq (cycle [(gen/sleep 5)
                                                  {:type :info, :f :start}
                                                  (gen/sleep 5)
                                                  {:type :info, :f :stop}])))
                               (gen/time-limit 60))
               :model (multi-register (zipmap key-list (repeat 0)))
               :checker (independent/checker checker/linearizable))]
    (is (:valid? (:results (jepsen/run! test))))))

