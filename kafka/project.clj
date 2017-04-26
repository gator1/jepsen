(defproject jepsen.kafka "0.3.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :repl-options {:init-ns jepsen.kafka}
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.clojars.khdegraaf/jepsen "0.1.2.2"]
                 [org.clojars.khdegraaf/knossos "0.2.9.2-SNAPSHOT"]
                 ;[knossos "0.2.8"]
                 [jepsen.zookeeper "0.1.0-SNAPSHOT"]
                 [org.clojars.khdegraaf/gregor "0.5.3"]
                 ])
