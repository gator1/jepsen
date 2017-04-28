(ns file-e.nemesis
  (:require [clojure.tools.logging :refer :all]
            ;[file-e.core :refer :all]
            [file-e.util :as util]
            [jepsen [core :as jepsen]
             [client :as client]
             [nemesis :as nemesis]
             [control :as c]
             [net :as net]
             ])
  (:use     [clojure.java.shell :only [sh]]))

(defrecord Node [name f1 f2 b1 b2])
(def node1-ip (Node. "n1" "n1" "n1" "n1" "n1"))
(def node2-ip (Node. "n2" "n2" "n2" "n2" "n2"))
(def node3-ip (Node. "n3" "n3" "n3" "n3" "n3"))
(def node4-ip (Node. "n4" "n4" "n4" "n4" "n4"))
(def node5-ip (Node. "n5" "n5" "n5" "n5" "n5"))
(def nodes [node1-ip node2-ip node3-ip node4-ip node5-ip])
;(def nodes [node1-ip node2-ip node3-ip])
(def test-node (atom nil))

(defrecord Port [f1 f2 b1 b2])
(def port (Port. "SLOT4-1" "SLOT5-1" "SLOT4-0" "SLOT5-0"))


(def pwd-sudo "ubuntu")
(defn sudo-cmd
  [cmd]
  (sh "sh" "-c" (str "echo " pwd-sudo  " | sudo -S " cmd)))

(defn reboot-node
  [node]
;  (c/on node (c/su (c/exec :reboot))))
;  (c/on node (c/su (c/exec :shutdown :-r :now))))
;  (c/on node (c/su (c/exec (str "shutdown -r now")))))
  (c/on node (c/su (c/exec :shutdown :-r "now"))))

(defn port-out-service
  [node port]
      (c/on node (c/su (c/exec :iptables :-A :INPUT :-i port :-j :DROP))))

(defn all-ports-out-service
  [node port1 port2]
  (do
      (port-out-service node port1)
      (Thread/sleep 1000)
      (port-out-service node port2)))

(defn network-drop-cmd
  [port]
  (str "cfe inject rNetwork_drop (srcIP,srcPort,destIP,destPort) value(" port ",all,all,all)" ))

(defn network-drop1
  "Drop all frontend/backend ports of one node"
  [node front]
  (if front
    (map #(c/on (.b1 node) (c/su (c/exec (network-drop-cmd %)))) 
         [(.f1 node) (.f2 node)] )
    (map #(c/on (.f1 node) (c/su (c/exec (network-drop-cmd %)))) 
         [(.b1 node) (.b2 node)] )))

(defn network-drop
  "Drop all frontend/backend ports of one node"
  [node front]
  (if front
    (map #(c/on (.b1 node) (c/su (c/exec :cfe :inject :rNetwork_drop (str "(srcIP,srcPort,destIP,destPort)") 
                                         :value (str "(" % ",all,all,all)")))) 
         [(.f1 node) (.f2 node)])
    (map #(c/on (.f1 node) (c/su (c/exec :cfe :inject :rNetwork_drop "(srcIP,srcPort,destIP,destPort)" 
                                         :value "(" % ",all,all,all)")) 
         [(.b1 node) (.b2 node)]))))

(defn network-service-down
  "Stop network service for a perticular time"
  [node time]
    (c/on (.f1 node) (c/su (c/exec :cfe :inject :rNetSrv_down_Ex "(duration)" :value "(" time ")"))))

(defn nemesis-test-1
  "Write wrong value."
  []
  (reify client/Client
    (setup! [this test node] this)
    (invoke! [this test op]
      (case (:f op)
        :start (let [node (+ 1 (rand-int 3))
                     loc  (util/location node)
                     v (+ 5 (rand-int 5))]
                 (util/set-reg loc v)
                 (assoc op :value (str "Write " v " into " loc)))
        :stop  (assoc op :value (str "stop nemesis-test-1 >>>>> "))))
    (teardown! [this test] this)))

(defn nemesis-test-2
  "Reboot one node"
  []
  (reify client/Client
    (setup! [this test node] this)
    (invoke! [this test op]
      (case (:f op)
        :start (let [node (rand-nth nodes)]
                 (reboot-node (.b1 node))
                 (assoc op :value (str "Rebooted " (.name node))))
        :stop  (assoc op :value (str "stop nemesis-test-2 >>>>> "))))
    (teardown! [this test] this)))

