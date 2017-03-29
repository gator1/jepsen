(ns uv3-e.nemesis
  (:require [clojure.tools.logging :refer :all]
            [uv3-e.core :refer :all]
            [jepsen [core :as jepsen]
             [client :as client]
             [nemesis :as nemesis]
             [generator :as gen]
             [checker :as checker]
             [control :as c]
             ])
  (:use     [clojure.java.shell :only [sh]]) )

; force pcie 0 link down
; pcie id should be found before running the test
(defn linkdown
  [node]
  ;(c/on node (c/exec (c/lit "mml")) (c/exec (c/lit "pcie forcelinkdown 0 1")))
  )

(defn linkup
  [node]
  ;(c/on node (c/exec (c/lit "mml")) (c/exec (c/lit "pcie forcelinkup 0 0")))
  )

(defn partition-uni
  []
  (reify client/Client
    (setup! [this test _]
      this)

    (invoke! [this test op]
      (case (:f op)
        :start (do (linkdown (first (:nodes test)))
                   (assoc op :value "Cut off PCIE link"))
        :stop  (do (linkup (first (:nodes test)))
                   (assoc op :value "fully connected"))))

    (teardown! [this test]
      (linkup (first (:nodes test)))))
  )

(defn write-wrong-value
  "Change value."
  []
  (reify client/Client
    (setup! [this test node] this)
    (invoke! [this test op]
      (case (:f op)
        :start (let [val (+ 5 (rand-int 5))]
                 (set-reg val)
                 (assoc op :value (str "Write " val " into reg")))
        :stop  (assoc op :value (str  "stop noop-nemesis >>>>> "))))
    (teardown! [this test] this)))

