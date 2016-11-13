(ns smyrna.freq
  (:require [re-frame.core :as re-frame :refer [register-handler path register-sub dispatch subscribe]]
            [reagent.core :as reagent]
            [smyrna.api :as api]
            [smyrna.table :refer [table]]
            [smyrna.task :refer [spinner]]
            [smyrna.utils :refer [register-accessors register-getter dispatch-value area-selector]]))

(register-accessors :frequency-list-area)
(register-getter :frequency-list-table)
(register-getter :frequency-list-state)

(register-handler :set-frequency-list
                  (fn [state [_ data]]
                    (-> state
                        (assoc :frequency-list-state :displaying)
                        (assoc-in [:frequency-list-table :data] data))))

(register-handler :update-frequency-list
                  (fn [state _]
                    (api/call "frequency-list" {:context (:frequency-list-area state)
                                                :corpus (:current-corpus state)}
                              #(dispatch [:set-frequency-list %]))
                    (assoc state :frequency-list-state :in-progress)))

(defn freq-table []
  (let [state (subscribe [:frequency-list-state])]
    (fn render-freq-table []
      (condp = @state
        :in-progress [spinner "Trwa generowanie listy frekwencyjnej..."]
        :displaying (table :frequency-list-table :height 600)
        nil))))

(defn downloader []
  (let [corpus (subscribe [:current-corpus])
        frequency-list-area (subscribe [:frequency-list-area])]
    (fn render-downloader []
      [:p [:a {:href (str "/frequency-list/" @corpus "/" @frequency-list-area)} "Pobierz jako CSV"]])))

(defn frequency-lists []
  [:div
   [area-selector :set-frequency-list-area]
   [:button {:on-click #(dispatch [:update-frequency-list])} "Pokaż"]
   [freq-table]
   [downloader]])
