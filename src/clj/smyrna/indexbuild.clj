(ns smyrna.indexbuild
  (:require [smyrna.bitstream :as bitstream]
            [smyrna.index :as index]
            [smyrna.corpus :as corpus])
  (:import [smyrna.bitstream IBitSink]
           [java.util Arrays]))

(defn build-lemma-index
  [corpus {:keys [lemmata lemmatizer]}]
  (let [num-words (:word (:counts corpus))
        occurrences (reduce
                     (fn [acc i]
                       (let [words (filter #(< % num-words) (corpus/read-document corpus i :lookup false))
                             words-lemmatized (distinct (map lemmatizer words))]
                         (into acc (map #(+ i (bit-shift-left % 32)) words-lemmatized))))
                     (vector-of :long)
                     (range (corpus/num-documents corpus)))
        ^longs arr (into-array Long/TYPE occurrences)]
    (Arrays/sort arr)
    arr))

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
