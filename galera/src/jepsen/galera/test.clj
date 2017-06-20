(ns jepsen.galera.test
    (:gen-class)
    (:require
      [clojure.tools.logging :refer :all]
      [clojure.core.reducers :as r]
      ;[clojure.java.io :as io]
      ;[clojure.string :as str]
      ;[clojure.pprint :refer [pprint]]
      [knossos.op :as op]
      [jepsen.os.debian :as debian]
      [jepsen.checker.timeline :as timeline]
      [clojure.java.jdbc :as j]
      [jepsen [client :as client]
       [core :as jepsen]
       [cli        :as cli]
       [db :as db]
       [tests :as tests]
       [control :as c :refer [|]]
       [checker :as checker]
       [nemesis :as nemesis]
       [generator :as gen]
       [util :refer [timeout meh]]
       [galera :as galera]
      ;[jepsen.control.util :as cu]
      ;[jepsen.control.net :as cn]
       ]
      ))

(defn conn-spec
      "jdbc connection spec for a node."
      [node]
      {:classname   "org.mariadb.jdbc.Driver"
       :subprotocol "mariadb"
       :subname     (str "//" (name node) ":3306/jepsen")
       :user        "jepsen"
       :password    "jepsen"})

(def rollback-msg
  "mariadb drivers have a few exception classes that use this message"
  "Deadlock found when trying to get lock; try restarting transaction")

