(ns block.core
  (:require [clojure.tools.logging :refer :all]
            [clojure.java.io :as io]
            [clojure.edn :as edn]
            [clojure.string :as str]
            [jepsen [core :as jepsen]
             [db :as db]
             [control :as c]
             [cli :as cli]
             [client :as client]
             [nemesis :as nemesis]
             [generator :as gen]
             [checker :as checker]
             [tests :as tests]
             [util :refer [timeout]]]
            [knossos.model :refer [register cas-register]]
            [knossos.history :as history])
  (:use [clojure.java.shell :only [sh]])
  (:import (block BlockUtils)))


; define disk device, sector
(def fsdev "/dev/rbd2")
(def data-dir "/home/xuelin/data")
(def data-dir-1 "/home/xuelin/tmpfs")

(def data (str data-dir "/temp"))
(def block-size 512)
(def offset 12288)

(def nic-cmds
  {
   ;; ceph docker nodes
   "172.18.0.6" {:start ["/usr/bin/docker" "exec" "7f954c2e868a" "ifconfig" "eth0" "down"]
                 :stop ["/usr/bin/docker" "exec" "7f954c2e868a" "ifconfig" "eth0" "up"]}
   "172.18.0.5" {:start ["/usr/bin/docker" "exec" "d6c2407abe71" "ifconfig" "eth0" "down"]
                 :stop ["/usr/bin/docker" "exec" "d6c2407abe71" "ifconfig" "eth0" "up"]}
   "172.18.0.2" {:start ["/usr/bin/docker" "exec" "329ae7a2dd25" "ifconfig" "eth0" "down"]
                 :stop ["/usr/bin/docker" "exec" "329ae7a2dd25" "ifconfig" "eth0" "up"]}

   ;; fsblock osd nodes
   "162.7.83.11" {:start ["/usr/bin/ssh" "164.7.83.11" "ifconfig" "eth2" "down"]
                  :stop ["/usr/bin/ssh" "164.7.83.11" "ifconfig" "eth2" "up"]}
   "162.7.83.12" {:start ["/usr/bin/ssh" "164.7.83.12" "ifconfig" "eth2" "down"]
                  :stop ["/usr/bin/ssh" "164.7.83.12" "ifconfig" "eth2" "up"]}
   "162.7.83.13" {:start ["/usr/bin/ssh" "164.7.83.13" "ifconfig" "eth2" "down"]
                  :stop ["/usr/bin/ssh" "164.7.83.13" "ifconfig" "eth2" "down"]}
   }
  )
(def init-opts (atom {
                      :op-timeout-ms 5000
                      :nemesis-before-secs 1
                      :nemesis-between-secs 5
                      :nemesis-after-secs 4
                      :time-limit-secs 100
                      :nemesis "partition-random-halves"
                      :nemesis-node-ip "172.18.0.6"
                      :no-heal? "true"
                      :cut-once? "true"
                      :process-max 150
                      :api 0
                      }))

; define host sudo password and primary node ip
(def pwd-sudo "ubuntu")
(def node-ip  "182.81.129.5")

(def iter (atom 0))

; define operations
(defn r   [_ _] {:type :invoke, :f :read, :value nil})
(defn w   [_ _] {:type :invoke, :f :write, :value (rand-int 5)})
(defn cas [_ _] {:type :invoke, :f :cas, :value [(rand-int 5)(rand-int 5)]})
(defn add [_ _] {:type :invoke, :f :add, :value 1})
(defn bw  [_ _] {:type :invoke, :f :write, :value (swap! iter inc)})

(defn sudo-cmd
  [cmd]
  (sh "sh" "-c" (str "echo " pwd-sudo  " | sudo -S " cmd)))

(defn prepare-data-files
  [count]
  (sudo-cmd (str "rm -rf " data-dir))
  (doall (map #(sh "sh" "-c" (str "dd if=/dev/zero count=1 bs=" block-size " > " data %))
              (range count))))

(defn write-stage-data
  [num process]
  (let [num-bytes (BlockUtils/writeByte (str data process) 0 (.byteValue num))]
    (assert (= num-bytes 1))))

(defn read-stage-data
  [process]
  (long (BlockUtils/readByte (str data process) 0)))

