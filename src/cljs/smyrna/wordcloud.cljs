(ns smyrna.wordcloud
  (:require-macros [reagent.ratom :refer [reaction]])
  (:require [reagent.core :as reagent]
            [re-frame.core :as re-frame :refer [register-handler path register-sub dispatch dispatch-sync subscribe]]
            [smyrna.api :as api]
            [smyrna.utils :refer [register-accessors dispatch-value area-selector]]
            [smyrna.task :refer [spinner]]
            cljsjs.d3))

(register-accessors :wordcloud-area :wordcloud-data)

(defn wc-size []
  (let [el (aget (js/document.getElementsByClassName "wordcloud") 0)]
    [(.-clientWidth el) (.-clientHeight el)]))

(defn draw [words]
  (let [[w h] (wc-size)]
    (-> js/d3 (.select "#wcl") (.selectAll "svg") (.remove))
    (-> js/d3
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
    (-> js/d3.layout
        (.cloud)
        (.size (clj->js (wc-size)))
        (.words w)
        (.padding 5)
        (.font "Arial")
        (.fontWeight "bold")
        (.fontSize #(.-size %))
        (.on "end" draw)
        (.start))))

(register-handler :update-wordcloud
                  (fn [state _]
                    (api/call "compare-contexts" [(:wordcloud-area state) "Cały korpus" (:current-corpus state)] #(dispatch [:set-wordcloud-data %]))
                    (assoc state :wordcloud-data :in-progress)))

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
    (fn render-displayer []
      (if (= @data :in-progress)
        [spinner "Trwa generowanie chmury słów..."]
        [actual-displayer]))))

(defn wordcloud []
  [:div {:class "wordcloud"}
   [area-selector :set-wordcloud-area "[Wybierz obszar]"]
   [:button {:on-click #(dispatch [:update-wordcloud])} "Pokaż"]
   [displayer]])
