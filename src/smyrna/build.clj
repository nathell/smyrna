(ns smyrna.build
  (:require [clojure.data.csv :as csv]
            [clojure.java.io :as io]
            [smyrna.html :as html]
            [smyrna.huffman :as huffman]
            [smyrna.bitstream :as bitstream]
            [smyrna.fsa :as fsa]
            [pl.danieljanus.tagsoup :as tagsoup]))

(defn read-csv-df
  "Reads a CSV as a data frame (seq of maps) from file."
  [f]
  (let [[header & data] (csv/read-csv (io/reader f))
        header-keys (map keyword header)
        structure (apply create-struct header-keys)]
    (map #(apply struct structure %) data)))

(def prefix "/home/nathell/projects/p4/debate-clean/")

(defn frequencies-start
  [start coll]
  (persistent!
   (reduce (fn [counts x]
             (assoc! counts x (inc (get counts x 0))))
           (transient start) coll)))

(defn total-frequencies []
  (let [files (map #(str prefix (:file %)) (read-csv-df "/home/nathell/projects/p4/meta.csv"))]
    (apply merge-with + (map (comp frequencies vec html/serialize-tree tagsoup/parse) files))))

(defn save-dicts [vocab]
  (let [vocab (filter vector? vocab)
        grouped (group-by first vocab)
        grouped (into {} (for [[k v] grouped] [k (sort-by second v)]))
        {:keys [attr word text tag]} grouped]
    (doseq [[k v] grouped]
      (println "Saving" k)
      (fsa/serialize (fsa/build (map second v)) (format "%s.fsa" (name k))))
    (vec (concat word text attr tag [:nospace :end]))))

(defn dump-ints [out ints]
  (with-open [os (java.io.DataOutputStream. (io/output-stream out))]
    (doseq [x ints]
      (.writeInt os x))))

(defn build-corpus [freqs]
  (let [vocab (save-dicts (keys freqs))
        freq-vals (map freqs vocab)]
    (let [{:keys [symbols numl first-code codes lengths index]} (huffman/precompute-encoding vocab freq-vals)]
      (dump-ints "symbols" symbols)
      (dump-ints "numl" numl)
      (dump-ints "1stcode" first-code)
      (with-open [corpus-image (bitstream/file-bit-sink "image")
                  offset-file (java.io.DataOutputStream. (io/output-stream "offset"))]
        (doseq [entry (read-csv-df "/home/nathell/projects/p4/meta.csv")
                :let [f (str prefix (:file entry))]]
          (huffman/do-encode (-> f tagsoup/parse html/serialize-tree)
                             corpus-image codes lengths index)
          (.writeInt offset-file (.position corpus-image)))))))
