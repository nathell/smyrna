(ns smyrna.index
  (:require [smyrna.bitstream :as bitstream])
  (:import [smyrna.bitstream IBitSource IBitSink]))

(defn optimal-b
  [freq n]
  (long (Math/ceil (/ (* (Math/log 2) n) freq))))

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
                    b (optimal-b cnt n)]]
        (bitstream/write-gamma bs cnt)
        (doseq [n (diffs part)]
          (bitstream/write-golomb bs n b))))))

(defn read-index-entry
  [^IBitSource bs n]
  (let [freq (bitstream/read-gamma bs)
        b (optimal-b freq n)]
    (mapv (fn [_] (bitstream/read-golomb bs b)) (range freq))))

(defn read-index-offsets
  [^IBitSource bs n c]
  (take c (repeatedly (fn []
                        (let [pos (.position bs)]
                          (read-index-entry bs n)
                          pos)))))

(defn decode-index-entry
  [s]
  (loop [n (dec (first s))
         s (next s)
         res [n]]
    (if (seq s)
      (recur (+ n (first s)) (next s) (conj res (+ n (first s))))
      res)))
