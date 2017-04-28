(ns file-e.core-test
  (:require [clojure.test :refer :all]
            [file-e.core :refer :all]
            [file-e.nemesis :refer :all]
            [file-e.net :refer :all]
            [jepsen.core :as jepsen]
            [jepsen.generator :as gen]
            [jepsen.nemesis :as nemesis]
            [jepsen.checker :as checker]
            [jepsen.tests :as tests]
            [jepsen.independent :as independent]
            [knossos.model :refer [cas-register, multi-register]])
  (:use     clojure.tools.logging))

(deftest do-file-nemesis-test
  (let [test (file-nemesis-test {})]
    (is (:valid? (:results (jepsen/run! test))))))

(deftest do-file-cap-multi-test
  (let [test (file-cap-multi-test {})]
    (is (:valid? (:results (jepsen/run! test))))))