(ns smyrna.html
  (:require [pl.danieljanus.tagsoup :as tagsoup]
            [clojure.string :as string]))

(defn tokenize-subparts [text]
  (interpose :nospace (map #(vector :word %) (re-seq #"[\pL0-9]+|[^\pL0-9]+" text))))

(defn tokenize [text]
  (mapcat tokenize-subparts (string/split text #"[ \n]+")))

(defn serialize-attrs [m]
  (mapcat (fn [[k v]] [[:attr (name k)] [:text v]]) m))

(defn serialize-tree [tree]
  (cond
   (string? tree) (tokenize tree)
   (vector? tree)
   (let [[tag attrs & data] tree]
     (concat [[:tag (name tag)]] (serialize-attrs attrs) (mapcat serialize-tree data) [:end]))))
