(defproject jepsen.kafka "0.3.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :repl-options {:init-ns jepsen.kafka}
  :plugins [[lein-clean-m2 "0.1.2"]]
  :main jepsen.kafka
  :jvm-opts ["-Dlog4j.configuration=resources/log4j.properties"
             ;"-Dlogback.configurationFile=resources/logback.xml"
             ]
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.clojars.khdegraaf/jepsen "0.1.5.3-SNAPSHOT" ;:exclusions [org.slf4j/log4j-over-slf4j] ;slf4j-log4j12] ; log4j/log4j]
                  ]
                 ;[org.clojars.khdegraaf/knossos "0.2.9.2-SNAPSHOT" :exclusions [org.slf4j/slf4j-log4j12]
                  ;]
                 ;[jepsen.zookeeper "0.1.0-SNAPSHOT"]
                 [org.clojars.khdegraaf/gregor "0.5.3"
                  ;:exclusions [org.slf4j/log4j-over-slf4j] ;org.slf4j/slf4j-log4j12] ;org.slf4j/log4j-over-slf4j]; org.slf4j/slf4j-log4j12]
                  ;:exclusions [org.slf4j/slf4j-log4j12 log4j/log4j]
                  ]
                 ])
