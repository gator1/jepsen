(ns block.core
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
(def fsdev "/dev/rbd0")
(def data "/home/vagrant/jepsen/ceph-block/data/temp")
(def offset 12288)

; define host sudo password and primary node ip
(def pwd-sudo "root")
(def node-ip  "172.21.12.16")

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

; get data on specific position from disk
(defn get-data
  [process dev pos]
  (sh "sh" "-c" (str "dd if=/dev/zero count=1 > " data process))
  (let [ret (->> (str "cat " data process)
                 (str "dd if=" dev " skip=" (+ offset pos) " of=" data process " count=1 iflag=direct;")
                 sudo-cmd)]
    (if (= 0 (:exit ret)) (edn/read-string (:out ret)) -1)))

(defn set-data
  [process dev pos val]
  (sh "bash" "-c" (str "cat <(echo " val ") <(dd if=/dev/zero bs=1 count=510) > " data process))
  (let [ret (sudo-cmd (str "dd if=" data process " of=" dev " seek=" (+ offset pos) " count=1 oflag=direct"))]
    (if (= 0 (:exit ret)) 0 -1)))

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

; client for operation execution
(defn client
  []
  (reify client/Client
    (setup! [_ test node]
      (client))

    (invoke! [this test op]
      (timeout 10000 (assoc op :type :info, :error :timeout)
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

    (teardown! [_ test])))

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

; main entry
(defn -main
  "Test entry."
  [])
