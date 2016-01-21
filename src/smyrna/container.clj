(ns smyrna.container
  (:require [nio.core :as nio])
  (:import [java.nio ByteBuffer]))

;; (def b (nio/mmap "/home/nathell/projects/corpus-java/corpus/korpus.zip"))
;; (nio/set-byte-order! b :little-endian)

(defn scroll [^ByteBuffer b ^long x]
  (.position b (+ (.position b) x)))

(defn read-subarray [^ByteBuffer b ^long size]
  (let [arr (byte-array size)]
    (.get b arr 0 size)
    arr))

(defn has-more?
  ([^ByteBuffer b] (has-more? b 1))
  ([^ByteBuffer b ^long i]
   (<= (+ (.position b) i) (.limit b))))

(defn read-entry [^ByteBuffer b]
  (when (has-more? b 4)
    (let [signature (.getInt b)]
      (when (= signature 0x04034b50)
        (let [_ (scroll b 18)
              size (.getInt b)
              name-length (.getShort b)
              extra-length (.getShort b)
              name (read-subarray b name-length)
              _ (scroll b extra-length)
              pos (.position b)
              _ (scroll b size)]
          [(String. name) {:offset pos, :length size}])))))

(defn read-entries [^ByteBuffer b]
  (.rewind b)
  (into {} (take-while identity (repeatedly #(read-entry b)))))
