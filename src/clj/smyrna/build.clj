(ns smyrna.build
  (:require [clojure.data.csv :as csv]
            [clojure.java.io :as io]
            [clojure.string :as string]
            [polelum.core :as polelum]
            [smyrna.html :as html]
            [smyrna.huffman :as huffman :refer [enumerate]]
            [smyrna.index :as index]
            [smyrna.bitstream :as bitstream]
            [smyrna.meta :as meta]
            [smyrna.fsa :as fsa]
            [smyrna.task :as task]
            [smyrna.corpus :refer [corpora-path]]
            [taoensso.timbre :refer [infof]])
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
  (let [f (-> metafile io/file)]
    (if (.isDirectory f)
      (-> f .getCanonicalFile (str "/"))
      (-> f .getCanonicalFile .getParent (str "/")))))

(defn absolute-file [prefix f]
  (let [f (io/file f)]
    (if (.isAbsolute f)
      f
      (io/file prefix f))))

(defn total-frequencies [metadata]
  (let [num-documents (count metadata)
        files (map :file metadata)]
    {:num-documents (count files)
     :freqs (apply merge-with + (map-indexed (fn [i f]
                                               (task/set-info (format "Trwa zliczanie częstości słów (wykonano %s%%)..." (int (* 100 (/ i num-documents)))))
                                               (-> f html/parse html/serialize-tree vec frequencies))
                                             files))}))

(defn save-dicts [outdir vocab]
  (task/set-info "Trwa generowanie słowników...")
  (let [vocab (filter vector? vocab)
        grouped (group-by first vocab)
        grouped (into {} (for [[k v] grouped] [k (sort-by second v)]))
        {:keys [attr word text tag]} grouped]
    (doseq [[k v] grouped]
      (fsa/serialize (fsa/build (map second v)) (format "%s/%s.fsa" outdir (name k))))
    (vec (concat word text attr tag [:nospace :end]))))

(defn dump-ints [out ints]
  (with-open [os (java.io.DataOutputStream. (io/output-stream out))]
    (doseq [x ints]
      (.writeInt os x))))

(defn lemmatize-words
  [tokens]
  (task/set-info "Trwa lematyzacja słów...")
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

(defn precompute-encoding [vocab freq-vals]
  (task/set-info "Trwa inicjalizacja procesu kompresji...")
  (huffman/precompute-encoding vocab freq-vals))

(defn read-metadata [metafile]
  (let [f (io/file metafile)
        prefix (corpus-dir metafile)]
    (if (.isDirectory f)
      (let [files (filter #(and (.isFile %) (re-find #"\.html?$" (string/lower-case (.getName %))))
                          (file-seq f))]
        (for [f files]
          {:plik (.getName f), :file (absolute-file prefix f)}))
      (->> (read-csv-df f)
           (map #(update % :file (partial absolute-file prefix)))))))

(defn read-metadata-table [metafile]
  (let [f (io/file metafile)]
    (if (.isDirectory f)
      (into [["file" "plik"]]
            (map (juxt :file :plik) (read-metadata f)))
      (csv/read-csv metafile))))

(defn build-corpus [metafile outdir]
  (task/set-info "Trwa wczytywanie metadanych...")
  (io/make-parents (str outdir "/a"))
  (let [prefix (corpus-dir metafile)
        metadata (read-metadata metafile)
        {:keys [num-documents freqs]} (total-frequencies metadata)
        vocab (save-dicts outdir (keys freqs))
        num-words (count (take-while #(= (first %) :word) vocab))
        freq-vals (map freqs vocab)
        {:keys [lemmata lemmatizer]} (lemmatize-words vocab)]
    (fsa/serialize (fsa/build lemmata) (str outdir "/lemmata.fsa"))
    (dump-ints (str outdir "/lemmatizer") lemmatizer)
    (let [{:keys [symbols numl first-code codes lengths index]} (precompute-encoding vocab freq-vals)]
      (dump-ints (str outdir "/symbols") symbols)
      (dump-ints (str outdir "/numl") numl)
      (dump-ints (str outdir "/1stcode") first-code)
      (task/set-info "Trwa zapisywanie metadanych...")
      (with-open [f (io/writer (java.util.zip.GZIPOutputStream. (java.io.FileOutputStream. (str outdir "/meta.edn.gz"))))
                  f2 (io/writer (java.util.zip.GZIPOutputStream. (java.io.FileOutputStream. (str outdir "/paths.edn.gz"))))]
        (binding [*out* f]
          (prn (meta/as-dictionaries (meta/drop-columns #{"file"} (read-metadata-table metafile)))))
        (binding [*out* f2]
          (prn (mapv (comp str :file) metadata))))
      (task/set-info "Trwa budowanie obrazu korpusu...")
      (with-open [corpus-image (bitstream/file-bit-sink (str outdir "/image"))
                  offset-file (java.io.DataOutputStream. (io/output-stream (str outdir "/offset")))]
        (let [inv (reduce (fn [inv [i entry]]
                            (let [tokens (-> entry :file html/parse html/serialize-tree)]
                              (huffman/do-encode tokens corpus-image codes lengths index)
                              (.writeInt offset-file (.position corpus-image))
                              (let [words (filter #(< % num-words) (map index tokens))
                                    lemmata (distinct (map lemmatizer words))]
                                (into inv (map #(+ i (bit-shift-left % 32)) lemmata)))))
                          (vector-of :long)
                          (map-indexed vector metadata))
              _ (task/set-info "Trwa tworzenie indeksu odwrotnego...")
              ^longs arr (doto (into-array Long/TYPE inv) Arrays/sort)]
          (write-index (str outdir "/index") arr num-documents))))))

(defn temp-dir
  [prefix]
  (.toFile (java.nio.file.Files/createTempDirectory prefix (into-array java.nio.file.attribute.FileAttribute []))))

(defn slurp-bytes
  "Slurp the bytes from a slurpable thing"
  [x]
  (with-open [out (java.io.ByteArrayOutputStream.)]
    (io/copy (io/input-stream x) out)
    (.toByteArray out)))

(defn crc32
  [f]
  (let [crc (java.util.zip.CRC32.)]
    (.update crc (slurp-bytes f))
    (.getValue crc)))

(defn pack
  [dir out]
  (with-open [f (java.util.zip.ZipOutputStream. (io/output-stream out))]
    (.setMethod f java.util.zip.ZipOutputStream/STORED)
    (doseq [el (.listFiles (io/file dir))
            :let [entry (java.util.zip.ZipEntry. (.getName el))
                  sz (.length el)]]
      (.setSize entry sz)
      (.setCompressedSize entry sz)
      (.setCrc entry (crc32 el))
      (.putNextEntry f entry)
      (with-open [src (io/input-stream el)]
        (io/copy src f)))))

(defn build-corpus-file
  [metafile out]
  (let [dir (temp-dir "corpus")]
    (build-corpus metafile dir)
    (pack dir out)))

(defn build
  [corpus-name file]
  (let [out-path (str corpora-path "/" corpus-name ".smyrna")]
    (io/make-parents out-path)
    (build-corpus-file file out-path)))
