(ns smyrna.build
  (:require [clojure.data.csv :as csv]
            [clojure.java.io :as io]
            [smyrna.html :as html]
            [smyrna.huffman :as huffman :refer [enumerate]]
            [smyrna.index :as index]
            [smyrna.bitstream :as bitstream]
            [smyrna.meta :as meta]
            [smyrna.fsa :as fsa]
            [taoensso.timbre :refer [infof]]
            [pl.danieljanus.tagsoup :as tagsoup])
  (:import [smyrna.bitstream IBitSink]
           [java.util Arrays]))

(defn read-csv-df
  "Reads a CSV as a data frame (seq of maps) from file."
  [f]
  (let [[header & data] (csv/read-csv (io/reader f))
        header-keys (map keyword header)
        structure (apply create-struct header-keys)]
    (map #(apply struct structure %) data)))

(defn frequencies-start
  [start coll]
  (persistent!
   (reduce (fn [counts x]
             (assoc! counts x (inc (get counts x 0))))
           (transient start) coll)))

(defn corpus-dir
  [metafile]
  (-> metafile io/file .getCanonicalFile .getParent (str "/")))

(defn total-frequencies [metafile]
  (let [prefix (corpus-dir metafile)
        files (map #(str prefix (:file %)) (read-csv-df metafile))]
    {:num-documents (count files)
     :freqs (apply merge-with + (map (comp frequencies vec html/serialize-tree tagsoup/parse) files))}))

(defn save-dicts [outdir vocab]
  (let [vocab (filter vector? vocab)
        grouped (group-by first vocab)
        grouped (into {} (for [[k v] grouped] [k (sort-by second v)]))
        {:keys [attr word text tag]} grouped]
    (doseq [[k v] grouped]
      (infof "Saving %s..." k)
      (fsa/serialize (fsa/build (map second v)) (format "%s/%s.fsa" outdir (name k))))
    (vec (concat word text attr tag [:nospace :end]))))

(defn dump-ints [out ints]
  (with-open [os (java.io.DataOutputStream. (io/output-stream out))]
    (doseq [x ints]
      (.writeInt os x))))

(defn lemmatize-words
  [tokens]
  (let [words (take-while #(= (first %) :word) tokens)
        lemmata (-> (map #(-> % second polelum/lemmatize) words) distinct sort vec)
        enum (enumerate lemmata)]
    {:lemmata lemmata,
     :lemmatizer (mapv (comp enum polelum/lemmatize second) words)}))

(defn words-only
  [doc]
  (map second
       (filter #(and (vector? %) (= (first %) :word)) doc)))

(defn diffs
  [x]
  (into [(inc (first x))]
        (map - (rest x) x)))

(defn write-index
  [out idx n]
  (let [mask (dec (bit-shift-left 1 32))]
    (with-open [^IBitSink bs (bitstream/file-bit-sink out)]
      (doseq [part (partition-by #(bit-shift-right % 32) idx)
              :let [part (map #(bit-and mask %) part)
                    cnt (count part)
                    b (index/optimal-b cnt n)]]
        (bitstream/write-gamma bs cnt)
        (doseq [n (diffs part)]
          (bitstream/write-golomb bs n b))))))

(defn build-corpus [metafile outdir]
  (io/make-parents (str outdir "/a"))
  (infof "Gathering unique tokens...")
  (let [prefix (corpus-dir metafile)
        {:keys [num-documents freqs]} (total-frequencies metafile)
        vocab (save-dicts outdir (keys freqs))
        num-words (count (take-while #(= (first %) :word) vocab))
        freq-vals (map freqs vocab)
        {:keys [lemmata lemmatizer]} (lemmatize-words vocab)]
    (fsa/serialize (fsa/build lemmata) (str outdir "/lemmata.fsa"))
    (dump-ints (str outdir "/lemmatizer") lemmatizer)
    (let [{:keys [symbols numl first-code codes lengths index]} (huffman/precompute-encoding vocab freq-vals)]
      (dump-ints (str outdir "/symbols") symbols)
      (dump-ints (str outdir "/numl") numl)
      (dump-ints (str outdir "/1stcode") first-code)
      (infof "Saving metadata...")
      (with-open [f (io/writer (java.util.zip.GZIPOutputStream. (java.io.FileOutputStream. (str outdir "/meta.edn.gz"))))]
        (binding [*out* f]
          (prn (meta/as-dictionaries (meta/drop-columns #{"file"} (csv/read-csv (io/reader metafile)))))))
      (infof "Building corpus image...")
      (with-open [corpus-image (bitstream/file-bit-sink (str outdir "/image"))
                  offset-file (java.io.DataOutputStream. (io/output-stream (str outdir "/offset")))]
        (let [inv (reduce (fn [inv [i entry]]
                            (let [f (str prefix (:file entry))
                                  tokens (-> f tagsoup/parse html/serialize-tree)]
                              (huffman/do-encode tokens corpus-image codes lengths index)
                              (.writeInt offset-file (.position corpus-image))
                              (let [words (filter #(< % num-words) (map index tokens))
                                    lemmata (distinct (map lemmatizer words))]
                                (into inv (map #(+ i (bit-shift-left % 32)) lemmata)))))
                          (vector-of :long)
                          (map-indexed vector (read-csv-df metafile)))
              _ (infof "Inverting index...")
              ^longs arr (doto (into-array Long/TYPE inv) Arrays/sort)]
          (write-index (str outdir "/index") arr num-documents))))))
