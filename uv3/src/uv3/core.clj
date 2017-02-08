(ns uv3.core
  (:require [clojure.tools.logging :refer :all]
            [jepsen [core      :as jepsen]
                    [client    :as client]
                    [nemesis   :as nemesis]
                    [generator :as gen]
                    [checker   :as checker]
                    [control   :as c]
                    [independent :as independent]
                    [tests     :as tests]
                    [util      :refer [timeout]]]
            [jepsen.control.net :as control.net]
            [knossos.model :refer [cas-register]]
            [clojure.edn :as edn])
  (:use     [clojure.java.shell :only [sh]]))

; definitions for multi-register
(def key-list [1 2 3 4 5 6 7 8])
(def ^:private pos-list [0 255 256 257 1023 1024 1025 4095])
(def ^:private link-list ["sdb" "sdf" "sdj" "sdn"])
(def ^:private home-dir "/root/jepsen/uv3/")

(defn nid
  [n]
  (->> (str n)
       (re-find #"\d+")
       (Integer. )))

; operations for multi-register
(defn mr [_ _]
  (let [k (rand-nth key-list)]
    {:type :invoke :f :txn :value (independent/tuple k [[:read k nil]])}))

(defn mw [_ _]
  (let [k (rand-nth key-list)
        v (+ 1 (rand-int 9))]
    {:type :invoke :f :txn :value (independent/tuple k [[:write k v]])}))

; get data on specific position from disk
(defn get-multi-data
  [n key proc]
  (let [tmp (str home-dir "temp" key "-" proc)
        dev (str "/dev/" (nth link-list (dec n)))
        pos (nth pos-list (dec key))
        ret (sh "sh" "-c" (str "dd if=" dev " skip=" pos " of=" tmp " count=1 iflag=direct"))]
    (if (= 0 (:exit ret)) (edn/read-string (:out (sh "sh" "-c" (str "cat " tmp)))) -1)))

(defn set-multi-data
  [n key proc val]
  (let [tmp (str home-dir "temp" key "-" proc)
        dev (str "/dev/" (nth link-list (dec n)))
        pos (nth pos-list (dec key))
        ret (->> (str "dd if=" tmp " of=" dev " seek=" pos " count=1 oflag=direct conv=notrunc")
                 (str "cat <(echo " val ") <(dd if=/dev/zero bs=1 count=510) > " tmp " ;")
                 (sh "bash" "-c"))]
    (if (= 0 (:exit ret)) 0 -1)))

(defn init-multi-data
  [n count]
  (let [dev (str "/dev/" (nth link-list (dec n)))]
    (sh "sh" "-c" (str "dd if=/dev/zero of=" dev " count=" count))
    (doseq [k key-list] (set-multi-data n k 0 0))))

(defn get-multi-all
  [n]
  (doseq [k key-list]
    (let [v (get-multi-data n k 0)]
      (println "key: " k, "value: " v))))

; client for multi-register
(defn client-multi
  [n]
  (reify client/Client
    (setup! [this test node]
      (client-multi (nid node)))

    (invoke! [this test op]
      (timeout 5000 (assoc op :type :info :error :timeout)
               (case (:f op)
                 :txn (let [[key [[f k v]]] (:value op)
                            proc (:process op)]
                        (case f
                          :read (let [r (get-multi-data n key proc)]
                                  (if (> 0 r) (assoc op :type :fail, :value (independent/tuple key [[:read k v]]))
                                              (assoc op :type :ok,   :value (independent/tuple key [[:read k r]]))))

                          :write (let [r (set-multi-data n key proc v)
                                       t (independent/tuple key [[:write k v]])]
                                   (if (> 0 r) (assoc op :type :fail :value t)
                                               (assoc op :type :ok   :value t)))
                          )))))

    (teardown! [_ test])
  ))

; force pcie 0 link down
; pcie id should be found before running the test
(defn linkdown
  [node]
  (c/on :n1 (c/exec :echo :-e "pcie forcentlinkdown 0 1\npcie forcentlinkdown 1 1\nexit" :| :mml))
  )

(defn linkup
  [node]
  (c/on :n1 (c/exec :echo :-e "pcie forcentlinkdown 0 0\npcie forcentlinkdown 1 0\nexit" :| :mml))
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

