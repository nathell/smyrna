(ns smyrna.core
  (:require [re-frame.core :as re-frame :refer [register-handler path register-sub dispatch dispatch-sync subscribe]]
            [reagent.core :as reagent]
            [smyrna.utils :refer [register-accessors dispatch-value]]
            [smyrna.document-table :refer [document-table]]
            [smyrna.wordcloud :refer [wordcloud]]
            [smyrna.freq :refer [frequency-lists]]
            [smyrna.api :as api]))

(def meta-location "/meta")

;; State

(def initial-state
  {:tab 0,
   :document-filter {:page 0, :rows-per-page 10, :filters {}},
   :frequency-list-offset 0,
   :frequency-list-limit 25})

(defn load-metadata
  []
  (api/call "get-corpus-info" {} #(dispatch [:set-metadata %])))

(register-handler :initialize
  (fn [state _]
    (load-metadata)
    (dispatch [:refresh-table])
    (merge state initial-state)))

(register-accessors :tab)

(register-handler :set-metadata (fn [state [_ corpus-info]] (merge state corpus-info)))

(defn tabbar [& labels-and-components]
  (let [tab (subscribe [:tab])
        pairs (partition 2 labels-and-components)
        labels (map first pairs)
        components (map second pairs)]
    (fn tabbar-render []
      [:div {:class "nav-container"}
       [:ul {:class "nav"}
        (doall
         (for [[n label] (map-indexed vector labels)]
           [:li {:key (str "navbar-" n),
                 :class (if (= n @tab) "item selected" "item")}
            [:a {:href "#" :on-click #(dispatch [:set-tab n])} label]]))]
       (nth components @tab)])))

(defn document-browser []
  (let [browsed-document (subscribe [:browsed-document])
        document-filter (subscribe [:document-filter])]
    (fn render-document-browser []
      [:div {:class "nav-container"}
       [:h1 "Dokument"]
       (if-let [link @browsed-document]
         [:iframe {:width "100%" :height "50%" :src
                   (if-let [phrase (:phrase @document-filter)]
                     (str "/highlight/" phrase "/" link)
                     (str "/corpus/" link))}]
         [:h2 "Brak dokumentu do wyświetlenia."])])))

(defn root []
  [tabbar
   "Wyszukiwanie" [document-table]
   "Dokumenty" [document-browser]
   "Chmury słów" [wordcloud]
   "Listy frekwencyjne" [frequency-lists]])

(defn mount-root []
  (dispatch-sync [:initialize])
  (reagent/render [root] (.getElementById js/document "app")))

(defn init! []
  (mount-root))
