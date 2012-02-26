(defproject smyrna "0.2"
  :description "A simple concordancer for Polish corpora."
  :main smyrna.main
  :dependencies [[org.clojure/clojure "1.2.1"]
                 [org.clojars.nathell/morfologik-stemming "1.5.1-cidict1.8.1"]
                 [clj-json-rpc "0.1"]
                 [clj-tagsoup "0.2.6"]
                 [hiccup "0.3.1"]
                 [clojure-csv/clojure-csv "1.3.1"]
                 [ring "0.3.5"]])
