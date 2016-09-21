(ns jepsen.kafka
  (use kafka-clj.client :reload)
  (:require  [clojure.tools.logging :refer :all]
             [clojure.java.io :as io]
             [clojure.string     :as str]
             [jepsen  [db    :as db]
                      [core  :as jepsen]
                      [client  :as client]
                      [control :as c]
                      [tests :as tests]
                      [checker   :as checker]
                      [model     :as model]
                      [generator :as gen]
                      [store     :as store]
                      [nemesis   :as nemesis]
                      [report    :as report]
                      [util      :as util :refer  [meh
                                                   timeout
                                                   relative-time-nanos]]
                      ]
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
       (c/exec (c/lit "/opt/kafka/bin/kafka-server-start.sh -daemon config/server.properties")))
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

(comment  broker  (->>  (zk/brokers {"zookeeper.connect"  (str  (:host opts) ":2181")})
                    (filter #(=  (:host %)  (:host node)))
                    first))
(comment
                 producer  (producer/producer
                                {"metadata.broker.list"  (str  (:host node) ":9092")
                                 "request.required.acks" "-1" ; all in-sync brokers
                                 "producer.type"         "sync"
                                 "message.send.max_retries" "1"
                                 "connect.timeout.ms"    "1000"
                                 "retry.backoff.ms"       "1000"
                                 "serializer.class"     "kafka.serializer.DefaultEncoder"
                                 "partitioner.class"    "kafka.producer.DefaultPartitioner"}))

(defn dequeue!
    "Given a client and an :invoke :dequeue op, dequeues and acks a job."
    [test op   ]
    (info "dequeue! called" )
  )

(defn client
    "A client for a single compare-and-set register"
    []
    (reify client/Client
        (setup!  [_ test node]
           (info "client called" node)
           (comment work on this!!!
           (let [container  (-> node
                                 (str/split ":")
                                 ( nth 1))]
             (info "container" container)
             ))
           (def connector (create-connector  [{:host node :port 9092}]  {:flush-on-write true}))
           (let [enqueued (atom 0)
                 topic "jepsen"
                 ]
           (info "client producer broker topic does nothing" ))
        )
        (invoke!  [this test op]
          (info "invoke! "  op)
          (case  (:f op)
             :enqueue  (do
                      (assoc op :type :ok))
             :dequeue  (dequeue! test op)
             :drain   (timeout 10000  (assoc op :type :info :value :timeout)
                            (loop  []
                              (let  [op' (->>  (assoc op
                                                      :f    :dequeue
                                                      :time  (relative-time-nanos))
                                              util/log-op
                                              (jepsen/conj-op! test)
                                              (dequeue!
                                                test op ))]
                                ; Log completion
                                (->>  (assoc op' :time  (relative-time-nanos))
                                     util/log-op
                                     (jepsen/conj-op! test))

                                (if  (= :fail  (:type op'))
                                  ; Done
                                  (assoc op :type :ok, :value :exhausted)

                                  ; Keep going.
                                  (recur)))))))
        (teardown!  [_ test])))

; Generators

(defn std-gen
    "Takes a client generator and wraps it in a typical schedule and nemesis
      causing failover."
    [gen]
    (gen/phases
       (->> gen
            (gen/nemesis
               (gen/seq  (cycle  [(gen/sleep 10)
                                   {:type :info :f :start}
                                   (gen/sleep 10)
                                   {:type :info :f :stop}])))
            (gen/time-limit 100))
       ; Recover
       (gen/nemesis  (gen/once  {:type :info :f :stop}))
       ; Wait for resumption of normal ops
       (gen/clients  (gen/time-limit 10 gen))
       ; Drain
       (info "draining " )
       (gen/log "Draining")
       (gen/clients  (gen/each  (gen/once  {:type :invoke
                                            :f    :drain})))))


(defn kafka-test
    [version]
      (assoc  tests/noop-test
             :os debian/os
             :db  (db version)
             :client  (client)
             :model   (model/unordered-queue)
             :generator  (->>  (gen/queue)
                               (gen/delay 1)
                               std-gen)
      ))
