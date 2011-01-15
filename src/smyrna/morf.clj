(ns smyrna.morf
  (:require [clojure.string :as string])
  (:use clojure.test)
  (:import (morfologik.stemming PolishStemmer WordData)))

(let [stemmer (PolishStemmer.)]
  (defn analyze [word]
    (locking stemmer
      (let [res (map (fn [^WordData x] {:base (-> x .getStem str) :tag (-> x .getTag str)}) (.lookup stemmer word))]
        (when-not (empty? res) res)))))

(defn re-positions
  [^java.util.regex.Pattern re s]
  (let [m (re-matcher re s)]
    ((fn step []
       (when (.find m)
         (cons [(re-groups m) (.start m) (.end m)] (lazy-seq (step))))))))

(defn tokenize [text & [positions]]
  ((if positions re-positions re-seq) #"[\pL0-9]+" (.toLowerCase text)))

(defn lemmatize [word]
  (let [bases (set (map :base (analyze word)))
        cnt (count bases)]
    (if (zero? cnt)
      {word 1}
      (zipmap bases (repeat (/ 1 cnt))))))

(defn word-matches? [lemma word]
  (some (set (map :base (analyze word))) [lemma]))

(deftest test-morf
  (is (word-matches? "chleb" "chlebami"))
  (is (not (word-matches? "chlebek" "chlebami"))))

(defn lemma-frequencies [text]
  (apply merge-with + (map lemmatize (tokenize text))))

(defn word-frequencies [text]
  (-> text tokenize frequencies))