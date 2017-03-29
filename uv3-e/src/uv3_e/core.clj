(ns uv3-e.core
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
(def ^:private file-path "reg")


(defn get-reg
  []
  (Integer. (re-find  #"\d+" (slurp file-path))))

(defn set-reg
  [val]
  (spit file-path val))

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

