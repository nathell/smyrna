(ns smyrna.bitstream
  (:require [clojure.java.io :as io]
            [nio.core :as nio])
  (:import
   [java.io DataOutputStream]
   [java.nio ByteBuffer IntBuffer LongBuffer]))

(definterface IBitSource
  (^long nextBit [])
  (^long readUnary [])
  (^long readBinary [^long n])
  (^long position [])
  (scroll [^long i]))

(deftype LongBufferBitSource
    [^LongBuffer buffer ^{:volatile-mutable true, :tag long} current ^{:volatile-mutable true, :tag long} bit]
  smyrna.bitstream.IBitSource
  (nextBit [this]
    (let [res (bit-and (.get buffer current) (bit-shift-left 1 bit))]
      (if (= bit 0)
        (do
          (set! current (inc current))
          (set! bit 63))
        (set! bit (dec bit)))
      (if (= res 0) 0 1)))
  (readUnary [this]
    (loop [a-current current
           a-bits (inc bit)
           l (if (= bit 63) (.get buffer current) (bit-and (.get buffer current) (unchecked-dec (bit-shift-left 1 (inc bit)))))
           res (- bit 63)]
      (if (zero? l)
        (recur (inc a-current)
               64
               (.get buffer (inc a-current))
               (+ res 64))
        (let [n (Long/numberOfLeadingZeros l)]
          (set! current (if (= l 1) (inc a-current) a-current))
          (set! bit (if (= l 1) 63 (- 62 n)))
          (+ res (inc n))))))
  (readBinary [this n]
    (if (>= (inc bit) n)
      (let [res (bit-shift-right (.get buffer current) (- (inc bit) n))
            res (if (= n 64) res (bit-and res (dec (bit-shift-left 1 n))))
            new-bit (- bit n)]
        (if (= new-bit -1)
          (do
            (set! current (inc current))
            (set! bit 63))
          (set! bit new-bit))
        res)
      (let [part1-length (inc bit)
            part1 (.readBinary this part1-length)
            part2-length (- n part1-length)
            part2 (.readBinary this part2-length)]
        (+ (bit-shift-left part1 part2-length) part2))))
  (position [this]
    (+ (- 63 bit) (* 64 current)))
  (scroll [this i]
    (set! current (bit-shift-right i 6))
    (set! bit (- 63 (bit-and i 63)))))

(deftype VectorBitSource
    [v ^{:volatile-mutable true, :tag long} i]
  smyrna.bitstream.IBitSource
  (nextBit [this]
    (let [res (v i)]
      (set! i (inc i))
      res))
  (position [this] i)
  (scroll [this new-i] (set! i new-i)))

(defn bit-source
  [buf]
  (if (vector? buf)
    (VectorBitSource. buf 0)
    (LongBufferBitSource. buf 0 63)))

(definterface IBitSink
  (^long position [])
  (^void writeUnary [^long x])
  (^void writeBinary [^long x ^long n]))

(deftype FileBitSink
    [^DataOutputStream stream ^{:volatile-mutable true, :tag long} current ^{:volatile-mutable true, :tag long} bits]
  java.io.Closeable
  (close [this]
    (when (pos? bits)
      (.writeLong stream current))
    (.close stream))
  smyrna.bitstream.IBitSink
  (position [this]
    (+ (* 8 (.size stream)) bits))
  (writeUnary [this x]
    (loop [a-current current a-bits bits x x]
      (if (<= x (- 64 a-bits))
        (let [new-bits (+ a-bits x)
              new-current (bit-or a-current (bit-shift-left 1 (- 64 new-bits)))]
          (if (= new-bits 64)
            (do
              (.writeLong stream new-current)
              (set! current 0)
              (set! bits 0))
            (do
              (set! current new-current)
              (set! bits new-bits))))
        (do
          (.writeLong stream a-current)
          (recur 0 0 (- x (- 64 a-bits)))))))
  (writeBinary [this x n]
    (if (<= n (- 64 bits))
      (let [new-current (bit-or current (bit-shift-left x (- 64 bits n)))
            new-bits (+ bits n)]
        (if (= new-bits 64)
          (do
            (.writeLong stream new-current)
            (set! current 0)
            (set! bits 0))
          (do
            (set! current new-current)
            (set! bits new-bits))))
      (let [threshold (+ n bits -64)]
        (.writeBinary this (bit-shift-right x threshold) (- 64 bits))
        (.writeBinary this (bit-and x (dec (bit-shift-left 1 threshold))) threshold)))))

(defn file-bit-sink
  [f]
  (FileBitSink. (DataOutputStream. (io/output-stream f)) 0 0))

(defn read-gamma
  [^IBitSource bs]
  (let [p (.readUnary bs)
        n (.readBinary bs (dec p))]
    (+ n (bit-shift-left 1 (dec p)))))

(defn read-delta
  [^IBitSource bs]
  (let [p (read-gamma bs)
        n (.readBinary bs (dec p))]
    (+ n (bit-shift-left 1 (dec p)))))

(defn read-golomb
  [^IBitSource bs ^long b]
  (let [nb (- 63 (Long/numberOfLeadingZeros b))
        threshold (- (bit-shift-left 1 (inc nb)) b)
        q (dec (.readUnary bs))
        r (let [a (.readBinary bs nb)]
            (if (< a threshold) a (+ a a (.nextBit bs) (- threshold))))]
    (+ r (* q b) 1)))

(defn write-gamma
  [^IBitSink bs ^long x]
  (let [n (- 64 (Long/numberOfLeadingZeros x))]
    (.writeUnary bs n)
    (.writeBinary bs (- x (bit-shift-left 1 (dec n))) (dec n))))

(defn write-delta
  [^IBitSink bs ^long x]
  (let [n (- 64 (Long/numberOfLeadingZeros x))]
    (write-gamma bs n)
    (.writeBinary bs (- x (bit-shift-left 1 (dec n))) (dec n))))

(defn write-golomb
  [^IBitSink bs ^long x ^long b]
  (let [q (long (/ (dec x) b))
        r (- x (* q b) 1)
        nb (- 63 (Long/numberOfLeadingZeros b))
        threshold (- (bit-shift-left 1 (inc nb)) b)]
    (.writeUnary bs (inc q))
    (if (< r threshold)
      (.writeBinary bs r nb)
      (.writeBinary bs (+ r threshold) (inc nb)))))
