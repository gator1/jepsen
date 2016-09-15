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
  (c/exec (c/lit "/opt/kafka/bin/kafka-topics.sh --create --zookeeper localhost:2181 --replication-factor 1 --partitions 1 --topic jepsen "))
  (info "creating topic done")
)

(defn start!
  [id]
  ;(zk/start)
  (Thread/sleep (* 3 id))
  (c/su
    (info "start!  begins" id)
    (c/cd "/opt/kafka"
       (c/exec (c/lit "/opt/kafka/bin/kafka-server-start.sh config/server.properties")))
    (info "start!  ends" id)
  )
)

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
    (c/exec :rm :-rf "/opt/kafka")
    (c/exec :rm :-rf "/tmp/kafka-logs")))

(comment
  (defn set-broker-id!  [filename id]
    (info "set-broker-id!: filename: " filename)
    (let [transform #(if  (clojure.string/starts-with? %1 "broker.id")  (str "broker.id=" id) %1)
          joined (clojure.string/join "\n" (map transform (-> filename slurp clojure.string/split-lines)))]
      (info "joined: " joined)
      (spit filename joined))))

(defn set-broker-id! [filename id]
   (c/exec  (c/lit  (format "sed -i.bak '/^broker\\.id/s/^.*$/broker.id=%s/' %s"     id filename))))

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
    ; (info "deploy calls set-broker-id!" filename node id )
    (set-broker-id! filename id))
    ; (info "deplpoy set-broker-id done calls start!!" id )
    (info "deplpoy start! begins" id )
    (start! id)
    (info "deplpoy start! ends!" id )
    ; Create topic asynchronously
    (when (= id 1)
       (future  (create-topic)))
)

(defn install! [node version]
   ; Install specific versions
  (info "install! Kafka begins" node )
  (let [id  (Integer.  (re-find #"\d+", (name node)))
        kafka "kafka_2.10-0.10.0.1"]
    (c/exec :apt-get :update)
    (c/exec :apt-get :install :-y :--force-yes "default-jre")
    (c/exec :apt-get :install :-y :--force-yes "wget")
    (c/exec :rm :-rf "/opt/")
    (c/exec :mkdir :-p "/opt/")
    (c/cd "/opt/"
          (c/exec :wget (format "http://apache.claz.org/kafka/0.10.0.1/%s.tgz" kafka))
          (c/exec :gzip :-d (format "%s.tgz" kafka))
          (c/exec :tar :xf (format "%s.tar" kafka))
          (c/exec :mv kafka "kafka")
          (c/exec :rm (format "%s.tar" kafka)))
    (info "install! Kafka before call deploy" node )
    (deploy id node version))
    (info "install! Kafka ends call deploy" node )
  (info "install! Kafka ends" node )
)

(defn db
    "Kafka DB for a particular version."
    [version]
    (let [zk (zk/db "3.4.5+dfsg-2")]
      (reify db/DB
        (setup!  [_ test node]
          (info "setup! zk " node)
          (db/setup! zk test node)
          (info "setup! kafka" node)
          (install! node version)
          (info "setup! kafka done"  node)
        )
        (teardown!  [_ test node]
          (info "tearing down Kafka NUKE!!!" node)
          (nuke!)
          (info "Kafka NUKED!!!" node)
          ))))

(defn kafka-test
    [version]
      (assoc  tests/noop-test
             :os debian/os
             :db  (db version)))
