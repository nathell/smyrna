(ns smyrna.freq
  (:require [re-frame.core :as re-frame :refer [register-handler path register-sub dispatch subscribe]]
            [reagent.core :as reagent]
            [smyrna.api :as api]
            [smyrna.table :refer [table]]
            [smyrna.utils :refer [register-accessors register-getter dispatch-value area-selector]]))

(register-accessors :frequency-list-area)
(register-getter :frequency-list-table)

(register-handler :set-frequency-list
                  (fn [state [_ data]]
                    (assoc-in state [:frequency-list-table :data] data)))

(register-handler :update-frequency-list
                  (fn [state _]
                    (api/call "frequency-list" {:context (:frequency-list-area state)
                                                :corpus (:current-corpus state)}
                              #(dispatch [:set-frequency-list %]))
                    state))

(defn freq-table []
  (table :frequency-list-table :height 600))

(defn downloader []
  (let [frequency-list-area (subscribe [:frequency-list-area])]
    (fn render-downloader []
      [:p [:a {:href (str "/frequency-list/" @frequency-list-area)} "Pobierz jako CSV"]])))

(defn frequency-lists []
  [:div
   [area-selector :set-frequency-list-area]
   [:button {:on-click #(dispatch [:update-frequency-list])} "Pokaż"]
   [freq-table]
   [downloader]])
