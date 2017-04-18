(ns block.core-test
  (:require [clojure.test :refer :all]
            [block.core :refer :all]
            [jepsen.core :as jepsen]
            [jepsen.generator :as gen]
            [jepsen.checker :as checker]
            [jepsen.tests :as tests]
            [jepsen.nemesis :as nemesis]
            [knossos.model :refer [cas-register]])
  (:use     clojure.tools.logging))

(def ^:private fscap-map
  (assoc tests/noop-test
    :nodes [:n1 :n2 :n3]
    :name "fscp-test"
    :concurrency 3
    :client (client)
    :nemesis (nemesis/partition-random-halves)
    :generator (->> (gen/mix [r w cas])
                    (gen/stagger 1)
                    (gen/nemesis
                      (gen/seq (cycle [(gen/sleep 5)
                                       {:type :info, :f :start}
                                       (gen/sleep 5)
                                       {:type :info, :f :stop}])))
                    (gen/time-limit 100))
    :model (cas-register 0)
    ;:checker checker/linearizable)
    :checker (checker/compose
               {:perf   (checker/perf)
                :linear checker/linearizable}))
  )

; block consistency testing
(deftest fscp-test
  (info "consistency test\n")
  (set-reg 0 0)
  (is (:valid? (:results (jepsen/run! fscap-map)))))
