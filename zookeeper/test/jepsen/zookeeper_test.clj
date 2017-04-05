(ns jepsen.zookeeper-test
  (:require [clojure.test :refer :all]
            [clojure.tools.logging :refer :all]
            [jepsen.core :as jepsen]
            [jepsen.checker :as checker]
            [jepsen.zookeeper :as zk]))

(deftest zk-test
  (is (:valid? (:results (jepsen/run! (zk/zk-test "3.4.5+dfsg-2+deb8u1"))))))

(comment (deftest zk-linearizable-analysis-test
  (is (:valid? (:results (jepsen/analyze! (assoc (zk/zk-test "3.4.5+dfsg-2+deb8u1")
                                                 :checker checker/linearizable)
                          "/huawei/jepsen/zookeeper/store/latest"))))))