(defmacro capture-txn-abort
          "Converts aborted transactions to an ::abort keyword"
          [& body]
          `(try ~@body
                (catch java.sql.SQLTransactionRollbackException e#
                  (if (= (.getMessage e#) rollback-msg)
                    ::abort
                    (throw e#)))
                (catch java.sql.BatchUpdateException e#
                  (if (= (.getMessage e#) rollback-msg)
                    ::abort
                    (throw e#)))))

(defmacro with-txn-retries
          "Retries body on rollbacks."
          [& body]
          `(loop []
                 (let [res# (capture-txn-abort ~@body)]
                      (if (= ::abort res#)
                        (recur)
                        res#))))

(defmacro with-txn-aborts
          "Aborts body on rollbacks."
          [op & body]
          `(let [res# (capture-txn-abort ~@body)]
                (if (= ::abort res#)
                  (assoc ~op :type :fail)
                  res#)))

(defmacro with-error-handling
          "Common error handling for Galera errors"
          [op & body]
          `(try ~@body
                (catch java.sql.SQLNonTransientConnectionException e#
                  (condp = (.getMessage e#)
                         "WSREP has not yet prepared node for application use"
                         (assoc ~op :type :fail, :value (.getMessage e#))

                         (throw e#)))))

(defmacro with-txn
          "Executes body in a transaction, with a timeout, automatically retrying
          conflicts and handling common errors."
          [op [c node] & body]
          `(timeout 5000 (assoc ~op :type :info, :value :timed-out)
                    (with-error-handling ~op
                                         (with-txn-retries
                                           (j/with-db-transaction [~c (conn-spec ~node)
                                                                   :isolation :serializable]
                                                                  ~@body)))))

(defn basic-test
      [opts]
      (merge tests/noop-test
             {:name (str "galera " (:name opts))
              :os   debian/os
              :db   (galera/db (:version opts))
              :nemesis (nemesis/partition-random-halves)}
             (dissoc opts :name :version)))

(defn with-nemesis
      "Wraps a client generator in a nemesis that induces failures and eventually
      stops."
      [client]
      (gen/phases
        (gen/phases
          (->> client
               (gen/nemesis
                 (gen/seq (cycle [(gen/sleep 0)
                                  {:type :info, :f :start}
                                  (gen/sleep 10)
                                  {:type :info, :f :stop}])))
               (gen/time-limit 30))
          (gen/nemesis (gen/once {:type :info, :f :stop}))
          (gen/sleep 5))))

(defn set-client
      [node]
      (reify client/Client
             (setup! [this test node]
                     (j/with-db-connection [c (conn-spec node)]
                                           (j/execute! c ["create table if not exists jepsen
                       (id     int not null auto_increment primary key,
                       value  bigint not null)"]))

                     (set-client node))

             (invoke! [this test op]
                      (with-txn op [c node]
                                (try
                                  (case (:f op)
                                        :add  (do (j/insert! c :jepsen (select-keys op [:value]))
                                                  (assoc op :type :ok))
                                        :read (->> (j/query c ["select * from jepsen"])
                                                   (mapv :value)
                                                   (into (sorted-set))
                                                   (assoc op :type :ok, :value))))))

             (teardown! [_ test])))

(defn sets-test
      [version]
      (basic-test
        {:name "set"
         :version version
         :client (set-client nil)
         :generator (gen/phases
                      (->> (range)
                           (map (partial array-map
                                         :type :invoke
                                         :f :add
                                         :value))
                           gen/seq
                           (gen/delay 1/10)
                           with-nemesis)
                      (->> {:type :invoke, :f :read, :value nil}
                           gen/once
                           gen/clients))
         :checker (checker/compose
                    {:perf (checker/perf)
                     :set  checker/set})}))

(defrecord BankClient [node n starting-balance]
           client/Client
           (setup! [this test node]
                   (j/with-db-connection [c (conn-spec node)]
                                         ; Create table
                                         (j/execute! c ["create table if not exists accounts
                     (id      int not null primary key,
                     balance bigint not null)"])
                                         ; Create initial accts
                                         (dotimes [i n]
                                                  (try
                                                    (with-txn-retries
                                                      (j/insert! c :accounts {:id i, :balance starting-balance}))
                                                    (catch java.sql.SQLIntegrityConstraintViolationException e nil))))

                   (assoc this :node node))

           (invoke! [this test op]
                    (with-txn op [c node]
                              (try
                                (case (:f op)
                                      :read (->> (j/query c ["select * from accounts"])
                                                 (mapv :balance)
                                                 (assoc op :type :ok, :value))

                                      :transfer
                                      (let [{:keys [from to amount]} (:value op)
                                            b1 (-> c
                                                   (j/query ["select * from accounts where id = ?" from]
                                                            :row-fn :balance)
                                                   first
                                                   (- amount))
                                            b2 (-> c
                                                   (j/query ["select * from accounts where id = ?" to]
                                                            :row-fn :balance)
                                                   first
                                                   (+ amount))]
                                           (cond (neg? b1)
                                                 (assoc op :type :fail, :value [:negative from b1])

                                                 (neg? b2)
                                                 (assoc op :type :fail, :value [:negative to b2])

                                                 true
                                                 (do (j/update! c :accounts {:balance b1} ["id = ?" from])
                                                     (j/update! c :accounts {:balance b2} ["id = ?" to])
                                                     (assoc op :type :ok))))))))

           (teardown! [_ test]))

(defn bank-client
      "Simulates bank account transfers between n accounts, each starting with
      starting-balance."
      [n starting-balance]
      (BankClient. nil n starting-balance))

(defn bank-read
      "Reads the current state of all accounts without any synchronization."
      [_ _]
      {:type :invoke, :f :read})

(defn bank-transfer
      "Transfers a random amount between two randomly selected accounts."
      [test process]
      (let [n (-> test :client :n)]
           {:type  :invoke
            :f     :transfer
            :value {:from   (rand-int n)
                    :to     (rand-int n)
                    :amount (rand-int 5)}}))

(def bank-diff-transfer
  "Like transfer, but only transfers between *different* accounts."
  (gen/filter (fn [op] (not= (-> op :value :from)
                             (-> op :value :to)))
              bank-transfer))

(defn bank-checker
      "Balances must all be non-negative and sum to the model's total."
      []
      (reify checker/Checker
             (check [this test model history opts]
                    (let [bad-reads (->> history
                                         (r/filter op/ok?)
                                         (r/filter #(= :read (:f %)))
                                         (r/map (fn [op]
                                                    (let [balances (:value op)]
                                                         (cond (not= (:n model) (count balances))
                                                               {:type :wrong-n
                                                                :expected (:n model)
                                                                :found    (count balances)
                                                                :op       op}

                                                               (not= (:total model)
                                                                     (reduce + balances))
                                                               {:type :wrong-total
                                                                :expected (:total model)
                                                                :found    (reduce + balances)
                                                                :op       op}))))
                                         (r/filter identity)
                                         (into []))]
                         {:valid? (empty? bad-reads)
                          :bad-reads bad-reads}))))

(def version "7.4.7")
(def n 4)
(def initial-balance 1000)

(defn bank-test
      [opts]
      (basic-test
        {:name "bank"
         :concurrency 20
         :version version
         :model  {:n n :total (* n initial-balance)}
         :client (bank-client n initial-balance)
         :generator (gen/phases
                      (->> (gen/mix [bank-read bank-diff-transfer])
                           (gen/clients)
                           (gen/stagger 1/10)
                           (gen/time-limit 100))
                      (gen/log "waiting for quiescence")
                      (gen/sleep 30)
                      (gen/clients (gen/once bank-read)))
         :nemesis nemesis/noop
         :checker (checker/compose
                    {:timeline     (timeline/html)
                     :perf (checker/perf)
                     :bank (bank-checker)})}))



(defrecord Client [node n]
           client/Client
           (setup! [this test node]
                   (j/with-db-connection [c (conn-spec node)]
                                         ; Create table
                                         (j/execute! c ["create table if not exists dirty
                     (id      int not null primary key,
                      x       bigint not null)"])
                                         ; Create rows
                                         (dotimes [i n]
                                                  (try
                                                    (with-txn-retries
                                                      (Thread/sleep (rand-int 10))
                                                      (j/insert! c :dirty {:id i, :x -1}))
                                                    (catch java.sql.SQLIntegrityConstraintViolationException e nil))))

                   (assoc this :node node))

           (invoke! [this test op]
                    (timeout 5000 (assoc ~op :type :info, :value :timed-out)
                             (with-error-handling op
                                                         (with-txn-aborts op
                                                                                 (j/with-db-transaction [c (conn-spec node)
                                                                                                         :isolation :serializable]
                                                                                                        (try
                                                                                                          (case (:f op)
                                                                                                                :read (->> (j/query c ["select * from dirty"])
                                                                                                                           (mapv :x)
                                                                                                                           (assoc op :type :ok, :value))

                                                                                                                :write (let [x (:value op)
                                                                                                                             order (shuffle (range n))]
                                                                                                                            (doseq [i order]
                                                                                                                                   (j/query c ["select * from dirty where id = ?" i]))
                                                                                                                            (doseq [i order]
                                                                                                                                   (j/update! c :dirty {:x x} ["id = ?" i]))
                                                                                                                            (assoc op :type :ok)))))))))

           (teardown! [_ test]))

(defn client
      [n]
      (Client. nil n))

(defn checker
      "We're looking for a failed transaction whose value became visible to some
      read."
      []
      (reify checker/Checker
             (check [this test model history opts]
                    (let [failed-writes (->> history
                                             (r/filter op/fail?)
                                             (r/filter #(= :write (:f %)))
                                             (r/map :value)
                                             (into (hash-set)))
                          reads (->> history
                                     (r/filter op/ok?)
                                     (r/filter #(= :read (:f %)))
                                     (r/map :value))
                          inconsistent-reads (->> reads
                                                  (r/filter (partial apply not=))
                                                  (into []))
                          filthy-reads (->> reads
                                            (r/filter (partial some failed-writes))
                                            (into []))]
                         {:valid? (empty? filthy-reads)
                          :inconsistent-reads inconsistent-reads
                          :dirty-reads filthy-reads}))))

(def reads {:type :invoke, :f :read, :value nil})

(def writes (->> (range)
                 (map (partial array-map
                               :type :invoke,
                               :f :write,
                               :value))
                 gen/seq))

(defn dirty-reads-test
      [opts]
      (basic-test
        {:name "dirty reads"
         :concurrency 50
         :version version
         :client (client n)
         :generator (->> (gen/mix [reads writes])
                         gen/clients
                         (gen/time-limit 1000))
         :nemesis nemesis/noop
         :checker (checker/compose
                    {:timeline     (timeline/html)
                     :perf (checker/perf)
                     :dirty-reads (checker)})}))

(defn -main
      "Handles command line arguments. Can either run a test, or a web server for
      browsing results."
      [& args]
      (cli/run! (merge (cli/single-test-cmd {:test-fn dirty-reads-test})
                       (cli/serve-cmd))
                args))






