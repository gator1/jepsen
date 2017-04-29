(ns file-e.main
  (:gen-class)
  (:require [clojure.tools.logging :refer :all]
            [file-e.core :refer :all]
            [file-e.nemesis :refer :all]
            [file-e.net :refer :all]
            [jepsen.core :as jepsen]
            [jepsen.generator :as gen]
            [jepsen.nemesis :as nemesis]
            [jepsen.checker :as checker]
            [jepsen.tests :as tests]
            [jepsen.independent :as independent]
            [jepsen.cli :as cli]
            [jepsen.checker.timeline :as timeline]
           [knossos.model :refer [cas-register, multi-register]]))

(defn file-nemesis-test [opts]
  (info "noop test\n")
  (merge tests/noop-test
         opts
         {:nodes [:n1 :n2 :n3]
          :name "file-nemesis-test"
          :net file-iptables
          :client (client-nfs nil)
          ; :nemesis (nemesis/partition-random-node)
          ;:nemesis (nemesis-test-2)  ; reboot node
          ;:nemesis (nemesis-test-3)  ; reboot node
          :nemesis (nemesis-test-7)  ; block node's port
          ;:nemesis (nemesis-test-9)  ; kill process
          :generator (->> (gen/mix [r w cas])
                       (gen/stagger 1)
                       (gen/nemesis
                         (gen/seq (cycle [(gen/sleep 5)
                                          {:type :info, :f :start}
                                          (gen/sleep 5)
                                          {:type :info, :f :stop}])))
                       (gen/time-limit 20))
          :model (cas-register 0)
          :checker (checker/compose
                     {:perf   (checker/perf)
                      :linear checker/linearizable})}))

(defn file-cap-multi-test [opts]
  (info "consistency mutli-register test\n")
  (init-multi-data 1 4096)
  (merge tests/noop-test
         opts
         {:nodes [:n1 :n2 :n3]
          :name "file-cap-multi-test"
          :client (client-multi nil)
          :nemesis (nemesis/partition-random-halves)
          :generator (->> (gen/mix [rm wm])
                       (gen/stagger 1)
                       ;(gen/clients)
                       (gen/nemesis
                         (gen/seq (cycle [(gen/sleep 5)
                                          {:type :info, :f :start}
                                          (gen/sleep 5)
                                          {:type :info, :f :stop}])))
                       (gen/time-limit 10))
          :model (multi-register (zipmap key-list (repeat 0)))
          :checker (checker/compose {:timeline     (timeline/html)
                                     :perf   (checker/perf)
                                     :linear (independent/checker checker/linearizable)
                                     })
          }))

(defn -main
  "Handles command line arguments. Can either run a test, or a web server for
      browsing results."
  [& args]
  (cli/run! (merge (cli/single-test-cmd {:test-fn file-cap-multi-test})
                   (cli/serve-cmd))
            args))
