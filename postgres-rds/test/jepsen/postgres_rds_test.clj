(ns jepsen.postgres-rds-test
  (:require [clojure.test :refer :all]
            [jepsen.core :as jepsen]
            [jepsen.postgres-rds :refer [bank-test]]))

(def node "n1")

(deftest bank-test'
  (is (:valid? (:results (jepsen/run! (bank-test
                                        node
                                        10
                                        100
                                        ""
                                        false))))))
