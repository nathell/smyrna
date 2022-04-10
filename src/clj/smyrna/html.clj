(ns smyrna.html
  (:require [clojure.string :as string]
            [reaver]))

(defn parse [html]
  (-> html slurp reaver/parse reaver/edn :content first))

(defn tokenize-subparts [text]
  (interpose :nospace (map #(vector :word %) (re-seq #"[\pL0-9]+|[^\pL0-9]+" text))))

(defn tokenize [text]
  (mapcat tokenize-subparts (string/split text #"[ \n]+")))

(defn serialize-attrs [m]
  (mapcat (fn [[k v]] [[:attr (name k)] [:text v]]) m))

(defn serialize-tree [tree]
  (cond
   (string? tree) (tokenize tree)
   (map? tree)
   (let [{:keys [tag attrs content]} tree]
     (concat [[:tag (name tag)]] (serialize-attrs attrs) (mapcat serialize-tree content) [:end]))))
