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
            [jepsen.control.net :as control.net])
  (:refer-clojure :exclude [read write]))

(def ^:private disk-size 48)
(def ^:private cache-size (/ disk-size 2))

(def ^:dynamic *array-size* 2)

(defn rand-address [] (rand-int (* disk-size *array-size*)))

(defn r [_ _] {:type :invoke :f :txn :value [[:read (rand-address) nil]]})
(defn w [_ _] {:type :invoke :f :txn :value [[:write (rand-address) (+ 1 (rand-int 255))]]})

(defprotocol MemoryStore
  (read  [this address])
  (write [this address value]))

(defprotocol HotSwap
  (swap [this store]))

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

(defprotocol Sync
  (set [this address value]))

(defn- lru-cache [size backing-store]
  (let [cache (proxy [java.util.LinkedHashMap] [(+ 1 size) 2.0 true]
                (removeEldestEntry [e] (> (proxy-super size) size)))
        store (atom backing-store)]
    (reify
      MemoryStore
      (read [_ address]
        (locking cache
          (if (.containsKey cache address)
            (.get cache address)
            (.put cache address (.read @store address)))))
      (write [_ address value]
        (locking cache
          (.write @store address value)
          (.put cache address value)))
      HotSwap
      (swap [_ new-store] (reset! store new-store))
      Sync
      (set [_ address value]
        (.put cache address value)))))

(defn- translate-address [segment-size segment-vector address]
  [(segment-vector (quot address segment-size)) (rem address segment-size)])

(defn- create-tandem [disk1 disk2]
  (let [cache-a1 (lru-cache cache-size disk1)
        cache-a2 (lru-cache cache-size ostor-noop-disk)
        cache-b1 (lru-cache cache-size ostor-noop-disk)
        cache-b2 (lru-cache cache-size disk2)]
    (letfn [(disk-and-caches [address]
              (translate-address disk-size [[disk1 cache-a1 cache-b1]
                                            [disk2 cache-b2 cache-a2]] address))]
      (reify MemoryStore
        (read [_ address]
          (let [[[disk cache1 cache2] offset] (disk-and-caches address)]
            (locking disk
              (let [res (.read cache1 offset)]
                (.set cache2 offset res)
                res))))
        (write [_ address value]
          (let [[[disk cache1 cache2] offset] (disk-and-caches address)]
            (locking disk
              (let [res (.write cache1 offset value)]
                (.set cache2 offset res)
                res))))))))

(defn ostor-simulator []
  (let [disk1 (ostor-disk)
        disk2 (ostor-disk)
        cache (create-tandem disk1 disk2)]
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
