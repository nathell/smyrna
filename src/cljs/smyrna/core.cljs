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
            [smyrna.api :as api]))

(def meta-location "/meta")

;; State

(def initial-state
  {:tab 1,
   :document-filter {:page 0, :rows-per-page 10, :filters {}},
   :frequency-list-table {:columns {:leksem {:title "Leksem", :width 300}
                                    :frekwencja {:title "Frekwencja", :width 200}}
                          :shown-columns [:leksem :frekwencja]}})

(register-handler :initialize
  (fn [state _]
    (corpora/get-corpora)
    (corpora/get-files "/")
    (merge state initial-state)))

(register-accessors :tab :modal)

(register-handler :set-metadata (fn [state [_ corpus-info]] (merge state corpus-info)))

(defn tabbar [& labels-and-components]
  (let [tab (subscribe [:tab])
        modal (subscribe [:modal])
        current-corpus (subscribe [:current-corpus])
        pairs (partition 2 labels-and-components)
        labels (map first pairs)
        components (map second pairs)]
    (fn tabbar-render []
      [:div {:class (if @modal "nav-container modal-disabled" "nav-container")}
       [:ul {:class "nav"}
        (doall
         (for [[n label] (map-indexed vector labels)]
           (let [component (nth components n)
                 disabled? (and (not @current-corpus) (>= n 2))]
             [:li {:key (str "navbar-" n),
                   :class (str "navbar-" n " " (if (= n @tab) "item selected" "item"))}
              [:a {:href "#"
                   :class (if disabled? "disabled" "")
                   :on-click #(cond
                                disabled? nil
                                (vector? component) (dispatch [:set-tab n])
                                :otherwise (component))} label]])))
        [:li {:class "glue"}]
        [:li {:class "corpus-selector-container"} [corpora/corpus-selector]]]
       [:div {:class "tab"}
        (nth components @tab)]])))

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

(defn root []
  [:div
   [modal-box]
   [tabbar
    "Smyrna" #(dispatch [:set-modal about])
    "Korpusy" [corpora]
    "Wyszukiwanie" [document-table]
    "Dokumenty" [document-browser]
    "Chmury słów" [wordcloud]
    "Listy frekwencyjne" [frequency-lists]]])

(defn mount-root []
  (dispatch-sync [:initialize])
  (reagent/render [root] (.getElementById js/document "app")))

(defn init! []
  (mount-root))
