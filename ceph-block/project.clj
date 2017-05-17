(defproject ceph-block "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :repl-options {:init-ns block.core}
  :main block.core
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [jepsen "0.1.6-SNAPSHOT"]
                 [org.clojure/tools.trace "0.7.9"]
               ;  [knossos "0.2.8"]
                ])
