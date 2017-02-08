(ns object.core
  (:require [clojure.tools.logging :refer :all]
            [jepsen [core :as jepsen]
             [client :as client]
             [nemesis :as nemesis]
             [generator :as gen]
             [checker :as checker]
             [control :as c]
             [db      :as db]
             [independent :as independent]
             [tests :as tests]
             [util :refer [timeout]]]
            [knossos.model :refer [cas-register]]
            [knossos.history :as history]
            [clojure.edn :as edn]
            [clojure.string :as str])
  (:use     [clojure.java.shell :only [sh]]) )

(defn nid
  [n]
  (->> (str n)
       (re-find #"\d+")
       (Integer. )))

; definitions for multi-register
(def key-list [1 2 3 4 5 6 7 8])
(def ^:private obj-name "/testbucket/jepsenobj")
(def ^:private http-ok "HTTP/1.1 200 OK")

; operations for multi-register
(defn mr [_ _]
  (let [k (rand-nth key-list)]
    {:type :invoke :f :txn :value (independent/tuple k [[:read k nil]])}))

(defn mw [_ _]
  (let [k (rand-nth key-list)
        v (+ 1 (rand-int 9))]
    {:type :invoke :f :txn :value (independent/tuple k [[:write k v]])}))

(defn db
  [version]
  (reify db/DB
    (setup! [_ test node]
      (info node "stop firewall")
      (c/exec :echo :-e "service tiny-firewall stop"))

    (teardown! [_ test node])))

(defn get-ip
  [n]
  (first (str/split (->> (sh "getent" "ahosts" (str "n" n))
                         (:out)
                         (str/split-lines)
                         (first))
                    #"\s+")))   

(defn get-obj
  [n key]
  (let [ret (sh "sh" "-c" (str "curl -X GET http://" (get-ip n) obj-name key " -v"))]
    (if (= 0 (:exit ret)) 
      (if (str/includes? (:err ret) http-ok) (edn/read-string (:out ret)) -1) -1)))

(defn set-obj
  [n key val]
  (let [ret (sh "sh" "-c" (str "curl -X PUT -d " val " http://" (get-ip n) obj-name key " -v"))]
    (if (= 0 (:exit ret)) (if (str/includes? (:err ret) http-ok) 0 -1) -1)))

(defn init-objs
  [n]
  (doseq [k key-list] (set-obj n k 0)))

; client for object based
(defn client-obj
  [n]
  (reify client/Client
    (setup! [this test node]
      (client-obj (nid node)))

    (invoke! [this test op]
      (timeout 5000 (assoc op :type :info :error :timeout)
               (case (:f op)
                 :txn (let [[key [[f k v]]] (:value op)]
                        (case f
                          :read (let [r (get-obj n key)]
                                  (if (> 0 r) (assoc op :type :fail, :value (independent/tuple key [[:read k v]]))
                                              (assoc op :type :ok,   :value (independent/tuple key [[:read k r]]))))

                          :write (let [r (set-obj n key v)
                                       t (independent/tuple key [[:write k v]])]
                                   (if (> 0 r) (assoc op :type :fail :value t)
                                               (assoc op :type :ok   :value t)))
                          )))))

    (teardown! [_ test])
  ))

; partition node for perf test
(defn split-node
  [n nodes]
  (let [coll (remove (fn [x] (= x n)) nodes)]
    [[n], coll]))

; partition specific node
(defn partition-node
  [n]
  (nemesis/partitioner (comp nemesis/complete-grudge (partial split-node n))))

; generate n normal operations
(defn op-limit
  [n gen]
  (let [life (atom (inc n))]
    (reify gen/Generator
      (op [_ test process]
        (if (= process :nemesis)
          (when (pos? @life)
            (gen/op gen test process))
          (when (pos? (swap! life dec))
            (gen/op gen test process)))))))

; checker for perf test
(defn total-time
  [history]
  (loop [pairs (history/pairs history)
         cnt   0
         total 0]
    (if (nil? pairs)
      {:writes cnt :total-time total}
      (let [[invoke complete] (first pairs)
            pairs (next pairs)]
        (if (= :invoke (:type invoke))
          (recur pairs (inc cnt) (+ total (- (:time complete) (:time invoke))))
          (recur pairs cnt total))))))

(def perf-checker
  (reify checker/Checker
    (check [_ test model history opts]
      (merge {:valid? true} (total-time history)))))

