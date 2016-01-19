(ns smyrna.bitstream
  (:require [clojure.java.io :as io]
            [nio.core :as nio])
  (:import
   [java.io DataOutputStream]
   [java.nio ByteBuffer LongBuffer]))

(definterface IBitSource
  (^long nextBit []))

(deftype LongBufferBitSource
    [^LongBuffer buffer ^{:volatile-mutable true, :tag long} current ^{:volatile-mutable true, :tag long} bit]
  smyrna.bitstream.IBitSource
  (nextBit [this]
    (let [res (bit-shift-right (bit-and (.get buffer current) (bit-shift-left 1 bit)) bit)]
      (if (= bit 0)
        (do
          (set! current (inc current))
          (set! bit 63))
        (set! bit (dec bit)))
      res)))

(defn bit-source
  [buf]
  (LongBufferBitSource. buf 0 63))

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

(with-open [bs (file-bit-sink "/tmp/test2")]
  (.writeBinary bs 21978 30)
  (.writeBinary bs 1928326 40))
