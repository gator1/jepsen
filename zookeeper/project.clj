(defproject jepsen.zookeeper "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :jvm-opts ["-Xmx4g" "-d64"]
  :repl-options {:init-ns jepsen.zookeeper}
  :dependencies [[org.clojure/clojure "1.7.0"]
                 [jepsen "0.1.2"]
                 [avout "0.5.4"]])
