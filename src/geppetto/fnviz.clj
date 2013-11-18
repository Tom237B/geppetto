;; Adapted from work by:
;; Copyright Jason Wolfe and Prismatic, 2013.
;; Licensed under the EPL, same license as Clojure

(ns geppetto.fnviz
  (:require [clojure.java.shell :as shell]
            [clojure.string :as str])
  (:import (java.util HashSet) (java.io File))
  (:use [plumbing.core])
  (:use [plumbing.fnk.pfnk :only [input-schema]])
  (:use [geppetto.parameters])
  (:use [geppetto.fn]))

(defn double-quote [s] (str "\"" s "\""))

(defn- attribute-string [label-or-attribute-map]
  (when label-or-attribute-map
    (str "["
         (str/join "," 
                   (map (fn [[k v]] (str (name k) "=" v))
                        (if (map? label-or-attribute-map) 
                          label-or-attribute-map
                          {:label (double-quote label-or-attribute-map)})))
	 "]")))
      
(defn- walk-graph
  [g root node-key-fn node-label-fn edge-child-pair-fn
   ^HashSet visited indexer]
  (let [node-key (node-key-fn g root)
	node-map (node-label-fn g root)]
    (when-not (.contains visited node-key)
      (.add visited node-key)
      (apply str
	     (indexer node-key) (attribute-string node-map) ";\n"
	     (apply concat 
                    (for [[edge-map child] (edge-child-pair-fn root)]
                      (cons (str (indexer node-key) " -> " (indexer (node-key-fn g child)) 
                                 (attribute-string edge-map)
                                 ";\n")
                            (walk-graph g child node-key-fn node-label-fn edge-child-pair-fn
                                        visited indexer))))))))


(defn write-graphviz [file-stem g roots node-key-fn node-label-fn edge-child-pair-fn] 
  (let [dot-file (str file-stem ".dot")
        pdf-file (str file-stem ".pdf")
        indexer (memoize (fn [x] (double-quote (gensym))))
        vis (HashSet.)]
    (spit dot-file
          (str "strict digraph {\n"
               " rankdir = LR;\n"
               (apply str (for [root roots] (walk-graph g root node-key-fn node-label-fn
                                                        edge-child-pair-fn vis indexer)))
               "}\n"))
    (shell/sh "dot" "-Tpdf" "-o" pdf-file dot-file)
    pdf-file))

(defn my-node-key-fn [g k] k)

(defn my-label-fn
  [g k]
  (if-let [params (fn-params (get g k))]
    (format "%s\n%s" (name k) (vec params))
    (name k)))

(defn graphviz-el [g file-stem edge-list]
  (let [edge-map (map-vals #(map second %) (group-by first edge-list))]
    (write-graphviz
     file-stem g
     (set (apply concat edge-list))
     my-node-key-fn my-label-fn #(for [e (get edge-map %)] [nil e]))))

(defn graph-edges [g]
  (for [[k node] g
        parent (keys (input-schema node))
        :when (nil? (get-in (meta node) [:params parent]))]
    [parent k]))

(defn graphviz-graph
  "Generate file-stem.dot and file-stem.pdf representing the nodes and edges of Graph g"
  [file-stem g]
  (graphviz-el g file-stem (graph-edges g)))

;; (graphviz-graph "/tmp/foobar" {:x (fnk [a]) :y (fnk [a x])})
;; then check /tmp/foobar.pdf