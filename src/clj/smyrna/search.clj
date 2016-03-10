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

(defn filter-fn
  [header-indexed valsets [k v]]
  (let [i (header-indexed k)]
    (if (set? v)
      (fn [row] (v (row i)))
      (let [valset (valsets i)
            v (.toLowerCase v)
            matches (set (remove nil? (map-indexed (fn [i x] (when (not= -1 (.indexOf (.toLowerCase x) v)) i)) valset)))]
        (fn [row] (matches (row i)))))))

(defn andf
  ([] (constantly true))
  ([f] f)
  ([f g] (fn [x] (and (f x) (g x))))
  ([f g & fs] (andf f (apply andf g fs))))

(defn compute-filter
  [{[header _ valsets] :meta} m]
  (let [header-indexed (zipmap header (range))
        single-filters (map (partial filter-fn header-indexed valsets) m)]
    (apply andf single-filters)))

(defn nths
  ([s ns] (nths s ns 0))
  ([s [n & ns] skipped]
   (lazy-seq
    (if n
      (let [s (drop (- n skipped) s)]
        (cons (first s) (nths (rest s) ns (inc n))))))))

(defn get-documents
  [corpus {:keys [offset limit phrase filters]}]
  (let [documents (if (seq phrase)
                    (search-phrase corpus phrase)
                    (range (corpus/num-documents corpus)))
        flt (compute-filter corpus filters)
        [_ _ valsets all-rows] (:meta corpus)
        documents (filter flt (nths all-rows documents))
        decode-row (fn [row] (mapv #(nth %1 %2) valsets row))]
    {:results (mapv decode-row (take limit (drop offset documents))),
     :total (delay (count documents))}))
