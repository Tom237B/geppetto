(ns geppetto.repeat
  (:use [clojure.pprint :only [pprint]])
  (:use [clojure.data :only [diff]])
  (:use [geppetto.runs])
  (:use [geppetto.records])
  (:use [geppetto.parameters])
  (:use [geppetto.random])
  (:use [taoensso.timbre]))

(defn repeat-run
  "Returns results. Probably should be used by (verify-identical-repeat-run)."
  [runid run-fn datadir git recordsdir nthreads]
  (let [run (get-run runid)
        params-string (format "{:control %s :comparison %s}"
                         (prn-str (:control run))
                         (prn-str (:comparison run)))
        params (read-params params-string)]
    (alter-var-root (var rgen) (constantly (new-seed (:seed run))))
    (info (format "Repeating run %d with parameters:" runid))
    (info (with-out-str (pprint params)))
    (run-with-new-record run-fn params-string
      datadir (:seed run) git recordsdir nthreads
      (:repetitions run) false true true)))

(defn extract-single
  [rs resultstype only-ignore]
  (let [{:keys [only ignore]} (get only-ignore resultstype)]
    (cond only (select-keys rs only)
          ignore (apply dissoc rs ignore)
          :else rs)))

(defn extract-relevant-results
  [results only-ignore]
  (for [{:keys [control comparison comparative]} results]
    {:control (extract-single control :control only-ignore)
     :comparison (extract-single comparison :comparison only-ignore)
     :comparative (extract-single comparative :comparative only-ignore)}))

(defn value-diff?
  [old-val new-val]
  (if (and (or (= Double (type old-val)) (= Float (type old-val)))
           (or (= Double (type new-val)) (= Float (type new-val))))
    (> (Math/abs (- old-val new-val)) 0.001)
    (not= old-val new-val)))

(defn results-diff
  [old-sim new-sim]
  (into {} (for [resultstype [:control :comparison :comparative]]
             (let [old-rs (dissoc (get old-sim resultstype) :params)
                   new-rs (dissoc (get new-sim resultstype) :params)
                   key-val-pairs (map (fn [k] [k (get old-rs k) (get new-rs k)])
                                      (sort (set (concat (keys old-rs) (keys new-rs)))))
                   diffs (for [[k old-val new-val] key-val-pairs
                               :when (value-diff? old-val new-val)]
                           [k old-val new-val])]
               [resultstype diffs]))))

(defn verify-identical-repeat-run
  "only-ignore parameter takes the format:
   {:control {:only [:key1 :key2]} :comparison {:ignore [:key1 :key2]}} etc.
   Only takes priority over ignore. The :params field is always ignored."
  [runid only-ignore run-fn datadir git nthreads]
  (let [old-run (get-run runid)
        old-results (doall
                     (extract-relevant-results
                      (sort-by (comp :simulation :control)
                               (get-raw-results (:recorddir old-run)))
                      only-ignore))
        ;; note, new-results will have a :params field (old-results does not),
        ;; and this will be exploited below
        new-results (doall
                     (extract-relevant-results
                      (sort-by (comp :simulation :control)
                               (repeat-run runid run-fn datadir git "/tmp" nthreads))
                      only-ignore))]
    (filter (fn [{{:keys [control comparison comparative]} :diffs}]
              (or (not-empty control) (not-empty comparison) (not-empty comparative)))
            (for [[old-sim new-sim] (partition 2 (interleave old-results new-results))]
              {:params {:control (:params (:control new-sim))
                        :comparison (:params (:comparison new-sim))}
               :diffs (results-diff old-sim new-sim)}))))
