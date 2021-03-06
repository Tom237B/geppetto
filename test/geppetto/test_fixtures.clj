(ns geppetto.test-fixtures
  (:import [java.net URI])
  (:use [clojure.java.shell :only [sh]])
  (:require [clojure.string :as str])
  (:use [geppetto.misc])
  (:use [geppetto.parameters])
  (:use [geppetto.models])
  (:use [geppetto.random])
  (:use [korma db core config])
  (:require [taoensso.timbre :as timbre]))

(defn establish-params
  []
  (new-parameters {:problem "Testing"
                   :name "test-1"
                   :control "{:foo [1 2]}"
                   :description "testing params"}))

(defn in-memory-db
  [f]
  (dosync (alter geppetto-db (constantly {:classname "org.h2.Driver"
                                          :subprotocol "h2"
                                          :subname "mem:test;DB_CLOSE_DELAY=-1"})))
  (set-delimiters "")
  (set-naming {:keys str/lower-case})
  (with-db @geppetto-db
    (exec-raw (slurp "tables.sql")))
  (establish-params)
  (f))

(defn travis-mysql-db
  [f]
  (sh "mysql" "-u" "travis" "geppetto_test" :in (slurp "tables.sql"))
  (dosync (alter geppetto-db (constantly (mysql {:db "geppetto_test" :user "travis" :password ""}))))
  (set-delimiters "")
  (set-naming {:keys str/lower-case})
  (establish-params)
  (f))

(defn setup-random-seed
  [f]
  (alter-var-root (var rgen) (constantly (new-seed 0)))
  (f))

(defn quiet-mode
  [f]
  (timbre/set-level! :warn)
  (f))
