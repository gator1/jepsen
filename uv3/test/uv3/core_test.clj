(ns uv3.core-test
  (:require [clojure.test :refer :all]
            [uv3.core :refer :all]
            [jepsen.core :as jepsen]
            [jepsen.generator :as gen]
            [jepsen.checker :as checker]
            [jepsen.nemesis :as nemesis]
            [jepsen.independent :as independent]
            [jepsen.tests :as tests]
            [knossos.model :refer [cas-register, multi-register]])
  (:use     clojure.tools.logging))

(deftest object-multi-test
  (info "consistency mutli-register test\n")
  (init-multi-data 1 4096)
  (let [test (assoc tests/noop-test
               :nodes [:n1 :n2 :n3 :n4]
               :name "object-cap-multi-test"
               :client (client-multi nil)
               :nemesis (partition-uni)
               :generator (->> (gen/mix [mr mw])
                               (gen/stagger 1)
                               (gen/nemesis
                                 (gen/seq (cycle [(gen/sleep 5)
                                                  {:type :info, :f :start}
                                                  (gen/sleep 5)
                                                  {:type :info, :f :stop}])))
                               (gen/time-limit 100))
               :model (multi-register (zipmap key-list (repeat 0)))
               :checker (independent/checker checker/linearizable))]
    (is (:valid? (:results (jepsen/run! test))))))
