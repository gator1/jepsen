(ns jepsen.kafka-test
  (:require [clojure.test :refer :all]
            [jepsen.core :as jepsen]
            [jepsen.kafka :as kafka]))

(deftest kafka-test
   (is  (:valid?  (:results  (jepsen/run!  (kafka/kafka-test "3.4.5+dfsg-2"))))))