; get data on specific position from disk
(defn get-data-0
  [process dev pos]
  (info "in get-data-0" data process)
  (sh "sh" "-c" (str "dd if=/dev/zero count=1 > " data process))
  (let [ret (->> (str "cat " data process)
                 (str "dd if=" dev " skip=" (+ offset pos) " of=" data process " count=1 iflag=direct;")
                 sudo-cmd)]
    (if (= 0 (:exit ret)) (edn/read-string (:out ret)) -1)))

(defn set-data-0
  [process dev pos val]
  (info "in set-data-0" data process)
  (sh "bash" "-c" (str "cat <(echo " val ") <(dd if=/dev/zero bs=1 count=510) > " data process))
  (let [ret (sudo-cmd (str "dd if=" data process " of=" dev " seek=" (+ offset pos) " count=1 oflag=direct"))]
    (if (= 0 (:exit ret)) 0 -1)))

; get data on specific position from disk
(defn get-data-1
  [process dev pos]
  (info "get-date process: " process)
  (let [ret (sudo-cmd (str "dd if=" dev " skip=" (+ offset pos) " of=" data process " bs=" block-size " count=1 iflag=direct,sync;"))]
    (if (= 0 (:exit ret))
      (read-stage-data process) -1)))

(defn set-data-1
  [process dev pos val]
  (info "set-data process: " process)
  (write-stage-data val process)
  (let [ret (sudo-cmd (str "dd if=" data process " of=" dev " seek=" (+ offset pos) " bs=" block-size " count=1 oflag=direct,sync"))]
    (if (= 0 (:exit ret)) 0 -1)))

; get data on specific position from disk
(defn get-data
  [process dev pos]
  (case (:api @init-opts)
    0 (get-data-0 process dev pos)
    1 (get-data-1 process dev pos)))

(defn set-data
  [process dev pos val]
  (case (:api @init-opts)
    0 (set-data-0 process dev pos val)
    1 (set-data-1 process dev pos val)))

(defn init-blk
  [count]
  (->> (str "dd if=/dev/zero of=" fsdev " count=" count)
       sudo-cmd))

; read one of block data from disk
(defn get-blk
  [process]
  (swap! iter dec)
  (get-data process fsdev (+ (* @iter 512) 0)))

(defn set-blk
  [process val]
  (let [x (* (dec @iter) 512)]
    (set-data process fsdev (+ x 0) val)))

; read cas from disk
(defn get-reg
  [process]
  (get-data process fsdev 0))

; write cas to disk
(defn set-reg
  [process val]
  (set-data process fsdev 0 val))

; add cas to disk
(defn add-reg
  [process val]
  (set-reg process (+ (get-reg process) val)))

(defn exec-on-node
  "Exec commands on node"
  [node cmd]
  (try
    (sh "ssh" (str "root@" node) cmd)
    true
    (catch RuntimeException _ false)))

(defn exec-on-tester
  "Exec commands on test nodes"
  [node cmds]
  (try
    (let [results (apply sh cmds)]
      (info "results: " results " for cmds: " cmds)
      )
    true
    (catch RuntimeException _ false)))

