(ns geppetto.misc
  (:use [korma.db :only [mysql]])
  (:use [korma.config])
  (:import (java.util Date))
  (:import (java.text SimpleDateFormat))
  (:require [geppetto.workers :as workers])
  (:use [taoensso.timbre]))

(def geppetto-db (ref nil))

(defn setup-geppetto
  [dbhost dbport dbname dbuser dbpassword quiet?]
  (workers/load-resque)
  (if quiet? (set-level! :warn) (set-level! :info))
  (set-delimiters "`")
  (dosync (alter geppetto-db (constantly (mysql {:db dbname :port dbport :user dbuser
                                                 :password dbpassword :host dbhost})))))

(defn format-date-ms
  [ms]
  (.format (SimpleDateFormat. "yyyy-MM-dd HH:mm:ss") (Date. (long ms))))
