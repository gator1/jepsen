(ns jepsen.galera.dirty-reads
  "Dirty read analysis for Mariadb Galera Cluster.

  In this test, writers compete to set every row in a table to some unique
  value. Concurrently, readers attempt to read every row. We're looking for
  casdes where a *failed* transaction's number was visible to some reader."
  (:require [clojure.tools.logging :refer :all]
            [clojure.core.reducers :as r]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.pprint :refer [pprint]]
            [knossos.op :as op]
            [jepsen [client :as client]
             [core :as jepsen]
             [db :as db]
             [tests :as tests]
             [control :as c :refer [|]]
             [checker :as checker]
             [nemesis :as nemesis]
             [generator :as gen]
             [util :refer [timeout meh]]
             [galera :as galera]]
            [jepsen.control.util :as cu]
            [jepsen.control.net :as cn]
            [jepsen.os.debian :as debian]
            [clojure.java.jdbc :as j]))
