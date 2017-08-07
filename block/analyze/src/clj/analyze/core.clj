(ns analyze.core
  (:require [clojure.tools.logging :refer :all]
            [clojure.java.io :as io]
            [clojure.edn :as edn]
            [clojure.string :as str]
            [clojure.stacktrace :as trace]
            [clojure.core.reducers :as r]
            [clojure.set :as set]
            [jepsen.util :as util]
            [jepsen.store :as store]
            [jepsen.checker.perf :as perf]
            [multiset.core :as multiset]
            [gnuplot.core :as g]

            [jepsen [core :as jepsen]
             [db :as db]
             [control :as c]
             [cli :as cli]
             [client :as client]
             [nemesis :as nemesis]
             [generator :as gen]
             [checker :as checker]
             [tests :as tests]
             [util :refer [timeout]]]

             [knossos [model :as model]
               [op :as op]
               [linear :as linear]
               [history :as history]]
            [knossos.linear.report :as linear.report]
            [knossos.model :refer [register cas-register]])
  (:use [clojure.java.shell :only [sh]])
  (:import (java.text SimpleDateFormat))
  (:gen-class))

(defn type-label [t]
  (case t
    :info "timeout or error"
    (name t)))

(defn parse-log [log-path]
  (let [lines (slurp log-path)
        date-format (SimpleDateFormat. "yyyy-MM-dd hh:mm:ss,SSS{z}")
        time0 (atom nil)]
    (map
      (fn [line]
        (let [[ymd hmsSz _ _ _ worker-str nemesis-flag _ t op val status] (str/split line #"\s+")
              time (.parse date-format (str ymd " " hmsSz))
              millis (.getTime time)
              _ (when-not @time0 (reset! time0 millis))
              nanos (* 1000000 (- millis @time0))
              nemesis? (= nemesis-flag ":nemesis")
              to-keyword #(keyword (if (str/blank? %) % (str/replace % ":" "")))
              ]
          (if nemesis?
            {:type :info :f (to-keyword t) :time nanos :process :nemesis}
            (let [worker-num (long (- (int (first worker-str)) (int \0)))]
              {:type (to-keyword t) :f (to-keyword op) :value val :time nanos :process worker-num}
              )
            )
          ))
      (str/split-lines lines)
      )
    )
  )

(defn rate-preamble
  "Gnuplot commands for setting up a rate plot."
  [title output-path]
  (concat (perf/preamble output-path)
          [[:set :title title]]
          '[[set ylabel "Throughput (hz)"]]))

(defn rate-graph!
  "Writes a plot of operation rate by their completion times."
  [parsed-args]

  (let [log-path (get parsed-args "--log-path")
        output-path (get parsed-args "--output-path")
        title (or (get parsed-args "--title") "Jepsen")
        _ (assert (not (str/blank? log-path)) "Must provide log path using --log-path")
        _ (assert (not (str/blank? output-path)) "Must provide output path using --output-path")

        ops (parse-log log-path)
        history (vec ops)

        dt 10
        td (double (/ dt))
        t-max (->> history (r/map :time) (reduce max 0) util/nanos->secs)
        datasets (->> history
                      (r/remove op/invoke?)

                      ; Don't graph nemeses
                      (r/filter (comp integer? :process))

                      ; Compute rates
                      (reduce (fn [m op]
                                (update-in m [(:f op)
                                              (:type op)
                                              (perf/bucket-time dt
                                                                (util/nanos->secs
                                                                  (:time op)))]
                                           #(+ td (or % 0))))
                              {}))

        fs (util/polysort (keys datasets))

        fs->points (perf/fs->points fs)
        ]
    (g/raw-plot!
      (concat (rate-preamble title output-path)
              (perf/nemesis-regions history)
              ; Plot ops
              [['plot (apply g/list
                             (for [f fs, t perf/types]
                               ["-"
                                'with 'linespoints
                                'linetype (perf/type->color t)
                                'pointtype (fs->points f)

                                'title (str (util/name+ f) " "

                                            (type-label t))]))]])
      (for [f fs, t perf/types]
        (let [m (get-in datasets [f t])]
          (->> (perf/buckets dt t-max)
               (map (juxt identity #(get m % 0)))))))))

(defn latency-preamble
  "Gnuplot commands for setting up a latency plot."
  [title output-path]
  (concat (perf/preamble output-path)
          [[:set :title title]]
          '[[set ylabel "Latency (ms)"]
            [set logscale y]]))

(defn point-graph!
  "Writes a plot of raw latency data points."
  [parsed-args]
  (let [log-path (get parsed-args "--log-path")
        output-path (get parsed-args "--output-path")
        title (or (get parsed-args "--title") "Jepsen")
        _ (assert (not (str/blank? log-path)) "Must provide log path using --log-path")
        _ (assert (not (str/blank? output-path)) "Must provide output path using --output-path")

        ops (parse-log log-path)
        history (vec ops)
        history     (util/history->latencies history)

        datasets    (perf/invokes-by-f-type history)

        fs          (util/polysort (keys datasets))

        fs->points  (perf/fs->points fs)]
    (g/raw-plot!
      (concat (latency-preamble title output-path)
              (perf/nemesis-regions history)
              ; Plot ops
              [['plot (apply g/list
                             (for [f fs, t perf/types]
                               ["-"
                                'with        'points
                                'linetype    (perf/type->color t)
                                'pointtype   (fs->points f)

                                'title       (str (util/name+ f) " "

                                                  (type-label t))]))]])
      (for [f fs, t perf/types]
        (map perf/latency-point (get-in datasets [f t]))))
    output-path))

(defn latency-history!
  "Writes a plot of raw latency data points."
  [parsed-args]
  (let [log-path (get parsed-args "--log-path")
        output-path (get parsed-args "--output-path")
        title (or (get parsed-args "--title") "Jepsen")
        _ (assert (not (str/blank? log-path)) "Must provide log path using --log-path")
        _ (assert (not (str/blank? output-path)) "Must provide output path using --output-path")

        ops (parse-log log-path)
        history (vec ops)
        history     (util/history->latencies history)
        nemesis-intervals (perf/nemesis-intervals history)]

    (println "nemesis-intervals: " nemesis-intervals)
    (println "history: " history)
    output-path))

(defn check-linear [parsed-args]
  (let [log-path (get parsed-args "--log-path")
        output-path (get parsed-args "--output-path")
        title (or (get parsed-args "--title") "Jepsen")
        _ (assert (not (str/blank? log-path)) "Must provide log path using --log-path")
        _ (assert (not (str/blank? output-path)) "Must provide output path using --output-path")

        ops (parse-log log-path)
        history (vec ops)
        model (cas-register 0)
        a (linear/analysis model history)
        ]

    (when-not (:valid? a)
      (meh
        ; Renderer can't handle really broad concurrencies yet
        (linear.report/render-analysis!
          history a (str output-path "/linear.svg"))))

    ; Writing these can take *hours* so we truncate
    (assoc a
      :final-paths (take 10 (:final-paths a))
      :configs     (take 10 (:configs a)))

    )

  )

(defn -main
  "Analyze jepsen tests results"
  [& args]
  (let [parsed-args (into {} (map vec (partition 2 args)))
        action (get parsed-args "--action")]
    (case action
      "rate-graph" (rate-graph! parsed-args)
      "point-graph" (point-graph! parsed-args)
      "latency-history" (latency-history! parsed-args)
      "check-linear" (check-linear parsed-args)
      (throw (RuntimeException. (str "Unrecognized action: " action)))
      )
    )
  )

; (-main "--action"  "point-graph" "--log-path" "/home/xuelin/tepsen/analyze/tmp.log" "--output-path" "/home/xuelin/tepsen/analyze/tmp2.png")
