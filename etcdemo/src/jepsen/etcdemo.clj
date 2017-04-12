(ns jepsen.etcdemo
    (:gen-class)
    (:require [jepsen.cli :as cli]
              [jepsen.tests :as tests]))


(defn etcd-test
      "Given an options map from the command line runner (e.g. :nodes, :ssh,
      :concurrency, ...), constructs a test map."
      [opts]
      (merge tests/noop-test
             opts))

(defn -main
      "Handles command line arguments. Can either run a test, or a web server for
      browsing results."
      [& args]
      (cli/run! (cli/single-test-cmd {:test-fn etcd-test})
                args))