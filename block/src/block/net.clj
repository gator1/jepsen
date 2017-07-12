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

