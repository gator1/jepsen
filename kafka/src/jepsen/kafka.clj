(ns jepsen.kafka
  (:require  [clojure.tools.logging :refer :all]
             [clojure.java.io :as io]
             [clojure.string     :as str]
             [franzy.clients.consumer.protocols :refer :all]
             [franzy.clients.producer.protocols :refer :all]
             [franzy.clients.consumer.client :as consumer]
             [franzy.clients.producer.client :as producer]
             [franzy.clients.producer.defaults :as pd]
             [franzy.clients.consumer.defaults :as cd]
             [franzy.serialization.serializers :as serializers]
             [franzy.serialization.nippy.deserializers :as nippy-deserializers]
             [franzy.serialization.deserializers :as deserializers]
             ;[clj-kafka.consumer.zk  :as consumer]
             ;[clj-kafka.producer     :as producer]
             ;[clj-kafka.new.producer :as nproducer]
             ;[clj-kafka.zk           :as czk]
             ;[clj-kafka.core         :as ckafka]
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

(def topic "jepsen3")

(defn create-topic
  []
  ;(Thread/sleep 20)
  (info "creating topic")
  (info (c/exec (c/lit "/opt/kafka/bin/kafka-topics.sh --create --zookeeper localhost:2181 --replication-factor 3 --partitions 1 --topic " topic)))
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
       ;(c/exec (c/lit "/opt/kafka/bin/zookeeper-server-start.sh -daemon config/zookeeper.properties")))
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
    ;(when (= id 1)
    ;   (future  (create-topic)))
  ))

;        kafka "kafka_2.11-0.8.2.2"

;        kafka "kafka_2.10-0.8.2.1"
;2.10-0.8.2.1

