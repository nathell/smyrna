(ns smyrna.core
  (:use clojure.test
        [clojure.set :only [union]])
  (:require [smyrna.morf :as morf]
            [clojure.string :as string]
            [pl.danieljanus.tagsoup :as tagsoup]))

(defn create-inverted-index [freqs]
  (apply merge-with union (map #(zipmap (keys %1) (repeat #{%2})) freqs (iterate inc 0))))

(defn remove-scripts [node]
  (if (vector? node)
    (vec (map remove-scripts (filter #(not (and (vector? %) (= (tagsoup/tag %) :script))) node)))
    node))

(defn strings [node]
  (->> node remove-scripts flatten (filter string?)))

(defn index-fileset [fileset]
  (let [documents (map #(->> % tagsoup/parse strings (string/join " ")) fileset)
        lemma-frequencies (vec (map morf/lemma-frequencies documents))
        word-frequencies (vec (map morf/word-frequencies documents))]
    {:files (vec fileset)
     :lemma-frequencies lemma-frequencies
     :word-frequencies word-frequencies
     :lemma-global-frequency (apply merge-with + lemma-frequencies)
     :word-global-frequency (apply merge-with + word-frequencies)
     :lemma-index (create-inverted-index lemma-frequencies)
     :word-index (create-inverted-index word-frequencies)}))

(defn- highlight-query [node query]
  (cond (vector? node) [(vec (apply concat (take 2 node) (map #(highlight-query % query) (tagsoup/children node))))]
        (string? node) (let [words (morf/tokenize node true)
                             len (.length node)
                             filtered (filter #(morf/word-matches? query (first %)) words)
                             substrings (map (fn [[start end]] (.substring node start end)) (map first (partition 1 2 (partition 2 1 (concat (apply concat [0] (map rest filtered)) [len])))))
                             spans (map (fn [[_ start end]] [:span {:class "smyrna-match"} (.substring node start end)]) filtered)]
                         (remove #(= % "") (interleave substrings (concat spans [""]))))))

(defn highlight [node query]
  (first (highlight-query (remove-scripts node) query)))

(deftest test-highlight
  (is (= (highlight [:html {}
                           [:body {}
                            [:p {} "to jest kawałek Akapitu"]
                            [:p {} "ten tekst zostaje niezmieniony"]
                            [:div {}
                             [:p {} "a ten akapit jest akapitem zagnieżdżonym"]]]]
                          "akapit")
          [:html {}
           [:body {}
            [:p {} "to jest kawałek " [:span {:class "smyrna-match"} "Akapitu"]]
            [:p {} "ten tekst zostaje niezmieniony"]
            [:div {}
             [:p {} "a ten " [:span {:class "smyrna-match"} "akapit"] " jest " [:span {:class "smyrna-match"} "akapitem"] " zagnieżdżonym"]]]])))