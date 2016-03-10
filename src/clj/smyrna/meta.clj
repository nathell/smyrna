(ns smyrna.meta
  (:require [clojure.string :as string]))

(defn as-data-frame
  [[header & data]]
  (let [s (apply create-struct (map keyword header))]
    (map (partial apply struct s) data)))

(defn df-keys [df]
  (vec (keys (first df))))

(defn as-int [s]
  (if (empty? s)
    nil
    (Integer/parseInt s)))

(defn intable? [s]
  (try
    (as-int s)
    true
    (catch NumberFormatException _ false)))

(defn datable? [s]
  (or (empty? s)
      (re-find #"^[0-9]{4}-[0-9]{2}-[0-9]{2}$" s)))

(defn guess-type [values]
  (cond
    (#{["0" "1"] ["" "0" "1"]} values) :boolean
    (every? intable? values) :int
    (every? datable? values) :date
    :otherwise :string))

(defn as-dictionaries [[header & data]]
  (let [valsets (mapv (fn [i] (sort (distinct (map #(% i) data)))) (range (count header)))
        enums (map #(zipmap % (range)) valsets)]
    [header
     (mapv guess-type valsets)
     valsets
     (map (fn [row]
            (mapv #(%1 %2) enums row))
          data)]))

(defn row-key
  [[a b c _ d]]
  (string/join "/" [a b c d]))

(defn to-valseq
  [[header types valsets data]]
  (map (fn [row]
         (mapv #(nth %1 %2) valsets row))
       data))

(defn create-key-index
  [metadata]
  (into {}
        (map-indexed #(vector (row-key %2) %1) (to-valseq metadata))))

(defn get-header
  [[header types valsets]]
  (mapv (fn [column valset]
          (if (< (count valset) 100)
            [column (vec valset)]
            [column]))
        header valsets))
