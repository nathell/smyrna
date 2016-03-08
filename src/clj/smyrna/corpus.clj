(ns smyrna.corpus
  (:require [nio.core :as nio]
            [clojure.java.io :as io]
            [clojure.data.csv :as csv]
            [clojure.edn :as edn]
            [clojure.string :as string]
            [smyrna.bitstream :as bitstream]
            [smyrna.container :as container]
            [smyrna.huffman :as huffman :refer [enumerate]]
            [smyrna.fsa :as fsa]
            polelum)
  (:import [java.nio ByteBuffer IntBuffer]
           [java.util Arrays]
           [java.util.zip GZIPInputStream]
           [smyrna.bitstream IBitSource IBitSink]))

;; XXX: merge with container/read-subarray
(defn buffer-part
  [^ByteBuffer buf {:keys [offset length]}]
  (let [^bytes arr (byte-array length)]
    (.position buf offset)
    (.get buf arr 0 length)
    arr))

(defn int-subbuffer
  [^ByteBuffer buf {:keys [offset length]}]
  (.position buf offset)
  (doto (.asIntBuffer buf)
    (.limit (/ length 4))))

(defn long-subbuffer
  [^ByteBuffer buf {:keys [offset length]}]
  (.position buf offset)
  (doto (.asLongBuffer buf)
    (.limit (/ length 8))))

(defn read-meta
  [arr]
  (-> arr io/input-stream GZIPInputStream. io/reader java.io.PushbackReader. edn/read))

(defn to-valseq
  [[header types valsets data]]
  (map (fn [row]
         (mapv #(nth %1 %2) valsets row))
       data))

(defn row-key
  [[a b c _ d]]
  (string/join "/" [a b c d]))

(defn locate-by-key
  [cmeta rkey]
  (first (keep-indexed #(if (= (row-key %2) rkey) %1 nil) (to-valseq cmeta))))

(defn open
  [f]
  (let [buf (nio/mmap f)]
    (nio/set-byte-order! buf :little-endian)
    (let [elems (container/read-entries buf)
          read-dict (fn [type]
                      (let [elem-name (format "%s.fsa" (name type))
                            elem (elems elem-name)]
                        (fsa/strings (fsa/read (buffer-part buf elem)))))
          read-token-dict (fn [type]
                            (mapv (partial vector type)
                                  (read-dict type)))
          dict-keys [:word :text :attr :tag]
          dicts (map read-token-dict dict-keys)
          raw-meta-fn #(buffer-part buf (elems "meta.edn.gz"))]
      (nio/set-byte-order! buf :big-endian)
      {:tokens (into (vec (apply concat dicts)) [:nospace :end])
       :counts (zipmap dict-keys (map count dicts))
       :raw-meta-fn raw-meta-fn
       :meta (read-meta (raw-meta-fn))
       :image (long-subbuffer buf (elems "image"))
       :index (long-subbuffer buf (elems "index"))
       :lemmata (read-dict :lemmata)
       :lemmatizer (int-subbuffer buf (elems "lemmatizer"))
       :offset (int-subbuffer buf (elems "offset"))
       :numl (int-subbuffer buf (elems "numl"))
       :first-code (int-subbuffer buf (elems "1stcode"))
       :symbols (int-subbuffer buf (elems "symbols"))})))

(defn take-while-global
  [pred coll]
  (lazy-seq
   (when (pred)
     (let [s (seq coll)]
       (when s
         (cons (first s) (take-while-global pred (rest s))))))))

(defn read-document [corpus i & {:keys [lookup], :or {lookup true}}]
  (let [^IBitSource bs (bitstream/bit-source (:image corpus))
        token (fn [] ((if lookup (:tokens corpus) identity) (huffman/decode-symbol bs (:numl corpus) (:first-code corpus) (:symbols corpus))))
        ^IntBuffer offset (:offset corpus)
        i (long i)
        start (if (zero? i) 0 (.get offset (dec i)))
        end (.get offset i)]
    (.scroll bs start)
    (vec (take-while-global #(< (.position bs) end) (repeatedly token)))))

(defn transform
  ([x] x)
  ([x f] (if f (f x) x))
  ([x f & fs] (apply transform (transform x f) fs)))

(defn deserialize-attrs [s]
  (let [[kv rst] (split-with #(and (vector? %) (#{:attr :text} (first %))) s)
        kv (map (fn [[type v]] (if (= type :attr) (keyword v) v)) kv)]
    [(apply hash-map kv) rst]))

(defn deserialize [s]
  (loop [s s
         tree []
         text (StringBuilder.)
         space? false]
    (let [this (first s)]
      (cond
       (= this :end) [(if (empty? text) tree (conj tree (str text))) (next s)]
       (= this :nospace) (recur (next s) tree text false)
       (= (first this) :word) (recur (next s) tree (.append text (if space? (str " " (second this)) (second this))) true)
       (= (first this) :tag) (let [[attrs rst] (deserialize-attrs (next s))
                                   [subtree rst] (deserialize rst)]
                               (recur rst
                                      (if (empty? text)
                                        (conj tree (into [(keyword (second this)) attrs] subtree))
                                        (conj tree (str text) (into [(keyword (second this)) attrs] subtree)))
                                      (StringBuilder.)
                                      false))
       :otherwise (first tree)))))

(defn plaintext [s]
  (loop [s s
         tags ()
         text (StringBuilder.)
         space? false]
    (cond
     (= (first s) :nospace) (recur (next s) tags text false)
     (and (vector? (first s)) (= (first (first s)) :word)) (recur (next s) tags (.append text (if space? (str " " (second (first s))) (second (first s)))) true)
     (= (first s) :end) (recur (next s) (next tags)
                               (cond
                                (= (first tags) "p") (.append text "\n\n")
                                (#{"br" "h1" "h2" "h3" "h4" "h5" "h6"} (first tags)) (.append text "\n")
                                :otherwise text) false)
     (= (first (first s)) :tag) (recur (next s) (conj tags (second (first s))) text false)
     (nil? (first s)) (str text)
     :otherwise (recur (next s) tags text false))))

(defn lemmatize-words
  [corpus]
  (let [words (take-while #(= (first %) :word) (:tokens corpus))
        lemmata (-> (map #(-> % second polelum/lemmatize) words) distinct sort vec)
        enum (enumerate lemmata)]
    {:lemmata lemmata,
     :lemmatizer (mapv (comp enum polelum/lemmatize second) words)}))

(defn num-documents
  [corpus]
  (let [^IntBuffer offset (:offset corpus)]
    (.limit offset)))

(defn rle-append
  [rle n]
  (if-not rle
    (list n)
    (let [f (first rle)]
      (cond
       (and (not (vector? f)) (= n (inc f))) (conj (next rle) [f n])
       (and (vector? f) (= n (inc (second f)))) (conj (next rle) [(first f) n])
       :otherwise (conj rle n)))))

(defn update-in!
  ([m [k & ks] f & args]
   (if ks
     (assoc! m k (apply update-in! (get m k) ks f args))
     (assoc! m k (apply f (get m k) args)))))

(defn rle-add-all!
  [v i seq]
  (reduce (fn [acc n] (update-in! acc [n] rle-append i))
          v seq))
