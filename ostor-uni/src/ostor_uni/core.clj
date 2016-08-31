(ns ostor-uni.core
  (:require [clojure.tools.logging :refer :all]
            [jepsen [core      :as jepsen]
                    [client    :as client]
                    [nemesis   :as nemesis]
                    [generator :as gen]
                    [checker   :as checker]
                    [control   :as c]
                    [tests     :as tests]
                    [util      :refer [timeout]]]
            [jepsen.control.net :as control.net]
            [knossos.model :refer [cas-register]]))


; define operations
(defn r   [_ _] {:type :invoke, :f :read, :value nil})
(defn w   [_ _] {:type :invoke, :f :write, :value (rand-int 5)})
(defn cas [_ _] {:type :invoke, :f :cas, :value [(rand-int 5)(rand-int 5)]})

(defn get-reg
  []
  ; UNIBLKIODoIO -drive dev -pattern SD0 -mode RC -thread 4 -pass_count 1 -size 16 -seek_type S
  (println ">>> get register"))

(defn set-reg
  [val]
  ; UNIBLKIODoIO -drive dev -pattern SD0 -mode WC -thread 4 -pass_count 1 -size 16 -seek_type S
  (println ">>> set register"))

; client for operation execution
(defn client
  []
  (reify client/Client
    (setup! [_ test node]
      (client))

    (invoke! [this test op]
      (timeout 5000 (assoc op :type :info, :error :timeout)
               (case (:f op)
                 :read  (assoc op :type :ok, :value (get-reg))

                 :write (do (set-reg (:value op))
                            (assoc op :type :ok))

                 :cas   (let [[value value'] (:value op)]
                          (if (= (get-reg) value)
                            (do (set-reg value')
                                (assoc op :type :ok))
                            (assoc op :type :fail))))))

    (teardown! [_ test])))

; force pcie 0 link down
; pcie id should be found before running the test
(defn linkdown
  [node]
  ;(c/on node (c/exec (c/lit "mml")) (c/exec (c/lit "pcie forcelinkdown 0 1")))
  ;(c/on dest (c/su (c/exec :iptables :-A :INPUT :-s (control.net/ip src) :-j :DROP :-w)))
  )

(defn linkup
  [node]
  ;(c/on node (c/exec (c/lit "mml")) (c/exec (c/lit "pcie forcelinkup 0 0")))
  ;(c/on node (c/su (c/exec :iptables :-F :-w) (c/exec :iptables :-X :-w)))
  )

(defn partition-uni
  []
  (reify client/Client
    (setup! [this test _]
      this)

    (invoke! [this test op]
      (case (:f op)
        :start (do (linkdown (first (:nodes test)))
                   (assoc op :value "Cut off PCIE link"))
        :stop  (do (linkup (first (:nodes test)))
                   (assoc op :value "fully connected"))))

    (teardown! [this test]
      (linkup (first (:nodes test)))))
  )

; test cap
(defn cap-test
  []
  (set-reg 0)
  (let [test (assoc tests/noop-test
               :nodes [:n1 :n2]
               :name "oscap-test"
               ;:db (db "")
               :concurrency 1
               :client (client)
               :nemesis (partition-uni)
               :generator (->> (gen/mix [r w cas])
                               (gen/stagger 1)
                               ;(gen/clients)
                               (gen/nemesis
                                 (gen/seq (cycle [(gen/sleep 5)
                                                  {:type :info, :f :start}
                                                  (gen/sleep 5)
                                                  {:type :info, :f :stop}])))
                               (gen/time-limit 10))
               :model (cas-register 0)
               ;:checker checker/linearizable)
               :checker (checker/compose
                          {:perf   (checker/perf)
                           :linear checker/linearizable}))]
    (jepsen/run! test)))