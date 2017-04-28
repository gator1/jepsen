(ns file-e.net 
  (:require 
    ;[file-e.core :refer :all]
    [jepsen.control.net :as control.net]
    [jepsen.net :as net])
  (:use [jepsen.control]))

;(def backup-map {:n1 :b1 :n2 :b2 :n3 :b3})
(def backup-map {:n1 :n1 :n2 :n2 :n3 :n3})
(def file-iptables
  "The iptables for file."
  (reify net/Net
    (drop! [net test src dest]
      (do
        ;        (println "file-iptables")
        (on dest (su (exec :iptables :-A :INPUT
                           :-s (str (control.net/ip src) "," (control.net/ip (src backup-map)))
                           :-j :DROP)))))

    (heal! [net test]
      (on-many (:nodes test) (su
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
