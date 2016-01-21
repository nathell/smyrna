(ns smyrna.corpus
  (:require [nio.core :as nio]
            [clojure.java.io :as io]
            [clojure.data.csv :as csv]
            [smyrna.bitstream :as bitstream]
            [smyrna.container :as container]
            [smyrna.huffman :as huffman]
            [smyrna.fsa :as fsa])
  (:import [java.nio ByteBuffer]
           [java.util.zip GZIPInputStream]))

;; XXX: merge with container/read-subarray
(defn buffer-part
  [^ByteBuffer buf {:keys [offset length]}]
  (let [arr (byte-array length)]
    (-> buf (.position offset) (.get arr 0 length))
    arr))

(defn int-subbuffer
  [^ByteBuffer buf {:keys [offset length]}]
  (doto (.asIntBuffer (.position buf offset))
    (.limit (/ length 4))))

(defn long-subbuffer
  [^ByteBuffer buf {:keys [offset length]}]
  (doto (.asLongBuffer (.position buf offset))
    (.limit (/ length 8))))

(defn read-meta
  [arr]
  (-> arr io/input-stream GZIPInputStream. io/reader csv/read-csv))

(defn open
  [f]
  (let [buf (nio/mmap f)]
    (nio/set-byte-order! buf :little-endian)
    (let [elems (container/read-entries buf)
          read-dict (fn [type]
                      (let [elem-name (format "%s.fsa" (name type))
                            elem (elems elem-name)]
                        (map (partial vector type)
                             (fsa/strings (fsa/read (buffer-part buf elem))))))]
      (nio/set-byte-order! buf :big-endian)
      {:tokens (into (vec (mapcat read-dict [:word :text :attr :tag])) [:nospace :end]),
       :meta (read-meta (buffer-part buf (elems "meta.csv.gz")))
       :image (long-subbuffer buf (elems "image"))
       :offset (int-subbuffer buf (elems "offset"))
       :numl (int-subbuffer buf (elems "numl"))
       :first-code (int-subbuffer buf (elems "1stcode"))
       :symbols (int-subbuffer buf (elems "symbols"))})))

(defn read-document [corpus i]
  (let [bs (bitstream/bit-source (:image corpus))
        token (fn [] ((:tokens corpus) (huffman/decode-symbol bs (:numl corpus) (:first-code corpus) (:symbols corpus))))
        start (if (zero? i) 0 (.get (:offset corpus) (dec i)))
        end (.get (:offset corpus) i)]
    (.scroll bs start)
    (vec (take-while (fn [_] (<= (.position bs) end)) (repeatedly token)))))

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
