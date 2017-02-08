(ns block-s.cscp-test
  (:require [clojure.test :refer :all]
            [block-s.core :refer :all]
            [jepsen.core :as jepsen]
            [jepsen.tests :as tests]
            [jepsen.checker :as checker]
            [jepsen.generator :as gen])
  (:use     clojure.tools.logging))

(def ^:private fscs-map
  ;tests/noop-test
  (assoc tests/noop-test
    :name "fscs-test"
    :nodes [:n1 :n2 :n3]
    :concurrency 5
    :client (client)
    :nemesis (partition-clients)
    :generator (gen/phases
                 (->> add
                      (gen/stagger 1)
                      (gen/nemesis
                        (gen/seq (cycle [(gen/sleep 2)
                                         {:type :info, :f :start}
                                         (gen/sleep 2)
                                         {:type :info, :f :stop}])))
                      (op-limit 20))
                 (gen/nemesis
                   (gen/once {:type :info :f :stop}))
                 (gen/log "waiting for recover")
                 (gen/sleep 5)
                 (gen/clients (gen/once r)))
    :checker checker/counter))

; block client-server consistency testing on register write
(deftest fscs-test-reg
  (info "client consistency test - register\n")
  (set-reg 0 0)
  (is (:valid? (:results (jepsen/run! fscs-map)))))

; fstorage client-server testing based on block write
(deftest fscs-test-blk
  (info "client consistency test - block\n")
  (init-blk 1024000)
  (reset! iter 0)
  (let [test (assoc fscs-map
               :client (client-blk)
               :generator (gen/phases
                            (->> bw
                                 (gen/stagger 1)
                                 (gen/nemesis
                                   (gen/seq (cycle [(gen/sleep 2)
                                                    {:type :info, :f :start}
                                                    (gen/sleep 2)
                                                    {:type :info, :f :stop}])))
                                 (op-limit 20))
                            (gen/nemesis
                              (gen/once {:type :info :f :stop}))
                            (gen/log "waiting for recover")
                            (gen/sleep 5)
                            (->> r
                                 (gen/stagger 1)
                                 (gen/clients)
                                 (op-limit 20)))
               :checker cs-checker)]
    (is (:valid? (:results (jepsen/run! test))))))