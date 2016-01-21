(ns smyrna.bitstream
  (:require [clojure.java.io :as io]
            [nio.core :as nio])
  (:import
   [java.io DataOutputStream]
   [java.nio ByteBuffer IntBuffer LongBuffer]))

(definterface IBitSource
  (^long nextBit [])
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
