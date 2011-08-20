(defproject smyrna "0.1"
  :description "FIXME: write"
  :main smyrna.main
  :dependencies [[org.clojure/clojure "1.2.0"]
                 [org.clojure/clojure-contrib "1.2.0"]
                 [org.clojars.nathell/morfologik-stemming "1.4.0-cidict1.6"]
                 [clj-tagsoup "0.2.4"]
                 [hiccup "0.3.1"]
                 [clojure-csv/clojure-csv "1.3.1"]
                 [ring "0.3.5"]]
  :dev-dependencies [[swank-clojure "1.2.0"]])
