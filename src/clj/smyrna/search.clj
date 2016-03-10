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
  [corpus phrase within]
  (let [words (string/split phrase #" ")
        lemmata (map #(.indexOf (:lemmata corpus) %) words)
        candidates (map (comp set (partial search-lemma corpus)) lemmata)
        candidates (if within (conj candidates (set within)) candidates)]
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

(def contexts (atom {}))

(defn get-documents-raw
  [corpus {:keys [phrase filters within]}]
  (let [context (:documents (@contexts within))
        documents (if (seq phrase)
                    (search-phrase corpus phrase context)
                    (or context (range (corpus/num-documents corpus))))
        flt (compute-filter corpus filters)
        [_ _ valsets all-rows] (:meta corpus)]
    (map (fn [i row] (when (flt row) [i row]))
         documents
         (nths all-rows documents))))

(defn get-documents
  [corpus {:keys [offset limit], :or {offset 0, limit 10}, :as q}]
  (let [documents (map second (get-documents-raw corpus q))
        [_ _ valsets _] (:meta corpus)
        decode-row (fn [row] (mapv #(nth %1 %2) valsets row))]
    {:results (mapv decode-row (take limit (drop offset documents))),
     :total (delay (count documents))}))

(defn create-context
  [corpus name desc]
  (swap! contexts assoc name
         {:description desc,
          :documents (map first (get-documents-raw corpus desc))}))
