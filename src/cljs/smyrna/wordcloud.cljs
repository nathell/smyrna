(ns smyrna.wordcloud
  (:require [reagent.core :as reagent]
            [re-frame.core :as re-frame :refer [reg-event-fx dispatch subscribe]]
            [smyrna.utils :refer [reg-accessors area-selector]]
            [smyrna.task :refer [spinner]]
            ["d3" :as d3]
            ["d3-cloud" :as cloud]))

(reg-accessors :wordcloud-area :wordcloud-data)

(defn wc-size []
  (let [el (aget (js/document.getElementsByClassName "wordcloud") 0)]
    [(.-clientWidth el) (.-clientHeight el)]))

(defn draw [words]
  (let [[w h] (wc-size)]
    (-> d3 (.select "#wcl") (.selectAll "svg") (.remove))
    (-> d3
        (.select "#wcl")
        (.append "svg")
        (.attr "width" w)
        (.attr "height" h)
        (.append "g")
        (.attr "transform" (str "translate(" (/ w 2) "," (/ h 2) ")"))
        (.selectAll "text")
        (.data words)
        (.enter)
        (.append "text")
        (.style "font-size" #(str (.-size %) "px"))
        (.style "font-weight" "bold")
        (.style "font-family" "Arial")
        (.attr "text-anchor" "middle")
        (.attr "transform" #(str "translate(" (.-x %) "," (.-y %) ")rotate(" (.-rotate %) ")"))
        (.text #(.-text %)))))

(defn sizes
  [ll]
  (let [words (map first ll)
        counts (map second ll)
        maxc (apply max counts)
        scaled (map #(/ % maxc) counts)
        sizes (map #(* 100 (js/Math.sqrt %)) scaled)]
    (zipmap words sizes)))

(defn layout [data]
  (let [w (clj->js (for [[word count] (sizes @data)] {:text word, :size count}))]
    (-> (cloud)
        (.size (clj->js (wc-size)))
        (.words w)
        (.padding 5)
        (.font "Arial")
        (.fontWeight "bold")
        (.fontSize #(.-size %))
        (.on "end" draw)
        (.start))))

(reg-event-fx
 :update-wordcloud
 (fn [{db :db} _]
   {:api ["compare-contexts"
          [(:wordcloud-area db) "Cały korpus" (:current-corpus db)]
          :set-wordcloud-data]
    :db (assoc db :wordcloud-data :in-progress)}))

(defn displayer-render [data]
  (identity @data)
  [:div {:id "wcl"}])

(defn actual-displayer []
  (let [data (subscribe [:wordcloud-data])]
    (reagent/create-class {:reagent-render (partial displayer-render data)
                           :component-did-mount (partial layout data),
                           :component-did-update (partial layout data)})))

(defn displayer []
  (let [data (subscribe [:wordcloud-data])]
    (if (= @data :in-progress)
      [spinner "Trwa generowanie chmury słów..."]
      [actual-displayer])))

(defn wordcloud []
  [:div {:class "wordcloud"}
   [area-selector :set-wordcloud-area "[Wybierz obszar]"]
   [:button {:on-click #(dispatch [:update-wordcloud])
             :disabled (let [area @(subscribe [:wordcloud-area])]
                         (or (nil? area) (= area "[Wybierz obszar]")))}
    "Pokaż"]
   [displayer]])