; client for operation execution
(defn client
  [op-timeout-ms]
  (reify client/Client
    (setup! [_ test node]
      (info "init-ops are: " @init-opts)
      (client (:op-timeout-ms @init-opts)))

    (invoke! [this test op]
      (timeout op-timeout-ms (assoc op :type :info, :error :timeout)
               (case (:f op)
                 :read (let [ret (get-reg (:process op))]
                         (if (> 0 ret) (assoc op :type :fail)
                                       (assoc op :type :ok, :value ret)))

                 :write (let [ret (set-reg (:process op) (:value op))]
                          (if (> 0 ret) (assoc op :type :fail)
                                        (assoc op :type :ok)))

                 :cas   (let [[value value'] (:value op)]
                          (if (= (get-reg (:process op)) value)
                            (if (> 0 (set-reg (:process op) value'))
                              (assoc op :type :fail)
                              (assoc op :type :ok))
                            (assoc op :type :fail)))

                 :add   (do (add-reg (:process op) (:value op))
                            (assoc op :type :ok)))))

    (teardown! [_ test]
      (info "op-timeout-ms: " op-timeout-ms))))

; client for block operation
(defn client-blk
  []
  (reify client/Client
    (setup! [this test node]
      (client-blk))

    (invoke! [this test op]
      (timeout 5000 (assoc op :type :info, :error :timeout)
               (case (:f op)
                 :read (let [ret (get-blk (:process op))]
                         (if (> 0 ret) (assoc op :type :fail)
                                       (assoc op :type :ok, :value ret)))

                 :write (let [ret (set-blk (:process op) (:value op))]
                          (if (> 0 ret) (assoc op :type :fail)
                                        (assoc op :type :ok))))))

  (teardown! [this test])))

; partition node for perf test
(defn split-node
  [n nodes]
  (let [coll (remove (fn [x] (= x n)) nodes)]
    [[n], coll]))

; cut off secondary nodes by turns
; assure first element of nodes is primary node
(defn split-node-seq
  [nodes]
  (let [num (dec (count nodes))
        i   (rem @iter num)]
    (swap! iter inc)
    (split-node (nth nodes (inc i)) nodes)))

; assure last element of nodes is control node
(defn split-node-ctrl
  [nodes]
  (let [num (count nodes)
        i   (rem @iter 2)]
    (swap! iter inc)
    (if (zero? i)
      (split-node (nth nodes 1) nodes)
      (split-node (nth nodes (dec num)) nodes))))

; partition specific node
(defn partition-node
  [n]
  (nemesis/partitioner (comp nemesis/complete-grudge (partial split-node n))))

; partition secondary nodes in turn
(defn partition-node-seq
  []
  (reset! iter 0)
  (nemesis/partitioner (comp nemesis/complete-grudge split-node-seq)))

; partition one secondary node and control node in turn
(defn partition-node-ctrl
  []
  (reset! iter 0)
  (nemesis/partitioner (comp nemesis/complete-grudge split-node-ctrl)))

; partition clients host
(defn drop-net
  []
  (sudo-cmd (str "iptables -A INPUT -s " node-ip " -j DROP")))

(defn heal-net
  []
  (sudo-cmd "iptables -F"))

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

; generate n normal operations
(defn op-limit
  [n gen]
  (let [life (atom (inc n))]
    (reify gen/Generator
      (op [_ test process]
        (if (= process :nemesis)
          (when (pos? @life)
            (gen/op gen test process))
          (when (pos? (swap! life dec))
            (gen/op gen test process)))))))

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
          (recur pairs cnt total))))))

(def perf-checker
  (reify checker/Checker
    (check [_ test model history opts]
      (merge {:valid? true} (total-time history)))))

; check if any unacknowledged but successful writes
(defn check-blk
  [history]
  (loop [history            (seq (history/complete history))
         total              0             ; total writes
         surv               0             ; survivors
         acks               ()            ; acked writes
         errors             ()]           ; unacked writes
    (if (nil? history)
      {:valid?             (empty? errors)
       :writes             {"total" total "acked" (count acks) "survived" surv}
       :errors             errors}

      (let [op      (first history)
            history (next history)]

        (case [(:type op) (:f op)]
          [:invoke :read]
          (recur history total surv acks errors)

          [:ok :read]
          (if (number? (:value op))
            (if (some (partial = (:value op)) acks)
              (recur history total (inc surv) acks errors)
              (recur history total (inc surv) acks (conj errors (:value op))))
            ;null data init
            (recur history total surv acks errors))

          [:invoke :write]
          (recur history (inc total) surv acks errors)

          [:ok :write]
          (recur history total surv (conj acks (:value op)) errors)

          (recur history total surv acks errors))))))

(def cs-checker
  (reify checker/Checker
    (check [_ test model history opts]
      (merge {:valid? true} (check-blk history)))))

(def nemesis-exec-tester
  "Run cmd on node"
  (reify client/Client
    (setup! [this test node] this)
    (invoke! [this test op]
      (case (:f op)
        :start
          (let [node (:nemesis-node-ip @init-opts)
                cmds (:start (get nic-cmds node))]
            (exec-on-tester node cmds)
            (assoc op :value (str "Ran start nemesis on node: " node ": " cmds)))
        :stop
        (let [node (:nemesis-node-ip @init-opts)
              cmds (:stop (get nic-cmds node))]
          (exec-on-tester node cmds)
          (assoc op :value (str "Ran stop nemesis on node: " node ": " cmds)))
        ))
    (teardown! [this test]  (let [node (:nemesis-node-ip @init-opts)
                                  cmds (:stop (get nic-cmds node))]
                              (exec-on-tester node cmds)
                              this))))

