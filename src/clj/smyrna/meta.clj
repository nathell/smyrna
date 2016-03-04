(ns smyrna.meta)

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