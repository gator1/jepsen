(ns file-e.nemesis
  (:require [clojure.tools.logging :refer :all]
            [file-e.core :refer :all]
            [jepsen [core :as jepsen]
             [client :as client]
             [nemesis :as nemesis]
             [generator :as gen]
             [checker :as checker]
             [control :as c]
             ])
  (:use     [clojure.java.shell :only [sh]]) )

(def pwd-sudo "ubuntu")
(defn sudo-cmd
  [cmd]
  (sh "sh" "-c" (str "echo " pwd-sudo  " | sudo -S " cmd)))

(defn nemesis-test-1
  "Write wrong value."
  []
  (reify client/Client
    (setup! [this test node] this)
    (invoke! [this test op]
      (case (:f op)
        :start (let [node (+ 1 (rand-int 3))
                     loc  (location node)
                     v (+ 5 (rand-int 5))]
                 (set-reg loc v)
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
        :start (let [node (str "n5")]
                 (do
                   (c/on node (c/su (c/exec :shutdown :-r :now)))
                   (assoc op :value (str "Rebooted " node)))
        :stop  (assoc op :value (str "stop nemesis-test-2 >>>>> ")))))
    (teardown! [this test] this)))

(def MDS {:n4 "n4" :n5 "n5"})
(def mds (atom nil))
(defn nemesis-test-3
  "Shutdown one selected mds, then start it"
  []
  (reify client/Client
    (setup! [this test node] this)
    (invoke! [this test op]
      (case (:f op)
        :start (do
                 (reset! mds (rand-nth (keys MDS)))
                 (c/on (@mds MDS)  (c/su (c/exec :shutdown :-h :now))
                 (assoc op :value (str "shutdown " @mds))))
        :stop  (let [ret (sudo-cmd (str "lxc-start -d -n " (@mds MDS)))
                     _ (println (str "exit - " (:exit ret) " err - " (:err ret)))]
                 (if (= 0 (:exit ret)) 
                   (assoc op :value (str "start " (@mds MDS) " success"))
                   (assoc op :value (str "start " (@mds MDS) " fail"))))))
    (teardown! [this test] this)))

