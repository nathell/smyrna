(ns smyrna.morf
  (:require [clojure.string :as string])
  (:import (morfologik.stemming PolishStemmer WordData)))

(let [stemmer (PolishStemmer.)]
  (defn analyze [word]
    (locking stemmer
      (let [res (map (fn [^WordData x] {:base (-> x .getStem str) :tag (-> x .getTag str)}) (.lookup stemmer word))]
        (when-not (empty? res) res)))))

(defn tokenize [text]
  (re-seq #"[\pL0-9]+" (.toLowerCase text)))

(defn lemmatize [word]
  (let [bases (set (map :base (analyze word)))
        cnt (count bases)]
    (if (zero? cnt)
      {word 1}
      (zipmap bases (repeat (/ 1 cnt))))))

(defn lemma-frequencies [text]
  (apply merge-with + (map lemmatize (tokenize text))))

(defn word-frequencies [text]
  (-> text tokenize frequencies))