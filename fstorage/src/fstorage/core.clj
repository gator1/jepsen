(ns fstorage.core
  (:require [clojure.tools.logging :refer :all]
            [clojure.java.io :as io]
            [clojure.edn :as edn]
            [clojure.string :as str]
            [jepsen [core :as jepsen]
             [db :as db]
             [control :as c]
             [client :as client]
             [nemesis :as nemesis]
             [generator :as gen]
             [checker :as checker]
             [tests :as tests]
             [util :refer [timeout]]]
            [knossos.model :refer [register cas-register]]
            [knossos.history :as history])
  (:use     [clojure.java.shell :only [sh]]))


; define disk device, sector
(def fsdev "/home/gary/jepsen/fstorage/data/testdev")
(def data "/home/gary/jepsen/fstorage/data/temp")
(def offset 2)

; define host sudo password and primary node ip
(def pwd-sudo "ubuntu")
(def node-ip  "1.1.1.1")

; initialize database on each node
(defn db
  [version]
  (reify db/DB
    (setup! [_ test node]
      ;(info node "installing db")
      )

    (teardown! [_ test node])
    ;(info node "tearing down db"))))
    ))

; define operations
(defn r   [_ _] {:type :invoke, :f :read, :value nil})
(defn w   [_ _] {:type :invoke, :f :write, :value (rand-int 5)})
(defn cas [_ _] {:type :invoke, :f :cas, :value [(rand-int 5)(rand-int 5)]})
(defn add [_ _] {:type :invoke, :f :add, :value 1})

; read from disk
(defn get-reg
  []
  (->> (str "cat " data)
       (str "dd if=" fsdev " skip=" offset " of=" data " bs=2 count=1;")
       (sh "sh" "-c")
       :out
       edn/read-string))

; write to disk
(defn set-reg
  [val]
  (->> (str "dd if=" data " of=" fsdev " seek=" offset " bs=2 count=1")
       (str "echo " val " > " data " ;")
       (sh "sh" "-c")))

; add value to disk
(defn add-reg
  [val]
  (set-reg (+ (get-reg) val)))

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
                            (assoc op :type :fail)))
                 :add   (do (add-reg (:value op))
                            (assoc op :type :ok)))))

    (teardown! [_ test])))


; partition node :n2 for perf test
(defn split-n2
  [nodes]
  (let [coll (remove (fn [x] (= x :n2)) nodes)]
    [[:n2], coll]))

(defn partition-node-n2
  []
  (nemesis/partitioner (comp nemesis/complete-grudge split-n2)))


; partition clients host
(defn sudo-cmd
  [cmd]
  (sh "sh" "-c" (str "echo " pwd-sudo  " | sudo -S " cmd)))

(defn drop-net
  []
  (sudo-cmd (str "iptables -A INPUT -s " node-ip " -j DROP")))
  ;(sudo-cmd "iptables -A OUTPUT -d 1.1.1.1 -j DROP"))

(defn heal-net
  []
  ;(sudo-cmd "iptables -D INPUT -s 1.1.1.1 -j DROP")
  (sudo-cmd "iptables -F"))
  ;(sudo-cmd "iptables -X"))

(defn partition-clients
  []
  (reify client/Client
    (setup! [this test _]
      (heal-net)
      this)

    (invoke! [this test op]
      (case (:f op)
        :start (do (drop-net)
                   (assoc op :value "Cut off clients host"))
        :stop  (do (heal-net)
                   (assoc op :value "fully connected"))))

    (teardown! [this test]
      (heal-net))))


; checker for perf test
(defn total-time
  [history]
  (loop [pairs (history/pairs history)
         cnt   0
         total 0]
    (if (nil? pairs)
      {:writes cnt :total-time total}
      (let [[invoke complete] (first pairs)
            pairs (next pairs)]
        (if (= :invoke (:type invoke))
          (recur pairs (inc cnt) (+ total (- (:time complete) (:time invoke))))
          (recur pairs cnt total)))))
  )

(def perf-checker
  (reify checker/Checker
    (check [_ test model history opts]
      (merge {:valid? true} (total-time history)))))


; cap test
(defn cap-test
  []
  (set-reg 0)
  (let [test (assoc tests/noop-test
               :nodes [:n1 :n2 :n3 :n4]
               :name "fscap-test"
               ;:db (db "")
               :concurrency 3
               :client (client)
               :nemesis (nemesis/partition-random-halves)
               ;:nemesis (nemesis/partition-random-node)
               :generator (->> (gen/mix [r w cas])
                               (gen/stagger 1)
                               ;(gen/clients)
                               (gen/nemesis
                                 (gen/seq (cycle [(gen/sleep 5)
                                                  {:type :info, :f :start}
                                                  (gen/sleep 5)
                                                  {:type :info, :f :stop}])))
                               (gen/time-limit 15))
               :model (cas-register 0)
               ;:checker checker/linearizable)
               :checker (checker/compose
                          {:perf   (checker/perf)
                           :linear checker/linearizable}))]
    (jepsen/run! test)))

; perf test
(defn perf-test
  [t]
  (let [test (assoc tests/noop-test
               :node [:n1 :n2 :n3]
               :name "fsperf-test"
               :concurrency 1
               :client (client)
               :nemesis (partition-node-n2)
               :generator (->> w
                               (gen/stagger 1)
                               ;(gen/clients)
                               (gen/nemesis
                                 (gen/seq (cycle [(gen/sleep t)
                                                  {:type :info, :f :start}
                                                  (gen/sleep t)
                                                  {:type :info, :f :stop}])))
                               (gen/limit 10))
               :checker perf-checker)]
    (jepsen/run! test)))

; cscp test
(defn cscp-test
  []
  (set-reg 0)
  (let [test (assoc tests/noop-test
               :name "fscscp-test"
               :nodes [:n1 :n2 :n3]
               :concurrency 5
               :client (client)
               :nemesis (partition-clients)
               :generator (gen/phases
                            (->> add
                                 (gen/stagger 1)
                                 ;(gen/clients)
                                 (gen/nemesis
                                   (gen/seq (cycle [(gen/sleep 2)
                                                    {:type :info, :f :start}
                                                    (gen/sleep 2)
                                                    {:type :info, :f :stop}])))
                                 ;(gen/time-limit 15))
                                 (gen/limit 20))
                            (gen/nemesis
                              (gen/once {:type :info :f :stop}))
                            (gen/log "waiting for recover")
                            (gen/sleep 5)
                            (gen/clients (gen/once r)))
               :checker checker/counter)]
    (jepsen/run! test)))

; run all tests
(defn -main
  "Test entry."
  []
  (info "Start fstorage cap testing")
  (cap-test)

  (info "Start fstorage perf testing")
  (perf-test 2)

  (info "Start fstorage cscp testing")
  (cscp-test)
  )
