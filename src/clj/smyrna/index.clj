(ns smyrna.index
  (:require [smyrna.bitstream :as bitstream])
  (:import [smyrna.bitstream IBitSource]))

(defn optimal-b
  [freq n]
  (long (Math/ceil (/ (* (Math/log 2) n) freq))))

(defn decode-index-entry
  [s]
  (loop [n (dec (first s))
         s (next s)
         res [n]]
    (if (seq s)
      (recur (+ n (first s)) (next s) (conj res (+ n (first s))))
      res)))

(defn read-index-entry
  [^IBitSource bs n]
  (let [freq (bitstream/read-gamma bs)
        b (optimal-b freq n)]
    (decode-index-entry
     (mapv (fn [_] (bitstream/read-golomb bs b)) (range freq)))))

(defn read-index-offsets
  [^IBitSource bs n cnt]
  (vec
   (repeatedly cnt
               (fn []
                 (let [pos (.position bs)]
                   (read-index-entry bs n)
                   pos)))))
