(ns smyrna.search
  (:require [clojure.string :as string]
            [clojure.set :refer [union intersection]]
            [smyrna.bitstream :as bitstream]
            [smyrna.corpus :as corpus]
            [smyrna.index :as index]))

(defn search-lemma
  [corpus lemma]
  (when-not (= lemma -1)
    (let [bs (bitstream/bit-source (:index corpus))]
      (.scroll bs ((:index-offsets corpus) lemma))
      (index/read-index-entry bs (corpus/num-documents corpus)))))

(defn search-word
  [corpus word]
  (search-lemma corpus (.indexOf (:lemmata corpus) word)))

(defn search-phrase
  [corpus phrase]
  (let [words (string/split phrase #" ")
        lemmata (map #(.indexOf (:lemmata corpus) %) words)
        candidates (map (comp set (partial search-lemma corpus)) lemmata)]
    (filter (partial corpus/contains-phrase? corpus lemmata)
            (sort (apply intersection candidates)))))
