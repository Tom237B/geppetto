(ns geppetto.git
  (:use [clojure.string :only [split-lines trim]])
  (:use [clojure.java.shell :only [sh]]))

(defn get-commit-date
  [git pwd commit]
  (let [git-output (:out (sh git (format "--work-tree=%s" pwd)
                             "show" "--format=raw" commit))
        timestamp (second (re-find #"committer .* (\d+) [-+]\d{4}" git-output))]
    (:out (sh "date" "+%Y-%m-%d %H:%M:%S" (format "--date=@%s" timestamp)))))

(defn git-meta-info
  [git pwd]
  (let [[out _ _ _ & msg] (split-lines (:out (sh git (format "--work-tree=%s" pwd)
                                                 "log" "-n" "1")))
        branch (trim (subs (:out (sh git (format "--work-tree=%s" pwd)
                                     "branch" "--contains")) 2))
        commit (subs out 7)]
    {:commit commit
     :commitdate (get-commit-date git pwd commit)
     :commitmsg (apply str (interpose "\n" (map (fn [s] (subs s 4)) (filter not-empty msg))))
     :branch branch}))