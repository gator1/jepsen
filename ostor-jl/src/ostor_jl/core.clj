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
  (:refer-clojure :exclude [read write] :rename {set cset}))

(def ^:private disk-size 128)
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

(defprotocol Sync
  (set [this address value]))

(def ^:private timeout-interval 5000)

(defprotocol NemesisHook
  (disable [_ idx])
  (enable [_ idx]))

(defn- lru-cache [size backing-store]
  (let [cache (proxy [java.util.LinkedHashMap] [(+ 1 size) 2.0 true]
                (removeEldestEntry [e] (> (proxy-super size) size)))
        disabled? (atom false)
        store (atom backing-store)]
    (reify
      MemoryStore
      (read [_ address]
        (if @disabled? (Thread/sleep timeout-interval)
            (locking cache
              (if (.containsKey cache address)
                (.get cache address)
                (.put cache address (read @store address))))))
      (write [_ address value]
        (if @disabled? (Thread/sleep timeout-interval)
            (locking cache
              (write @store address value)
              (.put cache address value))))
      HotSwap
      (swap [_ new-store] (reset! store new-store))
      NemesisHook
      (disable [_ _] (reset! disabled? true))
      (enable [_ _] (.clear cache) (reset! disabled? false))
      Sync
      (set [_ address value]
        (if (not @disabled?)
          (.put cache address value))))))

(defn- translate-address [segment-size segment-vector address]
  [(segment-vector (quot address segment-size)) (rem address segment-size)])

(defn- create-tandem [disk1 disk2]
  (let [cache-a1 (lru-cache cache-size disk1)
        cache-a2 (lru-cache cache-size nil)
        cache-b1 (lru-cache cache-size nil)
        cache-b2 (lru-cache cache-size disk2)
        caches (atom {disk1 {:primary cache-a1 :replica cache-b1}
                      disk2 {:primary cache-b2 :replica cache-a2}})]
    (letfn [(address->disk-offset [address]
              (translate-address disk-size [disk1 disk2] address))
            (operate [disk offset operation]
              (locking disk
                (let [[cache1 cache2] [(get-in @caches [disk :primary]) (get-in @caches [disk :replica])]
                      res (operation cache1)]
                  (if cache2 (set cache2 offset res))
                  res)))
            (disable- [disk caches-to-disable cache-to-take-disk]
              (locking disk
                (doseq [cache caches-to-disable] (disable cache nil))
                (swap cache-to-take-disk disk)
                (swap! caches assoc disk {:primary (get-in @caches [disk :replica]) :replica nil})))
            (enable- [disk caches-to-enable cache-to-take-disk]
              (locking disk
                (swap cache-to-take-disk disk)
                (doseq [cache caches-to-enable] (enable cache nil))
                (swap! caches assoc disk {:replica (get-in @caches [disk :primary])
                                          :primary cache-to-take-disk})))]
      (reify
        MemoryStore
        (read [_ address]
          (let [[disk offset] (address->disk-offset address)]
            (operate disk offset #(read % offset))))
        (write [_ address value]
          (let [[disk offset] (address->disk-offset address)]
            (operate disk offset #(write % offset value))))
        NemesisHook
        (disable [_ idx]
          (case idx
            0 (disable- disk1 [cache-a1 cache-a2] cache-b1)
            1 (disable- disk2 [cache-b2 cache-b1] cache-a2)))
        (enable [_ idx]
          (case idx
            0 (enable- disk1 [cache-a1 cache-a2] cache-a1)
            1 (enable- disk2 [cache-b2 cache-b1] cache-b2)))))))

(def ^:private disks (delay (repeatedly *array-size* ostor-disk)))
(def ^:private cache (delay (apply create-tandem @disks))) ; NOTE assumes *array-size* equals 2

(defn ostor-simulator []
  (reify client/Client
    (setup! [this test node]
      this)
    (invoke! [this test op]
      (timeout timeout-interval (assoc op :type :info :error :timeout)
               (case (:f op)
                 :txn (let [[[operator offset value]] (:value op)]
                        (case operator
                          :read (assoc op :type :ok :value [[:read offset (read @cache offset)]])
                          :write (do (write @cache offset value)
                                     (assoc op :type :ok :value [[:write offset value]])))))))
    (teardown! [_ test])))

(defn- linkdown [idx]
  (disable @cache idx))

(defn- linkup [idx]
  (enable @cache idx))

(defn nemesis []
  (let [down (atom #{})
        do-link (fn [op idx swap-op link-fn msg]
                  (assoc op :value
                         (if idx
                           (do
                             (link-fn idx)
                             (swap! down swap-op idx)
                             (format "Controller: %s %s" idx msg))
                           (format "All controllers already %s" msg))))]
    (reify client/Client
      (setup! [this test _]
        this)
      (invoke! [this test op]
        (case (:f op)
          :start (let [idx (first (take 1 (shuffle (clojure.set/difference (cset (range *array-size*)) @down))))]
                   (do-link op idx conj linkup "up"))
          :stop  (let [idx (first (take 1 (shuffle @down)))]
                   (do-link op idx disj linkdown "down"))))
      (teardown! [this test]
        (linkup 0)))))