(defn fscap-test
  [opts]
  (merge tests/noop-test
         opts
         {
          ;    :nodes [:n1 :n2 :n3]
          :nodes ["172.18.0.6" "172.18.0.5" "172.18.0.2"]
          :name "fscp-test"
          :concurrency 3
          :client (client (:op-timeout-ms @init-opts))
          :nemesis (case (:nemesis @init-opts)
                     "noop" nemesis/noop
                     "partition-random-halves" (nemesis/partition-random-halves)
                     "partition-node" (nemesis/partition-node (:nemesis-node-ip @init-opts) (:no-heal? @init-opts) (:cut-once? @init-opts))
                     "exec-on-tester" nemesis-exec-tester
                     )
          ;:nemesis nemesis/noop
          ;                    :nemesis (nemesis/partition-random-halves)
          ;:nemesis (nemesis/partition-node "172.18.0.7" false false)
          :generator (->> (gen/mix [r w])
                          (gen/stagger 1)
                          (gen/nemesis
                            (gen/seq (cycle [(gen/sleep (:nemesis-before-secs @init-opts))
                                             {:type :info, :f :start}
                                             (gen/sleep (:nemesis-between-secs @init-opts))
                                             {:type :info, :f :stop}
                                             (gen/sleep (:nemesis-after-secs @init-opts))])))
                          (gen/time-limit (:time-limit-secs @init-opts)))
          :model (cas-register 0)
          ;:checker checker/linearizable)
          :checker (checker/compose
                     {:perf   (checker/perf)
                      :linear checker/linearizable})
          }))

; main entry
(defn -main
  "Test entry."
  [& args]
  (info "consistency test\n")

  (let [test-args
        (loop [rest-args args]
          (info "rest args: " rest-args)
          (case (first rest-args)
            "--op-timeout-ms"
            (do
              (swap! init-opts assoc :op-timeout-ms (Long/parseLong (second rest-args)))
              (recur (nnext rest-args)))
            "--nemesis-before-secs"
            (do
              (swap! init-opts assoc :nemesis-before-secs (Long/parseLong (second rest-args)))
              (recur (nnext rest-args)))
            "--nemesis-between-secs"
            (do
              (swap! init-opts assoc :nemesis-between-secs (Long/parseLong (second rest-args)))
              (recur (nnext rest-args)))
            "--nemesis-after-secs"
            (do
              (swap! init-opts assoc :nemesis-after-secs (Long/parseLong (second rest-args)))
              (recur (nnext rest-args)))
            "--time-limit-secs"
            (do
              (swap! init-opts assoc :time-limit-secs (Long/parseLong (second rest-args)))
              (recur (nnext rest-args)))
            "--process-max"
            (do
              (swap! init-opts assoc :process-max (Long/parseLong (second rest-args)))
              (recur (nnext rest-args)))
            "--api"
            (do
              (swap! init-opts assoc :api (Long/parseLong (second rest-args)))
              (recur (nnext rest-args)))
            "--nemesis"
            (do
              (swap! init-opts assoc :nemesis (second rest-args))
              (recur (nnext rest-args)))
            "--nemesis-node-ip"
            (do
              (swap! init-opts assoc :nemesis-node-ip (second rest-args))
              (recur (nnext rest-args)))
            "--no-heal"
            (do
              (swap! init-opts assoc :no-heal? (= "true" (second rest-args)))
              (recur (nnext rest-args)))
            "--cut-once"
            (do
              (swap! init-opts assoc :cut-once? (= "true" (second rest-args)))
              (recur (nnext rest-args)))
            rest-args))]
    (info "init-ops are: " @init-opts)
    ;    (prepare-data-files (:process-max @init-opts))
    (set-reg 0 0)
    (cli/run! (cli/single-test-cmd {:test-fn fscap-test}) test-args)))
;(-main "--nemesis" "noop" "test")
;(-main "--nemesis" "exec-on-node" "--nemesis-node-ip" "172.18.0.4"    "test")