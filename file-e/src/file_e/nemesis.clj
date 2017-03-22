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

(defn noop-nemesis
  "Does nothing."
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
        :stop  (assoc op :value (str "stop noop-nemesis >>>>> "))))
    (teardown! [this test] this)))
