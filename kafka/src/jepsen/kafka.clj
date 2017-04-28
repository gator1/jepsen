(ns jepsen.kafka
  (:gen-class)
  (:require  [clojure.tools.logging :refer :all]
             [clojure.java.io :as io]
             [clojure.string     :as str]
             [gregor.core :as gregor]
             [jepsen  [db    :as db]
                      [cli        :as cli]
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
                      [util      :as util :refer  [meh log-op
                                                   timeout
                                                   relative-time-nanos]]
                      ]
             [jepsen.control :as c :refer  [|]]
             [jepsen.checker.timeline :as timeline]
             ;[knossos.op :as op]
             [jepsen.control.util :as cu]
             [jepsen.os.ubuntu :as ubuntu])
  )

(def topic "jepsen")

(defn topic-status [node]
  (c/on node (info node "kafka-topics.sh --describe:" (c/exec (c/lit (str "/opt/kafka/bin/kafka-topics.sh --describe --zookeeper localhost:2181 --topic " topic))))))

(defn create-topic
  [node]
  (Thread/sleep 20)
  (info "creating topic")
  ; Delete it if it exists
  (try
    (info "kafka-topics.sh --delete2:" (c/exec (c/lit (str "/opt/kafka/bin/kafka-topics.sh --zookeeper localhost:2181 --delete --topic " topic))))
    (Thread/sleep 20)
    (catch Exception e
      (info "Didn't need to delete old topic.")
      ))
  (info "kafka-topics.sh --create:" (c/exec (c/lit (str "/opt/kafka/bin/kafka-topics.sh --create --zookeeper localhost:2181 --replication-factor 3 --partitions 5 --topic " topic
                                                          " --config unclean.leader.election.enable=false --config min.insync.replicas=3"
                                                          ))))
  (info "kafka-topics.sh --list:" (c/exec (c/lit "/opt/kafka/bin/kafka-topics.sh --list --zookeeper localhost:2181")))
  (topic-status node)
  (info "creating topic done")
)



(defn start!
  [id]
  ;(zk/start)
  (Thread/sleep (* 3 id))
  (c/su
    (info "start!  begins" id)
    (c/cd "/opt/kafka"
      (info id "kafka-server-start.sh:" (c/exec (c/lit "/opt/kafka/bin/kafka-server-start.sh -daemon config/server.properties"))))
      ;(c/exec (c/lit "/opt/kafka/bin/zookeeper-server-start.sh -daemon config/zookeeper.properties")))
    (info "start!  ends" id)
  )
)

(defn stop!
  []
  (info "stop! kafka (and zookeeper?).")
  (c/su
    (c/exec :service :zookeeper :stop)
    ;(c/exec (c/lit  "ps aux | grep zookeeper | grep -v grep | awk '{ print $2 }' | xargs kill -s kill"))
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
    (c/exec :echo (slurp (io/resource "server.properties")) :> filename)
    (set-broker-id! filename id)
    (info "deploy start! begins" id )
    (start! id)
    (info "deploy start! ends!" id )
    ; Create topic synchronously
    (when (= id 5)
       (create-topic node))
  ))

(defn zk-node-ids
      "Returns a map of node names to node ids."
      [test]
      (->> test
           :nodes
           (map-indexed (fn [i node] [node i]))
           (into {})))

(defn zk-node-id
      "Given a test and a node name from that test, returns the ID for that node."
      [test node]
      ((zk-node-ids test) node))

(defn zoo-cfg-servers
      "Constructs a zoo.cfg fragment for servers."
      [test]
      (->> (zk-node-ids test)
           (map (fn [[node id]]
                    (str "server." id "=" (name node) ":2888:3888")))
           (str/join "\n")))

(defn zk-deploy [test node]
  (c/exec :echo (zk-node-id test node) :> "/etc/zookeeper/conf/myid")

  (c/exec :echo (str (slurp (io/resource "zoo.cfg"))
                     "\n"
                     (zoo-cfg-servers test))
          :> "/etc/zookeeper/conf/zoo.cfg")

  (info node "ZK restarting")
  (c/exec :service :zookeeper :restart)
  (info node "ZK ready"))

(defn zk-teardown! [test node]
  (info node "tearing down ZK")
  (c/su
    (c/exec :service :zookeeper :stop)
    (c/exec :rm :-rf
            (c/lit "/var/lib/zookeeper/version-*")
            (c/lit "/var/log/zookeeper/*"))))

