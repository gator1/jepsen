(ns file-r.nemesis
  (:require [clojure.tools.logging :refer :all]
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
        :start (assoc op :value (str "start noop-nemesis <<<<< "))
        :stop  (assoc op :value (str "stop noop-nemesis >>>>> "))))
    (teardown! [this test] this)))