(defn nemesis-test-3
  "Shutdown one selected mds, then start it"
  []
  (reify client/Client
    (setup! [this test node] this)
    (invoke! [this test op]
      (case (:f op)
        :start (do
                 (reset! test-node (rand-nth nodes))
                 (c/on (.b1 @test-node)  (c/su (c/exec :shutdown :-h :now))
                 (assoc op :value (str "shutdown " (.name @test-node)))))
        :stop  (let [ret (sudo-cmd (str "lxc-start -d -n " (.b1 @test-node)))
                     _ (println (str "exit - " (:exit ret) " err - " (:err ret)))]
                 (if (= 0 (:exit ret)) 
                   (assoc op :value (str "start " (.name @test-node) " success"))
                   (assoc op :value (str "start " (.name @test-node) " fail"))))))
    (teardown! [this test] this)))

(defn nemesis-test-4
  "N_TC_DFR_System_Network_Redundancy_Back_002
   block all backend ports of one node"
  []
  (reify client/Client
    (setup! [this test _]
      (net/heal! (:net test) test)
      this)
    
    (invoke! [this test op]
      (case (:f op)
        :start (let [node (rand-nth nodes)]
                 (network-drop node false)
                 (assoc op :value (str "Block " (.name node) " backend ports")))
        :stop  (do (net/heal! (:net test) test)
                   (assoc op :value "fully connected"))))
    
    (teardown! [this test]
      (net/heal! (:net test) test))))

(defn nemesis-test-5
  "N_TC_DFR_System_Network_Redundancy_Back_002
   block all backend ports of two nodes"
  []
  (reify client/Client
    (setup! [this test _]
      (net/heal! (:net test) test)
      this)
    
    (invoke! [this test op]
      (case (:f op)
        :start (let [node (take 2 (shuffle nodes))]
                 (map #(network-drop % false) node)
                 (assoc op :value (str "Block " (.name (nth node 0)) " and " (.name (nth node 1)) " backend ports")))
        :stop  (do (net/heal! (:net test) test)
                   (assoc op :value "fully connected"))))
    
    (teardown! [this test]
      (net/heal! (:net test) test))))

(defn nemesis-test-6
  "N_TC_DFR_System_Cluster_CM_004
   Stop all backend ports at the same time"
  []
  (reify client/Client
    (setup! [this test _]
      (net/heal! (:net test) test)
      this)
    
    (invoke! [this test op]
      (case (:f op)
        :start (let [node (rand-nth nodes)]
                 (all-ports-out-service (.f1 node) (.b1 port) (.b2 port))
                 (assoc op :value (str "Block all backend ports on " (.name node))))
        :stop  (do (net/heal! (:net test) test)
                   (assoc op :value "fully connected"))))
    
    (teardown! [this test]
      (net/heal! (:net test) test))))

(def flag (atom true))
(defn nemesis-test-7
  "N_TC_DFR_System_Cluster_CM_005
   Stop all backend ports one at a time"
  []
  (reify client/Client
    (setup! [this test _]
      (net/heal! (:net test)
                 (do
                   (reset! test-node (rand-nth nodes))
                   test))
      this)
    
    (invoke! [this test op]
      (case (:f op)
        :start (if (true? @flag)
                 (do
                   (reset! flag false)
                   (port-out-service (.f1 @test-node) (.b1 port))
                   (assoc op :value (str "Block backend port " (.b1 port) " on " (.name @test-node))))
                 (do
                   (reset! flag true)
                   (port-out-service (.f1 @test-node) (.b2 port))
                   (assoc op :value (str "Block backend port " (.b2 port) " on " (.name @test-node)))))
        :stop  (do (net/heal! (:net test) test)
                 (assoc op :value "fully connected"))))
    
    (teardown! [this test]
      (net/heal! (:net test) test))))

(defn kill-process
 "Kill all of the process"
 [node proc]
 (try
   (c/on (.b1 node) (c/su (c/exec :pkill :-f proc )))
   true
   (catch RuntimeException _ false)))

(defn nemesis-test-8
  "Shutdown one kind of process on one node"
  []
  (reify client/Client
    (setup! [this test node] this)
    (invoke! [this test op]
      (case (:f op)
        :start (let [proc (str "linux")]
                 (reset! test-node (rand-nth nodes))
                 (kill-process @test-node proc)
                 (assoc op :value (str "stop " proc " on " (.name @test-node))))
        :stop  (assoc op :value (str "stop nemesis-test-8 >>>>> "))))
    (teardown! [this test] this)))

(defn nemesis-test-9
  "Shutdown one kind of process on one node"
  []
  (reify client/Client
    (setup! [this test node] this)
    (invoke! [this test op]
      (case (:f op)
        :start (let [proc (rand-nth ["linux" "cron" "exim4"])]
                 (reset! test-node (rand-nth nodes))
                 (kill-process @test-node proc)
                 (assoc op :value (str "stop " proc " on " (.name @test-node))))
        :stop  (assoc op :value (str "stop nemesis-test-8 >>>>> "))))
    (teardown! [this test] this)))

