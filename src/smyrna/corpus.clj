(ns smyrna.corpus
  (:require [nio.core :as nio]
            [smyrna.container :as container]))

(defn buffer-part
  [buf {:keys [offset length]}]
  (let [arr (byte-array length)]
    (-> buf (.position offset) (.get arr 0 length))
    arr))

(defn open
  [f]
  (let [buf (nio/mmap f)]
    (nio/set-byte-order! buf :little-endian)
    (let [elems (container/read-entries buf)]
      (buffer-part buf (elems "text.fsa")))))
