(ns block.nemesis
    (:require [clojure.tools.logging :refer :all]
      [block.core :refer :all]
      [block.net :refer :all]
      [jepsen [core :as jepsen]
       [client :as client]
       [nemesis :as nemesis]
       [control :as c]
       [net :as net]
       ])
    (:use     [clojure.java.shell :only [sh]]))

(def test-nodes (atom nil))

(defn nemesis-nodes
      "Run nemesis on nodes selected with fn-nodes, and with passed in start and stop functions."
      [fn-nodes desc fn-start fn-stop]
      (reify client/Client
             (setup! [this test node] this)
             (invoke! [this test op]
                      (case (:f op)
                            :start  (reset! test-nodes (fn-nodes (:nodes test)))
                                    (doall (map fn-start @test-nodes))
                                    (assoc op :value (str  "start " desc " nemesis <<<<< "))
                            :stop   (doall (map fn-stop @test-nodes))
                                    (assoc op :value (str  "stop " desc " nemesis >>>>> "))))
             (teardown! [this test] this)))

(defn rand-1 [test] (rand-nth (:nodes test)))
(defn rand-2 [test] (take 2 (shuffle (:nodes test))))
(defn rand-3 [test] (take 3 (shuffle (:nodes test))))
(defn rand-n [test]
      (let [nodes (:nodes test)
            n (+ 1 (rand-int (- (count nodes) 2)))]
           (take n (shuffle nodes))))

(defn nemesis-storage-net-down [fn-nodes test]
      (nemesis-nodes fn-nodes "storage-net-down" (partial down-port :eth2) (partial up-port :eth2)))

(defn nemesis-storage-net-drop [fn-nodes test]
      (nemesis-nodes fn-nodes "storage-net-drop" (partial drop-port :eth2) (partial heal-port :eth2)))

(defn nemesis-storage-net-stop [fn-nodes test]
      (nemesis-nodes fn-nodes "storage-net-stop" (partial stop-port :eth2) (partial start-port :eth2)))

(defn nemesis-storage-net-loss [fn-nodes test]
      (nemesis-nodes fn-nodes "storage-net-loss" (partial loss-port :eth2) (partial unloss-port :eth2)))

(defn nemesis-slow-management [fn-nodes test]
      (nemesis-nodes fn-nodes "slow-management" (partial slow-port :eth0) (partial unslow-port :eth0)))

(defn nemesis-loss-management [fn-nodes test]
      (nemesis-nodes fn-nodes "loss-management" (partial loss-port :eth0) (partial unloss-port :eth0)))

(defn nemesis-dup-management [fn-nodes test]
      (nemesis-nodes fn-nodes "dup-management" (partial dup-port :eth0) (partial undup-port :eth0)))

(defn nemesis-reorder-management [fn-nodes test]
      (nemesis-nodes fn-nodes "reorder-management" (partial reorder-port :eth0) (partial unreorder-port :eth0)))

(defn nemesis-kill-zookeeper [fn-nodes test]
      (nemesis-nodes fn-nodes "kill-zookeeper" kill-zookeeper nothing))

(defn nemesis-kill-agent [fn-nodes test]
      (nemesis-nodes fn-nodes "kill-agent" kill-agent nothing))

(defn nemesis-kill-osd [fn-nodes test]
      (nemesis-nodes fn-nodes "kill-osd" kill-osd nothing))

(defn nemesis-kill-vbs [fn-nodes test]
      (nemesis-nodes fn-nodes "kill-vbs" kill-vbs nothing))

(defn nemesis-kill-mdc [fn-nodes test]
      (nemesis-nodes fn-nodes "kill-mdc" kill-osd nothing))

