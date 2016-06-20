(ns smyrna.freq
  (:require [re-frame.core :as re-frame :refer [register-handler path register-sub dispatch subscribe]]
            [smyrna.api :as api]
            [smyrna.utils :refer [register-accessors register-getter dispatch-value area-selector]]))

(register-accessors :frequency-list-area :frequency-list :frequency-list-offset :frequency-list-limit)

(register-handler :update-frequency-list
                  (fn [state _]
                    (api/call "frequency-list" {:context (:frequency-list-area state),
                                                :offset (:frequency-list-offset state),
                                                :limit (:frequency-list-limit state)}
                              #(dispatch [:set-frequency-list %]))
                    state))

(defn table []
  (let [frequency-list (subscribe [:frequency-list])]
    (fn render-table []
      [:div
       (when (seq @frequency-list)
         [:table {:class "frequency-list"}
          [:tr [:th "Leksem"] [:th "Frekwencja"]]
          (for [[word count] @frequency-list]
            [:tr [:td word] [:td count]])])])))

(defn downloader []
  (let [frequency-list-area (subscribe [:frequency-list-area])]
    (fn render-downloader []
      [:p [:a {:href (str "/frequency-list/" @frequency-list-area)} "Pobierz jako CSV"]])))

(defn frequency-lists []
  [:div
   [area-selector :set-frequency-list-area]
   [:button {:on-click #(dispatch [:update-frequency-list])} "Pokaż"]
   [table]
   [downloader]])
