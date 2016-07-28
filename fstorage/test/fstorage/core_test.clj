(ns fstorage.core-test
  (:require [clojure.test  :refer :all]
            [jepsen.core   :as jepsen]
            [fstorage.core :as fs]))

; fstorage consistency testing
(deftest fscp-test
  (is (:valid? (:results (jepsen/run! (fs/fstorage-test))))))

; fstorage availability testing
(deftest fsap-test
  ())

; fstorage performance testing
(deftest fsperf-test
  ())

; fstorage client-server consistency testing
(deftest fscscp-test
  ())