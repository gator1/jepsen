(ns fstorage.cscp-test
  (:require [clojure.test :refer :all]
            [fstorage.core :refer :all]
            [jepsen.core :as jepsen]
            [jepsen.tests :as tests]
            [jepsen.checker :as checker]
            [jepsen.generator :as gen]))

(defn fscscp-map
  []
  tests/noop-test
  (assoc tests/noop-test
    :name "fscscp-test"
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
                      (oper-limit 20))
                 (gen/nemesis
                   (gen/once {:type :info :f :stop}))
                 (gen/log "waiting for recover")
                 (gen/sleep 5)
                 (gen/clients (gen/once r)))
    :checker checker/counter))

; fstorage client-server consistency testing on register write
(deftest fscscp-test-reg
  (set-reg 0)
  (is (:valid? (:results (jepsen/run! (fscscp-map))))))

; fstorage client-server testing based on block write
(deftest fscscp-test-blk
  (init-blk 4096)
  (reset! iter 0)
  (let [test (assoc (fscscp-map)
               :client (client-blk)
               :generator (gen/phases
                            (->> bw
                                 (gen/stagger 1)
                                 (gen/nemesis
                                   (gen/seq (cycle [(gen/sleep 2)
                                                    {:type :info, :f :start}
                                                    (gen/sleep 2)
                                                    {:type :info, :f :stop}])))
                                 (oper-limit 20))
                            (gen/nemesis
                              (gen/once {:type :info :f :stop}))
                            (gen/log "waiting for recover")
                            (gen/sleep 5)
                            (->> r
                                 (gen/stagger 1)
                                 (gen/clients)
                                 (read-limit 20)))
               :checker cs-checker)]
    (is (:valid? (:results (jepsen/run! test))))))