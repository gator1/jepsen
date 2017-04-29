(ns file-e.core-test
  (:require [clojure.test :refer :all]
            [file-e.main :refer :all]
            [jepsen.core :as jepsen])
  (:use     clojure.tools.logging))

(deftest do-file-nemesis-test
  (let [test (file-nemesis-test {})]
    (is (:valid? (:results (jepsen/run! test))))))

(deftest do-file-cap-multi-test
  (let [test (file-cap-multi-test {})]
    (is (:valid? (:results (jepsen/run! test))))))