(defn install! [node version]
   ; Install specific versions
  (info "install! Kafka begins" node )
  ; https://www.apache.org/dyn/closer.cgi?path=/kafka/0.10.2.0/kafka_2.12-0.10.2.0.tgz
  (let [id  (Integer.  (re-find #"\d+", (name node)))
        kafka "kafka_2.12-0.10.2.0"]
    (c/exec :apt-get :update)
    (debian/install-jdk8!)
    ;(c/exec :apt-get :install :-y :--force-yes "default-jre")
    (c/exec :apt-get :install :-y :--force-yes "wget")
    (c/exec :rm :-rf "/opt/")
    (c/exec :mkdir :-p "/opt/")
    (c/cd "/opt/"
          (c/exec :wget (format "http://apache.claz.org/kafka/0.10.2.0/%s.tgz" kafka))
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
          ;(info "tearing down Zookeeper")
          ;(db/teardown! zk test node)
          ;(info "tearing down Kafka NUKE!!!" node)
          ;(nuke!)
          ;(info "Kafka NUKED!!!" node)
          ))))

(defn test-setup-all []
      (let [db (db "3.4.5+dfsg-2")
            test tests/noop-test]
           (doall (map #(c/on % (db/setup! db test %)) [:n1 :n2 :n3 :n4 :n5]))))

(comment (defn dequeue-messages! [messages]
      (let [message (first messages)
            value (if (nil? message) nil (codec/decode (:value message)))]
           value))

(defn consumer [node]
      (consumer/consumer {"zookeeper.connect"  (str (name node) ":2181")
                          "group.id"            "jepsen.consumer"
                          "auto.offset.reset"   "smallest"
                          "auto.commit.enable"  "true"}))

(defn dequeue-only! [op node queue]
  (try
    (ckafka/with-resource
      [consumer (consumer node)]
      consumer/shutdown
      (timeout 10000 (assoc op :type :fail :value :timeout)
               (let [value (dequeue-messages! (consumer/messages consumer queue))]
                    (if (nil? value)
                      (assoc op :type :fail :value :exhausted)
                      (assoc op :type :ok :value value))))
      )
  (catch Exception e
    ; Exception is probably timeout variant
    (assoc op :type :fail :value :timeout)))))

(defn consumer [node]
  (let [cc {:bootstrap.servers       [(str (name node) ":9092")]
            :group.id                "jepsen.client"
            :auto.offset.reset       :smallest
            ;;here we turn on committing offsets to Kafka itself, every 1000 ms
            :enable.auto.commit      true
            :auto.commit.interval.ms 1000}
        key-deserializer (deserializers/keyword-deserializer)
        value-deserializer (nippy-deserializers/nippy-deserializer)
        options (cd/make-default-consumer-options {:poll-timeout-ms 10000})]
   (consumer/make-consumer cc key-deserializer value-deserializer options)))

(defn dequeue-only! [op node queue]
  (try
    (with-open [c (consumer node)]
      (subscribe-to-partitions! c [queue])
      (println "Partitions subscribed to:" (partition-subscriptions c))
      (let [cr (poll! c)
            message (first cr)
            value (:value message)]
          (clear-subscriptions! c)
          (if (nil? message)
            (assoc op :type :fail :value :exhausted)
            (assoc op :type :ok :value value)))
      )
    (catch Exception e
      ; Exception is probably timeout variant
      (assoc op :type :fail :value :timeout))))

(comment (defn drain!
      "Returns a sequence of all elements available within dt millis."
      [node queue]
      (ckafka/with-resource
        [consumer (consumer node)]
        consumer/shutdown
        (let [done (atom [])]
             (loop [coll (consumer/messages consumer queue)]
                   (if (-> done
                           (swap! conj (first coll))
                           future
                           (deref 10000 ::timeout)
                           (= ::timeout))
                     @done
                     (recur (rest coll))))))))

(comment (defn drain! [node queue]
      (ckafka/with-resource
        [consumer (consumer node)]
        (doall (consumer/messages consumer queue)))))

(defn dequeue!
  "Given a channel and an operation, dequeues a value and returns the
  corresponding operation."
  [client queue op]
  (timeout 60000
           (assoc op :type :fail :value :timeout)
           (dequeue-only! op (:node client) queue)))

(defn enqueue-only! [node queue value]
      (let [;;notice vs the string producer example, we're going with much simpler defaults to demonstrate you don't need all that ceremony
            pc {:bootstrap.servers [(str (name node) ":9092")]
                :acks              "all"
                :retry.backoff.ms   1000
                ;;Best Practice: Set a client-id for better auditing, easier admin operations, etc.
                :client.id         "jepsen.client"}
            ;;now we are using a keyword serializer for keys, so we can use Clojure keywords as keys if we want
            key-serializer (serializers/keyword-serializer)
            ;;now we are using the EDN serializer so we should be able to send full Clojure objects that are EDN-serializable
            value-serializer (serializers/edn-serializer)
            ;;we're being lazy, but we could set some options instead of using the defaults, or not even pass options at all
            options (pd/make-default-producer-options)
            partition 0]
           ;;this producer is created without passing options, if you have no need....
           (with-open [p (producer/make-producer pc key-serializer value-serializer)]
                            ;;we can also send without options and also pass everything as 1 map if we choose - this time we send clj values
                            (send-sync! p queue 0 nil value options))))

(comment (defn producer [node]
      (producer/producer {"metadata.broker.list" (str (name node) ":9092")
                          "request.required.acks" "-1" ; all in-sync brokers
                          "producer.type"         "sync"
                          ;"message.send.max_retries" "1"
                          ;"connect.timeout.ms"    "1000"
                          "retry.backoff.ms"       "1000"
                          "serializer.class" "kafka.serializer.DefaultEncoder"
                          "partitioner.class" "kafka.producer.DefaultPartitioner"})))

(comment (defn enqueue-only! [node queue value]
      (producer/send-message (producer node) (producer/message queue (codec/encode value)))))

(comment (defn brokers [node]
      (czk/brokers {"zookeeper.connect" (str (name node) ":2181")})))

(defn enqueue! [client queue op]
  (try
    (timeout 10000  (assoc op :type :info, :value :timeout)
             (enqueue-only! (:node client) queue (:value op))
             (assoc op :type :ok))
    (catch Exception e
      (assoc op :type :info, :value :timeout))))

(defrecord Client [client queue]
  client/Client
  (setup!  [this test node]
           (info "setup! client called" node)
           (let [;brokers (->> (brokers node)
                 ;            (filter #(= (:host %) (name node))))
                 ;            first)
                 ;a0 (info "brokers:" brokers)
                 ;a1 (info "starting client producer." node)
                 ;producer (producer node)
                 ;a2 (info "starting client consumer" node)
                 ;consumer (consumer node)
                 ;messages (consumer/messages consumer queue)
                 client {:producer nil :consumer nil :node node :messages nil}]
            (info "done client setup..." node)
            (assoc this :client client)))

  (teardown!  [_ test]
    ;(consumer/shutdown (:consumer client))
    )

  (invoke!  [this test op]
     (case  (:f op)
         :enqueue (enqueue! client queue op)
         :dequeue  (dequeue! client queue op)
         :drain  (timeout 60000 (assoc op :type :info :value :timeout)
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

(defn client [] (Client. nil topic))

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

(def gen1
  (->>  (gen/queue)
        (gen/delay 1)
        std-gen))

(def gen2
  (gen/phases
    (->> (gen/queue)
         (gen/delay 1/10)
         (gen/nemesis
           (gen/seq
             (cycle [(gen/sleep 60)
                     {:type :info :f :start}
                     (gen/sleep 60)
                     {:type :info :f :stop}])))
         (gen/time-limit 360))
    (gen/nemesis
      (gen/once {:type :info, :f :stop}))
    (gen/log "waiting for recovery")
    (gen/sleep 60)
    (gen/clients
      (gen/each
        (gen/once {:type :invoke
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
             :generator  gen2
      ))