(defn install! [node sversion kversion]
   ; Install specific versions
  (info "install! Kafka begins" node )
  ; https://www.apache.org/dyn/closer.cgi?path=/kafka/0.10.2.0/kafka_2.12-0.10.2.0.tgz
  (let [id  (Integer.  (re-find #"\d+", (name node)))
        ;kafka "kafka_2.11-0.10.0.1"
        kafka (format "kafka_%s-%s" sversion kversion)
        ]
     ;(info node "apt-get update:" (c/exec :apt-get :update))
     ;(info node "install-jdk8!:" (debian/install-jdk8!))
    ;(c/exec :apt-get :install :-y :--force-yes "default-jre")
     ;(info node "apt-get install -y --force-yes wget:" (c/exec :apt-get :install :-y :--force-yes "wget"))
    (info node "rm -rf /opt/:" (c/exec :rm :-rf "/opt/"))
    (info node "mkdir -p /opt/:" (c/exec :mkdir :-p "/opt/"))
    (c/cd "/opt/"
          ; http://apache.claz.org/kafka/0.10.0.1/kafka_2.11-0.10.0.1.tgz
          ;(info "wget kafka:" (c/exec :wget (format "http://apache.claz.org/kafka/%s/%s.tgz" kversion kafka)))
          ;(info "gzip -d kafka:" (c/exec :gzip :-d (format "%s.tgz" kafka)))
          (info "tar xfz kafka:" (c/exec (c/lit (format "tar xfz /tmp/%s.tgz -C /opt" kafka))))
          (info "mv kafka:" (c/exec :mv kafka "kafka")))
          ;(info "rm kafka.tar:" (c/exec :rm (format "%s.tar" kafka))))
  ; (info "install! Kafka before call deploy" node ))
  ; (info "install! Kafka ends call deploy" node )
  (info "install! Kafka ends" node ))
)

