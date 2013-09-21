(ns geppetto.optimize
  (:import [java.io File])
  (:require [clojure.string :as str])
  (:use [clojure.java.io :only [file]])
  (:use [geppetto.misc :only [format-date-ms]])
  (:use [geppetto.git :only [git-meta-info]])
  (:use [geppetto.runs :only [commit-run get-raw-results]])
  (:use [geppetto.r :only [results-to-rbin]])
  (:use [geppetto.parameters :only [read-params explode-params vectorize-params]]))

(defn random-neighboring-indices
  [vparams param-indices]
  (let [chosen-key (rand-nth (filter (fn [k] (second (get vparams k))) (keys vparams)))
        chosen-count (count (get vparams chosen-key))
        current-index (get param-indices chosen-key)
        chosen-index (if (< (rand) 0.1)
                       ;; include this so we don't get stuck in left-right repeats
                       (rand-nth (range chosen-count))
                       (cond (= current-index 0) (inc current-index)
                             (= current-index (dec chosen-count)) (dec current-index)
                             :else (rand-nth [(inc current-index) (dec current-index)])))]
    (assoc param-indices chosen-key chosen-index)))

(defn choose-param-indices
  [vparams attempted-param-indices]
  (let [attempted (set attempted-param-indices)]
    (if (empty? attempted-param-indices)
      ;; start with random params
      (into {} (for [[k vs] (seq vparams)] [k (rand-int (count vs))]))
      (loop [i 0]
        (when (< i 100)
          (let [new-param-indices (random-neighboring-indices vparams (last attempted-param-indices))]
            (if (attempted new-param-indices) (recur (inc i))
                new-param-indices)))))))

(defn select-params-from-indices
  [vparams param-indices]
  (into {} (for [[k vs] (seq vparams)] [k (nth vs (get param-indices k))])))

(defn solution-delta
  "A negative result means there was an improvement."
  [opt-type opt-metric results-new results-old]
  (if (= :max opt-type)
    ;; maximizing, so old-new < 0 is good
    (- (get results-old opt-metric) (get results-new opt-metric))
    ;; else, minimizing, so new-old < 0 is good
    (- (get results-new opt-metric) (get results-old opt-metric))))

(defn better-than?
  [opt-type opt-metric results-new results-old strict?]
  (if strict?
    (> 0 (solution-delta opt-type opt-metric results-new results-old))
    (>= 0 (solution-delta opt-type opt-metric results-new results-old))))

;; simulated annealing
(defn optimize-loop
  [control-params run-fn opt-type opt-metric alpha initial-temperature temperature-schedule max-steps]
  (loop [best-results nil
         results []
         attempted-param-indices []
         temperature initial-temperature
         step 1]
    (let [ps-indices (choose-param-indices control-params attempted-param-indices)]
      (if (or (nil? ps-indices) (> step max-steps))
        best-results
        (let [ps (select-params-from-indices control-params ps-indices)
              [control-results _ _] (run-fn false ps)
              prob (when (not-empty results)
                     (Math/exp (- (/ 1.0 temperature)
                                  (solution-delta opt-type opt-metric control-results (last results)))))
              best? (or (nil? best-results)
                        (better-than? opt-type opt-metric control-results best-results true))
              keep? (or (empty? results)
                        (better-than? opt-type opt-metric control-results (last results) false)
                        (< (rand) prob))]
          (prn control-results)
          (println "Best?" best? "Keep?" keep? "temperature" temperature "step" step)
          (recur (if best? control-results best-results)
                 (if keep? (conj results control-results) results)
                 (conj attempted-param-indices ps-indices)
                 (if (= 0 (mod step temperature-schedule)) (* alpha temperature) temperature)
                 (inc step)))))))

(defn optimize
  [run-fn params-str-or-map opt-type opt-metric alpha initial-temperature temperature-schedule max-steps
   datadir seed git recordsdir nthreads repetitions upload? save-record?]
  (let [t (. System (currentTimeMillis))
        recdir (.getAbsolutePath (File. (str recordsdir "/" t)))
        ;; TODO: support comparative runs
        params (if (string? params-str-or-map) (read-params params-str-or-map)
                   ;; else, should be a map; no reason to get the params from the db
                   params-str-or-map)
        ;; don't explode params, just vectorize
        control-params (vectorize-params (:control params))
        working-directory (System/getProperty "user.dir")
        simcount 1
        run-meta (merge {:starttime (format-date-ms t)
                         :paramid (:paramid params)
                         :datadir datadir :recorddir recdir :nthreads nthreads
                         :pwd working-directory :repetitions repetitions :seed seed
                         :hostname (.getHostName (java.net.InetAddress/getLocalHost))
                         :username (System/getProperty "user.name")
                         :simcount simcount}
                        (git-meta-info git working-directory))]
    (println (format "Parameter space: %d possible parameters"
                     (reduce * (map count (vals control-params)))))
    (optimize-loop control-params run-fn opt-type opt-metric
                   alpha initial-temperature temperature-schedule max-steps)))
