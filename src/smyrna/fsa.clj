(ns smyrna.fsa
  (:require [clojure.java.io :as io])
  (:import [morfologik.fsa.builders FSABuilder CFSA2Serializer]))

(defn build
  [words]
  (FSABuilder/build (map (memfn getBytes) words)))

(defn serialize
  [fsa out]
  (with-open [os (io/output-stream out)]
    (.serialize (CFSA2Serializer.) fsa os)))

(defn strings
  [fsa]
  (let [get-str (fn [^java.nio.ByteBuffer bb]
                  (let [o (.arrayOffset bb)]
                    (String. (.array bb) (+ o (.position bb)) (+ o (.remaining bb)))))]
    (map get-str fsa)))
