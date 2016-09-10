(ns jepsen.kafka
  (:require  [clojure.tools.logging :refer :all]
             [clojure.java.io :as io]
             [clojure.string     :as str]
             [jepsen  [db    :as db]
                      [control :as c]
                      [tests :as tests] ]
             [jepsen.control :as c :refer  [|]]
             [jepsen.control.util :as cu]
             [jepsen.zookeeper :as zk]
             [jepsen.os.debian :as debian]))

(defn create-topic
  []
  (Thread/sleep 20)
  (info "creating topic")
  (c/exec (c/lit "/opt/kafka/bin/kafka-create-topic.sh --partition 5 --replica 5 --topic jepsen --zookeeper localhost:2181 ")))

(defn start!
  [id]
  ;(zk/start)
  (Thread/sleep (* 3 id))
  (c/su
     (c/cd "/opt/kafka"
     (c/exec (c/lit "/opt/kafka/bin/kafka-server-start.sh config/server.properties")))))

(defn stop!
  []
  (c/su
     ;(c/exec :ps :aux "|" :grep :kafka "|" :grep :-v :grep "|" :awk "{print $2 }" "|" :xargs :kill :-s :kill)
     (c/exec (c/lit  "ps aux | grep kafka | grep -v grep | awk '{ print $2 }' | xargs kill -s kill"))))

(defn restart!
  [id]
  (stop! )
  (start! id))

(defn status!
  []
  (c/exec (c/lit "/opt/kafka/bin/kafka-list-topic.sh --zookeeper localhost:2181")))

(defn nuke!
  []
  ;(zk/nuke)
  (stop! )
  (c/su
    (stop!)
    ;(c/exec :rm :-rf "/opt/kafka")
    (c/exec :rm :-rf "/tmp/kafka-logs")))

(defn set-broker-id!  [filename id]
  (let [transform #(if  (clojure.string/starts-with? %1 "broker.id")  (str "broker.id=" id) %1)
        joined (clojure.string/join "\n" (map transform (-> filename slurp clojure.string/split-lines)))]
    (info "joined: " joined)
    (spit filename joined)))

(comment
  (defn set-broker-id!  [filename id]
    (c/exec :rm :-rf filename)
    (c/exec :echo
            (-> "server.properties"
                io/resource
                slurp
                (str/replace "%%ID%%"  (str id))) :> "/opt/kafka/config/server.properties")))

(defn deploy [id node version]
  (let [filename "/opt/kafka/config/server.properties"]
    (info "set-broker-id!" filename node id )
    (set-broker-id! filename id))
  ; Create topic asynchronously
  (future  (create-topic))
  (start! id))

(defn install! [node version]
   ; Install specific versions
  (info "install! Kafka" node )
  (let [id  (Integer.  (re-find #"\d+", (name node)))
        kafka "kafka_2.10-0.10.0.1"]
     (info node id)
     (c/exec :apt-get :update)
     (c/exec :apt-get :install :-y :--force-yes "default-jre")
     (c/exec :apt-get :install :-y :--force-yes "wget")
     (c/exec :rm :-rf "/opt/kafka")
     (c/exec :mkdir :-p "/opt/kafka")
     (c/cd "/opt/"
           (c/exec :wget (format "http://apache.claz.org/kafka/0.10.0.1/%s.tgz" kafka))
           (c/exec :gzip :-d (format "%s.tgz" kafka))
           (c/exec :tar :xf (format "%s.tar" kafka))
           (c/exec :mv kafka "kafka"))
     (deploy id node version)))

(defn db
    "Kafka DB for a particular version."
    [version]
    (let [zk (zk/db "3.4.5+dfsg-2")]
      (reify db/DB
        (setup!  [_ test node]
          (info node "installing zk" version)
          (db/setup! zk test node)
          (info node "installing kafka" version)
          (install!  node version))
        (teardown!  [_ test node]
          (info node "tearing down Kafka NUKE!!!")
          (nuke!)))))

(defn kafka-test
    [version]
      (assoc  tests/noop-test
             :os debian/os
             :db  (db version)))
