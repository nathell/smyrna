(ns smyrna.core
  (:require [re-frame.core :as re-frame :refer [reg-event-fx reg-event-db dispatch dispatch-sync subscribe]]
            [reagent.core :as reagent]
            [smyrna.task :as task]
            [smyrna.utils :refer [reg-accessors]]
            [smyrna.corpora :refer [corpora] :as corpora]
            [smyrna.document-table :refer [document-table]]
            [smyrna.wordcloud :refer [wordcloud]]
            [smyrna.freq :refer [frequency-lists]]
            [smyrna.table :refer [table]]
            [smyrna.tabbar :refer [tabbar]]
            [smyrna.api :as api]))

(def meta-location "/meta")

;; State

(def initial-state
  {:tab 1,
   :document-tab 0,
   :document-filter {:page 0, :rows-per-page 10, :filters {}},
   :current-filter {:page 0, :rows-per-page 10, :filters {}},
   :frequency-list-area "Cały korpus",
   :frequency-list-table {:columns {:leksem {:title "Leksem", :width 300}
                                    :frekwencja {:title "Frekwencja", :width 200}}
                          :column-order [:leksem :frekwencja]
                          :shown-columns [:leksem :frekwencja]}
   :corpora-table {:columns {:id {:title "ID", :width 0}
                             :name {:title "Nazwa korpusu", :width 300}
                             :num-documents {:title "Liczba dokumentów", :width 300}}
                   :column-order [:id :name :num-documents]
                   :shown-columns [:name :num-documents]}
   :document-table {:data []}})

(reg-accessors :tab :modal)

(reg-event-fx
 :initialize
 (fn [{db :db} _]
   {:db initial-state
    :dispatch-n [[:get-corpora] [:get-files "/"] [:get-task-info]]}))

(reg-event-db
 :set-corpora-list
 (fn [db [_ corpora]]
   (assoc-in db [:corpora-table :data] corpora)))

(defn about []
  [:div {:class "about"}
   [:h1 "Smyrna"]
   [:p "Prosty konkordancer dla języka polskiego, wersja 0.3"]
   [:p "Copyright © 2010–2016, " [:a {:target "_blank" :href "http://danieljanus.pl"} "Daniel Janus"]]
   [:p "Smyrna jest "
       [:a {:target "_blank" :href "http://pl.wikipedia.org/wiki/Wolne_Oprogramowanie"} "Wolnym Oprogramowaniem"]
       ", udostępnianym na zasadach "
       [:a {:target "_blank" :href "#"} "licencji MIT"]
       ". Wykorzystuje do działania również "
       [:a {:target "_blank" :href "#"} "inne biblioteki na wolnych licencjach"] "."]])

(defn modal-box
  []
  (let [modal (subscribe [:modal])]
    [:div {:class (if @modal "modal-dialog modal-visible" "modal-dialog")}
     (when @modal
       [:div
        [:a {:class "close" :href "#" :on-click #(dispatch [:set-modal nil])} "✖"] [@modal]])]))

(defn main-tab-disabled?
  []
  (let [current-corpus (subscribe [:current-corpus])]
    (fn [n]
      (and (not @current-corpus) (>= n 2)))))

(defn root []
  (let [modal (subscribe [:modal])]
    [:div {:class "fullsize"}
     [modal-box]
     [:div {:class (if @modal "modal-disabled" "fullsize")}
      [tabbar :tab
       ["Smyrna" #(dispatch [:set-modal about])
        "Korpusy" [corpora]
        "Wyszukiwanie" [document-table]
        "Chmury słów" [wordcloud]
        "Listy frekwencyjne" [frequency-lists]
        :glue nil
        :inline [corpora/corpus-selector]]
       :disabled-item? (main-tab-disabled?)]]]))

(defn mount-root []
  (dispatch-sync [:initialize])
  (reagent/render [root] (.getElementById js/document "app")))

(defn init! []
  (mount-root))
