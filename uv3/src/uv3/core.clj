(ns uv3.core
  (:require [clojure.tools.logging :refer :all]
            [jepsen [core      :as jepsen]
                    [client    :as client]
                    [nemesis   :as nemesis]
                    [generator :as gen]
                    [checker   :as checker]
                    [control   :as c]
                    [tests     :as tests]
                    [util      :refer [timeout]]]
            [jepsen.control.net :as control.net]
            [knossos.model :refer [cas-register]]))


; define operations
(defn r   [_ _] {:type :invoke, :f :read, :value nil})
(defn w   [_ _] {:type :invoke, :f :write, :value (rand-int 5)})
(defn cas [_ _] {:type :invoke, :f :cas, :value [(rand-int 5)(rand-int 5)]})

(defn get-reg
  []
  ; UNIBLKIODoIO -drive dev -pattern SD0 -mode RC -thread 4 -pass_count 1 -size 16 -seek_type S
  )

(defn set-reg
  [val]
  ; UNIBLKIODoIO -drive dev -pattern SD0 -mode WC -thread 4 -pass_count 1 -size 16 -seek_type S
  )

; client for operation execution
(defn client
  []
  (reify client/Client
    (setup! [_ test node]
      (client))

    (invoke! [this test op]
      (timeout 5000 (assoc op :type :info, :error :timeout)
               (case (:f op)
                 :read  (assoc op :type :ok, :value (get-reg))

                 :write (do (set-reg (:value op))
                            (assoc op :type :ok))

                 :cas   (let [[value value'] (:value op)]
                          (if (= (get-reg) value)
                            (do (set-reg value')
                                (assoc op :type :ok))
                            (assoc op :type :fail))))))

    (teardown! [_ test])))

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
