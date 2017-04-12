(ns block-e.nemesis
  (:require [clojure.tools.logging :refer :all]
            [block-e.core :refer :all]
            [jepsen [core :as jepsen]
             [client :as client]
             [nemesis :as nemesis]
             [generator :as gen]
             [checker :as checker]
             [control :as c]
             ])
  (:use     [clojure.java.shell :only [sh]]) )

(defn test-nemesis-1
  "Does nothing."
  []
  (reify client/Client
    (setup! [this test node] this)
    (invoke! [this test op]
      (case (:f op)
        :start (assoc op :value (str  "start noop-nemesis <<<<< "))
        :stop  (assoc op :value (str  "stop noop-nemesis >>>>> "))))
    (teardown! [this test] this)))

(defn test-nemesis-2
  "Change value."
  []
  (reify client/Client
    (setup! [this test node] this)
    (invoke! [this test op]
      (case (:f op)
        :start (let [proc (+ 1 (rand-int 4))
                     val (+ 5 (rand-int 5))]
                 (set-reg proc val)
                 (assoc op :value (str "Write " val " into " proc)))
        :stop  (assoc op :value (str  "stop noop-nemesis >>>>> "))))
    (teardown! [this test] this)))

