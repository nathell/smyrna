(ns smyrna.morf
  (:import (morfologik.stemming PolishStemmer WordData)))

(defn lookup [word]
  (let [data (.lookup (PolishStemmer.) word)]
    (map (fn [x] {:word (str (.getWord x)),
                  :stem (str (.getStem x)),
                  :tag (str (.getTag x))})
         data)))