(ns smyrna.core
  (:use [clojure.set :only [union]])
  (:require [smyrna.morf :as morf]))

(defn create-inverted-index [freqs]
  (apply merge-with union (map #(zipmap (keys %1) (repeat #{%2})) freqs (iterate inc 0))))

(defn index-fileset [fileset]
  (let [documents (map slurp fileset)
        lemma-frequencies (vec (map morf/lemma-frequencies documents))
        word-frequencies (vec (map morf/word-frequencies documents))]
    {:files (vec fileset)
     :lemma-frequencies lemma-frequencies
     :word-frequencies word-frequencies
     :lemma-index (create-inverted-index lemma-frequencies)
     :word-index (create-inverted-index word-frequencies)}))
