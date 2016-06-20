(ns smyrna.search
  (:require [clojure.string :as string]
            [clojure.set :refer [union intersection]]
            [smyrna.bitstream :as bitstream]
            [smyrna.corpus :as corpus]
            [smyrna.index :as index]
            [smyrna.meta :as meta]))

(defn search-lemma
  [corpus lemma]
  (when-not (= lemma -1)
    (let [bs (bitstream/bit-source (:index corpus))]
      (.scroll bs ((:index-offsets corpus) lemma))
      (index/read-index-entry bs (corpus/num-documents corpus)))))

(defn lemma
  [corpus word]
  (.indexOf (:lemmata corpus) word))

(defn phrase-lemmata
  [corpus phrase]
  (map (partial lemma corpus) (string/split phrase #" ")))

(defn search-word
  [corpus word]
  (search-lemma corpus (lemma corpus word)))

(defn search-phrase
  [corpus phrase within]
  (let [lemmata (phrase-lemmata corpus phrase)
        candidates (map (comp set (partial search-lemma corpus)) lemmata)
        candidates (if within (conj candidates (set within)) candidates)]
    (filter (partial corpus/contains-phrase? corpus lemmata)
            (sort (apply intersection candidates)))))

(defn highlight-doc
  [{:keys [tokens] :as corpus} doc phrase]
  (let [lemmata (phrase-lemmata corpus phrase)
        l (count lemmata)
        starts (set (corpus/phrase-positions corpus lemmata doc))
        ends (set (map (partial + l) starts))]
    (reduce (fn [acc [i segment]]
              ((comp ; order here is last-to-first!
                #(conj % (tokens segment))
                (if (starts i) #(into % [[:tag "span"] [:attr "class"] [:text "match"]]) identity)
                (if (ends i) #(conj % :end) identity))
               acc))
            [] (map-indexed vector doc))))

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

(defn get-context [corpus name]
  (or (:documents (@contexts name))
      (range (corpus/num-documents corpus))))

(defn get-documents-raw
  [corpus {:keys [phrase filters within]}]
  (let [context (get-context corpus within)
        documents (if (seq phrase)
                    (search-phrase corpus phrase context)
                    context)
        flt (compute-filter corpus filters)
        [_ _ valsets all-rows] (:meta corpus)]
    (remove nil?
            (map (fn [i row] (when (flt row) [i row]))
                 documents
                 (nths all-rows documents)))))

(defn get-documents
  [corpus {:keys [offset limit], :or {offset 0, limit 10}, :as q}]
  (let [documents (map second (get-documents-raw corpus q))
        [_ _ valsets _] (:meta corpus)
        decode-row (fn [row] (let [res (mapv #(nth %1 %2) valsets row)] (into [(meta/row-key res)] res)))]
    {:results (mapv decode-row (take limit (drop offset documents))),
     :total (delay (count documents))}))

(defn create-context
  [corpus name desc]
  (swap! contexts assoc name
         {:description desc,
          :documents (map first (get-documents-raw corpus desc))}))

(defn alnum? [w]
  (re-find #"[\p{L}0-9]" w))

(defn count-lexemes
  [{:keys [lemmata ^java.nio.IntBuffer lemmatizer counts] :as corpus} docs]
  (let [^longs arr (make-array Long/TYPE (count lemmata))
        ^long wc (:word counts)]
    (doseq [i docs
            :let [doc (corpus/read-document corpus i :lookup false)]
            ^long token doc
            :when (< token wc)
            :let [l (.get lemmatizer token)]]
      (aset arr l (inc (aget arr l))))
    arr))

(defn log-likelihoods
  [freqs freqs-ref]
  (let [total (apply + freqs)
        total-ref (apply + freqs-ref)]
    (mapv (fn [a b]
            (if (and (pos? a) (pos? b))
              (let [scale (/ (+ a b) (+ total total-ref))
                    e1 (* total scale)
                    e2 (* total-ref scale)]
                (* 2 (+ (* a (Math/log (/ a e1)))
                        (* b (Math/log (/ b e2))))))
              0))
          freqs freqs-ref)))

(def count-context
  (memoize (fn [corpus context-name]
             (count-lexemes corpus (get-context corpus context-name)))))

(defn frequency-list
  ([corpus context-name]
   (let [counts (count-context corpus context-name)]
     (filter (comp alnum? first)
             (sort-by second > (map vector (:lemmata corpus) counts)))))
  ([corpus context-name limit offset]
   (take limit (drop offset (frequency-list corpus context-name)))))

(defn compare-contexts
  [corpus c1 c2]
  (let [count1 (count-context corpus c1)
        count2 (count-context corpus c2)
        ll (log-likelihoods count1 count2)]
    (->> ll
         (map vector (:lemmata corpus))
         (sort-by second >)
         (take-while (comp pos? second))
         (take 200)
         vec)))
