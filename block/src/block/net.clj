(ns block.net
  (:require 
    [block.core :refer :all]
    [jepsen.control.net :as control.net]
    [jepsen.net :as net])
  (:use [jepsen.control]))

(def block-iptables
  "The iptables for block."
  (reify net/Net
    (drop! [net test src dest]
      (do
        (on dest (su (exec :iptables :-A :INPUT :-s (control.net/ip src) :-j :DROP)))))
    
    (heal! [net test]
      (on-many (:nodes test)
        (su
          (exec :iptables :-F)
          (exec :iptables :-X))))
    
    (slow! [net test]
      (on-many (:nodes test)
               (su (exec :tc :qdisc :add :dev :eth0 :root :netem :delay :50ms
                         :10ms :distribution :normal))))
    
    (flaky! [net test]
      (on-many (:nodes test)
               (su (exec :tc :qdisc :add :dev :eth0 :root :netem :loss "20%"
                         "75%"))))
    
    (fast! [net test]
      (on-many (:nodes test)
               (exec :tc :qdisc :del :dev :eth0 :root)))))

(defn kill-process
      "Kill all of the process"
      [proc node]
      (try
        (on node (su (exec :pkill :-f proc )))
        true
        (catch RuntimeException _ false)))

(defn kill-zookeeper [node] (kill-process "zookeeper" node))
(defn kill-agent [node] (kill-process "dsware_agent" node))
(defn kill-osd [node] (kill-process "dsware_osd" node))
(defn kill-vbs [node] (kill-process "dsware_vbs" node))
(defn kill-mdc [node] (kill-process "dsware_mdc" node))

(defn nothing [node])

(defn up-port
      [port node]
      (on node (su (exec :ifconfig port :up))))

(defn down-port
      [port node]
      (on node (su (exec :ifconfig port :down))))

(defn drop-port
      [port node]
      (on node (su (exec :iptables :-A :INPUT :-i port :-j :DROP))))

(defn heal-port
      [port node]
      (on node (su (exec (lit "iptables -F && iptables -X")))))

(defn stop-port
      [port node]
      (on node (su (exec (str "/etinit.d/network stop " port)))))

(defn start-port
      [port node]
      (on node (su (exec (str "/etinit.d/network start " port)))))

(defn slow-port
      [port node]
      (on node (su (exec :tc :qdisc :add :dev port :root :netem :delay :100ms :100ms))))

(defn unslow-port
      [port node]
      (on node (su (exec :tc :qdisc :dev :dev port :root :netem :delay :100ms :100ms))))

(defn loss-port
      [port node]
      (on node (su (exec :tc :qdisc :add :dev port :root :netem :loss :100%))))

(defn unloss-port
      [port node]
      (on node (su (exec :tc :qdisc :del :dev port :root :netem :loss :100%))))

(defn dup-port
      [port node]
      (on node (su (exec :tc :qdisc :add :dev port :root :netem :duplicate :100%))))

(defn undup-port
      [port node]
      (on node (su (exec :tc :qdisc :add :dev port :root :netem :duplicate :100%))))

(defn reorder-port
      [port node]
      (on node (su (exec :tc :qdisc :add :dev port :root :netem :delay :10ms :reorder :25% :50%))))

(defn unreorder-port
      [port node]
      (on node (su (exec :tc :qdisc :del :dev port :root :netem :delay :10ms :reorder :25% :50%))))