(defn db
    "Kafka DB for a particular version."
    [sversion kversion]
    ;(let [zk (zk/db "3.4.8-1")]
      (reify db/DB
        (setup!  [_ test node]
          (let [id (Integer.  (re-find #"\d+", (name node)))]
            ;(info "setup! zk " node)
            ;(db/setup! zk test node)
            (info "setup! kafka" node)
            (install! node sversion kversion)
            ; need to start zk right before kafka deploy
            ;(db/setup! zk test node)
            (zk-deploy test node)
            (deploy id node kversion)
            (info "setup! kafka done"  node)
        ))
        (teardown!  [_ test node]
          ; Comment out for now, saves time on retries, setting up sometimes doesn't work first time, succeeds on second try...
          (info "tearing down Kafka NUKE!!!" node)
          (nuke!)
          (info "Kafka NUKED!!!" node)
          ;(info "tearing down Zookeeper")
          (zk-teardown! test node)
          )
        db/LogFiles
        (log-files [_ test node]
                   (concat ["/var/log/zookeeper/zookeeper.log"] (cu/ls-full "/opt/kafka/logs")))
             ));)

(defn test-setup-all []
      (let [db (db "2.12" "0.10.2.0")
            test tests/noop-test]
           (doall (map #(c/on % (db/setup! db test %)) [:n1 :n2 :n3 :n4 :n5]))))

(defn consumer [node queue]
      (gregor/consumer (str (name node) ":9092")
                       "jepsen.consumer"
                       [queue]
                       {"auto.offset.reset" "earliest"
                        "enable.auto.commit" "false"}))

(defn dequeue-only! [op node queue]
  (let [c (consumer node queue)]
    (try
      (let [cr (gregor/poll c 15000)
            message (first cr)
            value (:value message)]
           (if (nil? message)
             (assoc op :type :fail, :error :exhausted, :debug {:node node})
             (do
               ;(println "message:" message)
               (gregor/commit-offsets! c [{:topic queue :partition (:partition message) :offset (+ 1 (:offset message))}])
               ; If this fails, we will throw an exception and return timeout.  That way we don't consume it.
               (assoc op :type :ok :value (codec/decode value) :debug {:node node :partition (:partition message) :offset (:offset message)}))))
     (catch Exception e
       ;(pst e 25)
       ; Exception is probably timeout variant
       (info (str "Dequeue exception: " (.getMessage e) e))
       (assoc op :type :fail :error (.getMessage e) :debug {:node node}))
     (finally (gregor/close c)))))

(defn drain! [client queue op]
  (let [node (:node client)
        c (consumer node queue)]
       (try
         (timeout 60000 (assoc op :type :ok, :value :exhausted, :debug {:node node})
            (let [cr (gregor/poll c 15000)
                  message (last cr)]
                 (if (nil? message)
                   (assoc op :type :ok, :value :exhausted, :debug {:node node})
                   (do
                     ;(println "message:" message)
                     (timeout 15000 (gregor/commit-offsets-async! c [{:topic queue :partition (:partition message) :offset (+ 1 (:offset message))}]))
                     ; If this fails, we will throw an exception and return timeout.  That way we don't consume it.
                     (assoc op :type :ok :value :exhausted :debug {:node node :partition (:partition message) :offset (:offset message)}))))
                  )
         (catch Exception e
           ;(pst e 25)
           ; Exception is probably timeout variant
           (info (str "Dequeue exception: " (.getMessage e) e))
           (assoc op :type :ok :value :exhausted :debug {:node node}))
         (finally
           (try
             (gregor/close c)
             (catch Exception e
               (info (str "Drain exception: " (.getMessage e) e))
               ))
           ))))

(defn dequeue!
  "Given a channel and an operation, dequeues a value and returns the
  corresponding operation."
  [client queue op]
  (timeout 20000
           (assoc op :type :fail :error :timeout :debug {:node (:node client)})
           (dequeue-only! op (:node client) queue)))

(defn enqueue-only! [node queue value]
  (let [p (gregor/producer (str (name node) ":9092") {"acks" "all"
                                                      "retry.backoff.ms" "1000"
                                                      "batch.size" "1"})]
    (try
      (deref (gregor/send p queue (str value)))
      (gregor/flush p)
     (catch Exception e
       (info (str "Enqueue exception: " (.getMessage e) e))
       (throw e))
     (finally (gregor/close p)))))

(defn enqueue! [client queue op]
  (try
    (timeout 10000  (assoc op :type :fail, :error :timeout, :debug {:node (:node client)})
             (enqueue-only! (:node client) queue (:value op))
             (assoc op :type :ok :debug {:node (:node client)}))
    (catch Exception e
      (assoc op :type :fail, :error (.getMessage e)))))

(defrecord Client [client queue]
  client/Client
  (setup!  [this test node]
           (info "setup! client called" node)
           (let [client {:producer nil :consumer nil :node node :messages nil}]
            (info "done client setup..." node)
            (assoc this :client client)))

  (teardown!  [_ test]
    ;(consumer/shutdown (:consumer client))
    )

  (invoke!  [this test op]
     (case  (:f op)
         :enqueue (do
                    ;(info "Testing enqueue...")
                    ;(topic-status (:node client))
                    (enqueue! client queue op))
         :dequeue  (do
                     ;(info "Testing dequeue...")
                     ;(topic-status (:node client))
                     (dequeue! client queue op))
         :drain (drain! client queue op)
            (comment (do
                   ; Note that this does more dequeues than strictly necessary
                   ; owing to lazy sequence chunking.
                   (->> (repeat op)                  ; Explode drain into
                        (map #(assoc % :f :dequeue)) ; infinite dequeues, then
                        (map (partial dequeue! client queue))  ; dequeue something
                        (take-while op/ok?)  ; as long as stuff arrives,
                        (interleave (repeat op))     ; interleave with invokes
                        (drop 1)                     ; except the initial one
                        (map (fn [completion]
                                 (log-op completion)
                                 (jepsen/conj-op! test completion)))
                        dorun)
                   (assoc op :type :ok :value :exhausted)))
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
    [opts]
      (merge  tests/noop-test
              {:name "kafka"
               :os ubuntu/os
               :db        (db "2.12" "0.10.2.0")
               :client    (client)
               :model     (model/unordered-queue)
               :nemesis   (nemesis/partition-random-halves)
               :checker   (checker/compose
                            {:timeline    (timeline/html)
                             :perf        (checker/perf)
                             :queue       checker/queue
                             :total-queue checker/total-queue})
               :generator  gen1}
      ))

(defn -main
      "Handles command line arguments. Can either run a test, or a web server for
      browsing results."
      [& args]
      (cli/run! (merge (cli/single-test-cmd {:test-fn kafka-test})
                       (cli/serve-cmd))
                args))
