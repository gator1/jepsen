(ns jepsen.kafka
  (:require  [clojure.tools.logging :refer :all]
             [clojure.java.io :as io]
             [clojure.string     :as str]
             [clj-kafka.consumer.zk  :as consumer]
             [clj-kafka.producer     :as producer]
             [clj-kafka.new.producer :as nproducer]
             [clj-kafka.zk           :as czk]
             [clj-kafka.core         :as ckafka]
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
                      [codec     :as codec]
                      [util      :as util :refer  [meh
                                                   timeout
                                                   relative-time-nanos]]
                      ]
             [jepsen.control :as c :refer  [|]]
             [jepsen.control.util :as cu]
             [jepsen.zookeeper :as zk]
             [jepsen.os.debian :as debian])
  )

(defn create-topic
  []
  (Thread/sleep 20)
  (info "creating topic")
  (info (c/exec (c/lit "/opt/kafka/bin/kafka-topics.sh --create --zookeeper localhost:2181 --replication-factor 1 --partitions 1 --topic jepsen ")))
  (info (c/exec (c/lit "/opt/kafka/bin/kafka-topics.sh --list --zookeeper localhost:2181")))
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
  (stop! )
  (c/su
    (stop!)
    (c/exec :rm :-rf "/opt/kafka")
    (c/exec :rm :-rf "/tmp/kafka-logs")))

(defn set-broker-id! [filename id]
   (c/exec  (c/lit  (format "sed -i.bak '/^broker\\.id/s/^.*$/broker.id=%s/' %s"     id filename))))

(defn deploy [id node version]
  (let [filename "/opt/kafka/config/server.properties"]
    ; (info "deploy calls set-broker-id!" filename node id )
    (set-broker-id! filename id)
    ; set advertised host name, otherwise it is canonical name
    ;(info "setting advertised host name to" (name node))
    ;(c/exec :echo (str "advertised.host.name=" (name node)) :> filename)
    ; (info "deplpoy set-broker-id done calls start!!" id )
    (info "deplpoy start! begins" id )
    (start! id)
    (info "deplpoy start! ends!" id )
    ; Create topic asynchronously
    (when (= id 1)
       (future  (create-topic)))
  ))

;        kafka "kafka_2.11-0.8.2.2"

;        kafka "kafka_2.10-0.8.2.1"
;2.10-0.8.2.1

(defn install! [node version]
   ; Install specific versions
  (info "install! Kafka begins" node )
  (let [id  (Integer.  (re-find #"\d+", (name node)))
        kafka "kafka_2.10-0.8.2.1"]
    (c/exec :apt-get :update)
    (c/exec :apt-get :install :-y :--force-yes "default-jre")
    (c/exec :apt-get :install :-y :--force-yes "wget")
    (c/exec :rm :-rf "/opt/")
    (c/exec :mkdir :-p "/opt/")
    (c/cd "/opt/"
          (c/exec :wget (format "http://apache.claz.org/kafka/0.8.2.1/%s.tgz" kafka))
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
    (let [zk (zk/db "3.4.5+dfsg-2+deb8u1")]
      (reify db/DB
        (setup!  [_ test node]
          (info "setup! zk " node)
          (db/setup! zk test node)
          (info "setup! kafka" node)
          (install! node version)
          (info "setup! kafka done"  node)
        )
        (teardown!  [_ test node]
          (info "tearing down Zookeeper")
          (db/teardown! zk test node)
          (info "tearing down Kafka NUKE!!!" node)
          (nuke!)
          (info "Kafka NUKED!!!" node)
          ))))

(defn test-setup-all []
      (let [db (db "3.4.5+dfsg-2")]
           (doall (map #(c/on % (db/setup! db "jepsen" %)) [:n1 :n2 :n3 :n4 :n5]))))

(defn dequeue-messages! [messages]
      (let [message (first messages)
            value (if (nil? message) nil (codec/decode (:value message)))]
           value))

(defn dequeue-only! [node queue]
      (ckafka/with-resource
        [consumer (consumer/consumer
                    {"zookeeper.connect"   (str (name node) ":2181")
                     "group.id"            "jepsen.consumer"
                     "auto.offset.reset"   "smallest"
                     "auto.commit.enable"  "true"})]
        consumer/shutdown
        (dequeue-messages! (consumer/messages consumer queue))))

(defn dequeue!
  "Given a channel and an operation, dequeues a value and returns the
  corresponding operation."
  [client queue op]
  (timeout 5000 (assoc op :type :fail :value :timeout)
           (let [value (dequeue-messages! (:messages client))]
                (if (nil? value)
                  (assoc op :type :fail :value :exhausted)
                  (assoc op :type :ok :value value)))))

(defn producer [node]
      (producer/producer {"metadata.broker.list" (str (name node) ":9092")
                          "request.required.acks" "-1" ; all in-sync brokers
                          "producer.type"         "sync"
                          ;"message.send.max_retries" "1"
                          ;"connect.timeout.ms"    "1000"
                          "retry.backoff.ms"       "1000"
                          "serializer.class" "kafka.serializer.DefaultEncoder"
                          "partitioner.class" "kafka.producer.DefaultPartitioner"}))

(defn enqueue-only! [producer queue value]
      (producer/send-message producer  (producer/message queue (codec/encode value))))

(defrecord Client [client queue]
  client/Client
  (setup!  [this test node]
           (info "setup! client called" node)
           (let [brokers (->> (czk/brokers {"zookeeper.connect" (str (name node) ":2181")})
                             (filter #(= (:host %) (name node))))
                 ;            first)
                 a0 (info "brokers:" brokers)
                 a1 (info "starting client producer." node)
                 producer (producer node)
                 a2 (info "starting client consumer" node)
                 consumer (consumer/consumer {"zookeeper.connect"  (str (name node) ":2181")
                                                                 "group.id"            "jepsen.consumer"
                                                                 "auto.offset.reset"   "smallest"
                                                                 "auto.commit.enable"  "true"})
                 messages (consumer/messages consumer queue)
                 client {:producer producer :consumer nil :node node :messages messages}]
            (info "done client setup..." node)
            (assoc this :client client)))

  (teardown!  [_ test]
    (consumer/shutdown (:consumer client))
    )

  (invoke!  [this test op]
     (case  (:f op)
         :enqueue (timeout 10000  (assoc op :type :info, :error :timeout)
                    (enqueue-only! (:producer client) queue (:value op))
                    (assoc op :type :ok))
         :dequeue  (dequeue! client queue op)
         :drain  (timeout 10000 (assoc op :type :info :value :timeout)
                                           (loop []
                                             (let [op' (->> (assoc op
                                                                   :f    :dequeue
                                                                   :time (relative-time-nanos))
                                                            util/log-op
                                                            (jepsen/conj-op! test)
                                                            (dequeue! client queue))]
                                               ; Log completion
                                               (->> (assoc op' :time (relative-time-nanos))
                                                    util/log-op
                                                    (jepsen/conj-op! test))

                                               (if (= :fail (:type op'))
                                                 ; Done
                                                 (assoc op :type :ok, :value :exhausted)

                                                 ; Keep going.
                                                 (recur)))))
         ))
  )

(defn client [] (Client. nil "jepsen"))

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
             ;:nemesis (nemesis/partition-random-halves)
             :checker    (checker/compose
                            {:queue       checker/queue
                            :total-queue checker/total-queue})
             :generator  (->>  (gen/queue)
                               (gen/delay 1)
                               std-gen)
      ))
