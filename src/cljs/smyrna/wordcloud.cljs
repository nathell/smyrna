(ns smyrna.wordcloud
  (:require-macros [reagent.ratom :refer [reaction]])
  (:require [reagent.core :as reagent]
            [re-frame.core :as re-frame :refer [register-handler path register-sub dispatch dispatch-sync subscribe]]
            [smyrna.api :as api]
            [smyrna.utils :refer [register-accessors dispatch-value area-selector]]))

(register-accessors :wordcloud-area :wordcloud-data)

(defn draw [words]
  (-> js/d3 (.select "#wcl") (.selectAll "svg") (.remove))
  (-> js/d3
      (.select "#wcl")
      (.append "svg")
      (.attr "width" 800)
      (.attr "height" 600)
      (.append "g")
      (.attr "transform" "translate(400,300)")
      (.selectAll "text")
      (.data words)
      (.enter)
      (.append "text")
      (.style "font-size" #(str (.-size %) "px"))
      (.style "font-weight" "bold")
      (.style "font-family" "Arial")
      (.attr "text-anchor" "middle")
      (.attr "transform" #(str "translate(" (.-x %) "," (.-y %) ")rotate(" (.-rotate %) ")"))
      (.text #(.-text %))))

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
        (.size #js [800 600])
        (.words w)
        (.padding 5)
        (.font "Arial")
        (.fontWeight "bold")
        (.fontSize #(.-size %))
        (.on "end" draw)
        (.start))))

(register-handler :update-wordcloud
                  (fn [state _]
                    (api/call "compare-contexts" [(:wordcloud-area state) nil] #(dispatch [:set-wordcloud-data %]))
                    state))

(defn displayer-render [data]
  (identity @data)
  [:div {:id "wcl"}])

(defn displayer []
  (let [data (subscribe [:wordcloud-data])]
    (reagent/create-class {:reagent-render (partial displayer-render data)
                           :component-did-mount (partial layout data),
                           :component-did-update (partial layout data)})))

(defn wordcloud []
  [:div
   [area-selector :set-wordcloud-area "[Wybierz obszar]"]
   [:button {:on-click #(dispatch [:update-wordcloud])} "Pokaż"]
   [displayer]])
