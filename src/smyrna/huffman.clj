(ns smyrna.huffman
  (:require [smyrna.bitstream :as bitstream]
            [clojure.data.csv :as csv]
            [clojure.java.io :as io])
  (:import [java.util Arrays]
           [java.nio IntBuffer]
           [smyrna.bitstream IBitSink IBitSource]))

(defn extendv
  ([v] (extendv v 64 0))
  ([v n x]
   (let [l (count v)]
     (if (> l n)
       (throw (Exception.))
       (into v (repeat (- n l) x))))))

(defn canonical-code [code-lengths]
  (let [numl (frequencies code-lengths)
        max-length (apply max code-lengths)
        first-code (loop [first-code (list 0)
                          i (dec max-length)]
                     (if (= i 0)
                       (vec first-code)
                       (recur (conj first-code (long (/ (+ (first first-code) (numl (inc i) 0)) 2)))
                              (dec i))))
        {:keys [symbols codes]} (loop [next-code first-code
                                       symbols (vec (repeat max-length []))
                                       codes []
                                       code-lengths code-lengths
                                       i 0]
                                  (if-let [x (first code-lengths)]
                                    (recur
                                     (update-in next-code [(dec x)] inc)
                                     (update-in symbols [(dec x)] conj i)
                                     (conj codes (next-code (dec x)))
                                     (next code-lengths)
                                     (inc i))
                                    {:symbols (reduce into symbols) :codes codes}))]
    {:numl (extendv (vec (for [i (range 1 (inc (apply max (keys numl))))] (numl i 0)))), :first-code (extendv first-code), :codes codes, :symbols symbols}))

(defn down-heap [^longs heap ^long i len]
  (let [v (aget heap i)]
    (loop [i i]
      (let [[nx vm] (if (and (<= (+ i i) len)
                             (< (aget heap (aget heap (+ i i))) (aget heap v)))
                      [(+ i i) (aget heap (+ i i))] [i v])
            [nx vm] (if (and (<= (+ i i 1) len)
                             (< (aget heap (aget heap (+ i i 1))) (aget heap vm)))
                      [(+ i i 1) (aget heap (+ i i 1))] [nx vm])]
        (if (< i nx)
          (do
            (aset-long heap i (aget heap nx))
            (recur (long nx)))
          (do
            (aset-long heap i v)
            heap))))))

(defn make-heap [heap len]
  (doseq [i (range (bit-shift-right len 1) 0 -1)]
    (down-heap heap i len))
  heap)

(defn code-lengths [freqs]
  (let [len (count freqs)
        ^longs a (into-array Long/TYPE (concat [0] (range (inc len) (inc (* 2 len))) freqs))]
    (make-heap a len)
    (doseq [h (range len 1 -1)]
      (let [h (int h)
            m1 (aget a 1)
            _ (aset-long a 1 (aget a h))
            h (dec h)
            _ (down-heap a 1 h)
            m2 (aget a 1)]
        (aset-long a (inc h) (+ (aget a m1) (aget a m2)))
        (aset-long a 1 (inc h))
        (aset-long a m1 (inc h))
        (aset-long a m2 (inc h))
        (down-heap a 1 h)))
    (aset-long a 2 0)
    (doseq [i (range 3 (+ len len 1))]
      (aset-long a i (inc (aget a (aget a i)))))
    (vec (Arrays/copyOfRange a (inc len) (count a)))))

(defn do-encode
  [s ^IBitSink out codes lengths index]
  (doseq [sym s :let [i (index sym)]]
    (.writeBinary out (codes i) (lengths i))))

(defn precompute-encoding
  [syms counts]
  (let [lengths (code-lengths counts)]
    (assoc (canonical-code lengths)
      :lengths lengths
      :index (zipmap syms (range)))))

(defn encode
  ([s ^IBitSink out] (encode s out (frequencies s)))
  ([s ^IBitSink out freqs] (encode s out (keys freqs) (vals freqs)))
  ([s ^IBitSink out syms counts]
   (let [{:keys [codes lengths index]} (precompute-encoding syms counts)]
     (do-encode s out codes lengths index))))

(defn int-buffer
  [v]
  (IntBuffer/wrap (into-array Integer/TYPE (map int v))))

(defn decode-symbol
  [^IBitSource bs ^IntBuffer numl ^IntBuffer first-code ^IntBuffer symbols]
  (loop [n (.nextBit bs)
         i 0
         ofs 0]
    (if (>= n (.get first-code i))
      (.get symbols (+ ofs n (- (.get first-code i))))
      (recur (+ n n (.nextBit bs))
             (inc i)
             (+ ofs (.get numl i))))))
