(ns ostor-jl.core
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
            [knossos.model :refer [inconsistent]])
  (:refer-clojure :exclude [read write]))

(def ^:private disk-size 48)
(def ^:private cache-size (/ disk-size 2))

(defn r [_ _] {:type :invoke :f :txn :value [[:read (rand-int disk-size) nil]]})
(defn w [_ _] {:type :invoke :f :txn :value [[:write (rand-int disk-size) (+ 1 (rand-int 255))]]})

(def ^:private ostor-configurations #{:two :four :eight})

(defprotocol MemoryStore
  (read  [this address])
  (write [this address value]))

(defn- ostor-disk []
  (let [backing-store (atom {})]
    (reify MemoryStore
      (read [_ address]
        (@backing-store address))
      (write [_ address value]
        (swap! backing-store assoc address value)))))

(def ^:private ostor-noop-disk
  (reify MemoryStore
    (read [_ _] nil)
    (write [_ _ _] nil)))

(defn- lru-cache [size backing-store]
  (let [cache (proxy [java.util.LinkedHashMap] [size 2.0 true]
                (removeEldestEntry [e] (> (proxy-super size) size)))]
    (reify MemoryStore
      (read [_ address]
        (locking cache
          (if (.containsKey cache address)
            (.get cache address)
            (.put cache address (.read backing-store address)))))
      (write [_ address value]
        (locking cache
          (.write backing-store address value)
          (.put cache address value))))))

(defn ostor-simulator []
  (let [disk (ostor-disk)
        cache (lru-cache cache-size disk)]
    (reify client/Client
      (setup! [this test node]
        this)
      (invoke! [this test op]
        (timeout 5000 (assoc op :type :info :error :timeout)
                 (case (:f op)
                   :txn (let [[[operator offset value]] (:value op)]
                          (case operator
                            :read (assoc op :type :ok :value [[:read offset (.read cache offset)]])
                            :write (do (.write cache offset value)
                                       (assoc op :type :ok :value [[:write offset value]])))))))
      (teardown! [_ test]))))

(defn- linkdown [node]

  )

(defn- linkup [node]

  )

(defn nemesis []
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
      (linkup (first (:nodes test))))))
