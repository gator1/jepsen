(ns kafkatest.core-test
  (:require [clojure.test :refer :all]
            [clojure.tools.logging :refer :all]
            [kafkatest.core :refer :all]
            [clj-kafka.consumer.zk  :as consumer]
            [clj-kafka.producer :as producer]
            [clj-kafka.new.producer :as nproducer]
            [clj-kafka.zk           :as czk]
            [clj-kafka.core         :as ckafka])
            )


(deftest p
  (def node :n2)
  (println "producer" (str (name node) ":9092"))
  (println (= "n1:9092" (str (name node) ":9092")))

  (def p  (producer/producer  {"metadata.broker.list"  (str (name node) ":9092")
                               "request.required.acks" "-1" ; all in-sync brokers
                               "producer.type"         "sync"
                               "message.send.max_retries" "1"
                               "connect.timeout.ms"    "1000"
                               "retry.backoff.ms"       "1000"
                               "serializer.class" "kafka.serializer.DefaultEncoder"
                               "partitioner.class" "kafka.producer.DefaultPartitioner"}))
  (producer/send-message p  (producer/message "jepsen"  (.getBytes "this is my Test")))
  (producer/send-message p  (producer/message "jepsen"  (.getBytes "this is my Kyle")))
)



(deftest p2
  (def node :n2)
  (println "producer" (str (name node) ":9092"))
  (println (= "n1:9092" (str (name node) ":9092")))
  (def p  (producer/producer  {"metadata.broker.list"  (str (name node) ":9092")
                                       "serializer.class" "kafka.serializer.DefaultEncoder"
                                       "partitioner.class" "kafka.producer.DefaultPartitioner"}))

  (producer/send-message p  (producer/message "jepsen"  (.getBytes "this is my gators")))
  (producer/send-message p  (producer/message "jepsen"  (.getBytes "this is my jepsen")))
)

(deftest c
  (println "consumer")
  (def consumer-config  {"zookeeper.connect" "n1:2181"
                         "group.id" "clj-kafka.consumer"
                         "auto.offset.reset" "smallest"
                         "auto.commit.enable" "false"})

  (def x
         (ckafka/with-resource  [c  (consumer/consumer consumer-config)]
            consumer/shutdown
            (doall  (take 2  (consumer/messages c "jepsen")))))
  (println  x)

)

(defn drain!
    "Returns a sequence of all elements available within dt millis."
    []
    (ckafka/with-resource
      [consumer (consumer/consumer
                  {"zookeeper.connect"    "n1:2181"
                   "group.id"            "jepsen.consumer"
                   "auto.offset.reset"   "smallest"
                   "auto.commit.enable"  "true"})]
      consumer/shutdown
      (let  [done  (atom  [])]
        (loop  [coll  (consumer/messages consumer  "jepsen")]
          (if  (-> done
                   (swap! conj  (first coll))
                   future
                   (deref 1000 ::timeout)
                   (= ::timeout))
            @done
            (recur  (rest coll)))))))

(deftest d
    "Returns a sequence of all elements available within dt millis."
    (let [res (drain! )]
      ( println res)
      ))
