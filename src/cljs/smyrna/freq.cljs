(ns smyrna.freq
  (:require [re-frame.core :as re-frame :refer [reg-event-db reg-event-fx reg-sub dispatch subscribe]]
            [reagent.core :as reagent]
            [smyrna.api :as api]
            [smyrna.table :refer [table]]
            [smyrna.task :refer [spinner]]
            [smyrna.utils :refer [reg-accessors reg-getters dispatch-value area-selector]]))

(reg-accessors :frequency-list-area)
(reg-getters :frequency-list-table :frequency-list-state)

(reg-event-db
 :set-frequency-list
 (fn [db [_ data]]
   (-> db
       (assoc :frequency-list-state :displaying)
       (assoc-in [:frequency-list-table :data] data))))

(reg-event-fx
 :update-frequency-list
 (fn [{db :db} _]
   {:api ["frequency-list"
          {:context (:frequency-list-area db)
           :corpus (:current-corpus db)}
          :set-frequency-list]
    :db (assoc db :frequency-list-state :in-progress)}))

(defn freq-table []
  (let [state (subscribe [:frequency-list-state])]
    (condp = @state
      :in-progress [spinner "Trwa generowanie listy frekwencyjnej..."]
      :displaying (table :frequency-list-table :height 600)
      nil)))

(defn downloader []
  (let [corpus (subscribe [:current-corpus])
        frequency-list-area (subscribe [:frequency-list-area])
        state (subscribe [:frequency-list-state])]
    (when (= @state :displaying)
      [:p [:a {:href (str "/frequency-list/" @corpus "/" @frequency-list-area)} "Pobierz jako CSV"]])))

(defn frequency-lists []
  [:div
   [area-selector :set-frequency-list-area]
   [:button {:on-click #(dispatch [:update-frequency-list])} "Pokaż"]
   [freq-table]
   [downloader]])
