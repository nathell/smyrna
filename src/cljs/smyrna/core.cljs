(ns smyrna.core
  (:require [re-frame.core :as re-frame :refer [register-handler path register-sub dispatch dispatch-sync subscribe]]
            [reagent.core :as reagent]
            [smyrna.task :as task]
            [smyrna.utils :refer [register-accessors register-getter dispatch-value]]
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

(register-handler :initialize
  (fn [state _]
    (corpora/get-corpora)
    (corpora/get-files "/")
    (merge state initial-state)))

(register-accessors :tab :modal)

(register-handler :set-corpora-list (fn [state [_ corpora]]
                                      (assoc-in state [:corpora-table :data] corpora)))

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
    (fn render-modal-box []
      [:div {:class (if @modal "modal-dialog modal-visible" "modal-dialog")}
       (when @modal
         [:div
          [:a {:class "close" :href "#" :on-click #(dispatch [:set-modal nil])} "✖"] [@modal]])])))

(defn main-tab-disabled?
  []
  (let [current-corpus (subscribe [:current-corpus])]
    (fn [n]
      (and (not @current-corpus) (>= n 2)))))

(defn root []
  (let [modal (subscribe [:modal])]
    (fn render-root []
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
         :disabled-item? (main-tab-disabled?)]]])))

(defn mount-root []
  (dispatch-sync [:initialize])
  (reagent/render [root] (.getElementById js/document "app")))

(defn init! []
  (mount-root))
