(ns file-e.util)

; define path
;(def ^:private file-path "/home/gary/mike/gator1/jepsen/file-e/mount/n")
(def file-path "mount/n")

(defn nid
      [n]
      (->> (str n)
           (re-find #"\d+")
           (Integer. )))

(defn location
      [n]
      (str file-path (nid n) "/data"))

(defn set-reg
      [loc val]
      (spit loc val))
;  (sh "sh" "-c" (str "echo " val " > " loc)))




