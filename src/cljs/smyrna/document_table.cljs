(ns smyrna.document-table
  (:require [clojure.walk :refer [postwalk]]
            [clojure.string :as string]
            [reagent.core :as reagent :refer [atom]]
            [re-frame.core :as re-frame :refer [reg-event-db reg-event-fx reg-sub dispatch dispatch-sync subscribe]]
            [smyrna.api :as api]
            [smyrna.utils :refer [reg-accessors reg-getters dispatch-value]]
            [smyrna.table :refer [table]]
            [smyrna.tabbar :refer [tabbar]]))

(defn filter-params [{:keys [page rows-per-page filters phrase within]}]
  {:offset (* page rows-per-page),
   :limit rows-per-page,
   :filters filters,
   :phrase phrase,
   :within within})

(defn toggle [set el]
  ((if (set el) disj conj) set el))

(defn toggle-nilable [s el n]
  (toggle (or s (set (range n))) el))

(reg-accessors :new-area :browsed-document :browsed-document-num :document-tab :advanced)
(reg-getters :document-filter :current-filter :metadata :contexts :document-table)
(reg-sub :vignette #(-> %1 :custom :vignette))
(reg-sub :phrase #(-> %1 :document-filter :phrase))

(reg-event-db
 :set-documents
 (fn [db [_ documents]]
   (assoc-in db [:document-table :data] documents)))

(reg-event-db
 :set-phrase
 (fn [db [_ value]]
   (assoc-in db [:document-filter :phrase] value)))

(reg-event-db
 :set-filter
 (fn [db [_ column value]]
   (if (empty? value)
     (update-in db [:document-filter :filters] dissoc column)
     (assoc-in db [:document-filter :filters column] value))))

(reg-event-db
 :checkboxes-set-all
 (fn [db [_ column]]
   (update-in db [:document-filter :filters] dissoc column)))

(reg-event-db
 :checkboxes-clear-all
 (fn [db [_ column]]
   (assoc-in db [:document-filter :filters column] #{})))

(reg-event-db
 :checkboxes-toggle
 (fn [db [_ column value cnt]]
   (update-in db [:document-filter :filters column] toggle-nilable value cnt)))

(reg-event-db
 :browse-basic
 (fn [db [_ n documents]]
   (if (seq documents)
     (assoc db
            :browsed-document-num n
            :browsed-document (first documents)
            :document-tab 1)
     db)))

(reg-event-db
 :browse
 (fn [db [_ row document]]
   (let [df (:document-filter db)]
     (assoc db
            :browsed-document-num (+ row (* (:page df) (:rows-per-page df)))
            :browsed-document document
            :document-tab 1))))

(reg-event-db
 :set-search-context
 (fn [db [_ value]]
   (assoc-in db [:document-filter :within] value)))

(reg-event-fx
 :refresh-table
 (fn [{db :db} _]
   {:api ["get-documents"
          (assoc (filter-params (:document-filter db))
                 :corpus (:current-corpus db))
          :set-documents]
    :db (assoc db :current-filter (:document-filter db))}))

(reg-event-fx
 :reset-filters
 (fn [{db :db} _]
   {:db (assoc db :document-filter {:page 0, :rows-per-page 10, :filters {}})
    :dispatch [:refresh-table]}))

(reg-event-fx
 :move-page
 (fn [{db :db} [_ delta]]
   {:db (update-in db [:document-filter :page] + delta)
    :dispatch [:refresh-table]}))

(reg-event-fx
 :browse-num
 (fn [{db :db} [_ n]]
   {:api ["get-documents"
          (assoc (filter-params (:document-filter db))
                 :corpus (:current-corpus db)
                 :offset n :limit 1)
          :browse-basic n]}))

(reg-event-fx
 :create-area
 (fn [{db :db} [_ n]]
   (let [params (filter-params (:document-filter db))
         name (:new-area db)]
     {:api ["create-context" {:name name, :description params, :corpus (:current-corpus db)}]
      :db (update-in db [:contexts] conj [name params])})))

(defn area-creator []
  [:div
   [:input {:type "text", :placeholder "Wpisz nazwę obszaru", :on-change (dispatch-value :set-new-area)}]
   [:button {:on-click #(do (dispatch [:create-area])
                            (dispatch [:set-modal nil]))}
    "Utwórz obszar"]])

(defn search []
  (let [contexts (subscribe [:contexts])
        phrase (subscribe [:phrase])
        advanced (subscribe [:advanced])]
    [:form {:class "search" :on-submit #(do (.preventDefault %1) (dispatch [:refresh-table]) false)}
     [:div {:class "group"}
      [:input {:class "phrase", :type "text", :autoFocus true, :placeholder "Wpisz szukaną frazę", :on-change (dispatch-value :set-phrase), :value @phrase}]]
     [:div {:class "group"}
      [:input {:type "submit", :value "Szukaj", :class "search-button"}]
      [:a {:class "advanced" :href "#" :on-click #(dispatch [:set-advanced (not @advanced)])} (if @advanced "« Ukryj zaawansowane opcje" "Pokaż zaawansowane opcje »")]]
     (if @advanced
       [:div {:class "group"}
        "Obszar: "
        (into [:select {:on-change (dispatch-value :set-search-context)}
               [:option "Cały korpus"]]
              (for [[opt _] @contexts]
                [:option {:value opt, #_:selected #_(= (:within @search-params) opt)} opt]))])
     (if @advanced
       [:div {:class "group"}
        [:button {:on-click #(dispatch [:set-modal area-creator])} "Utwórz obszar"]
        [:button {:on-click #(dispatch [:reset-filters])} "Resetuj filtry"]])]))

(defn document-summary
  []
  (let [{:keys [phrase within filters page]} @(subscribe [:current-filter])]
    [:p.summary

     (if (seq phrase)
       [:span "Dokumenty zawierające frazę „" [:b phrase] "”"]
       "Wszystkie dokumenty")
     " w "
     (if (or (nil? within) (= within "Cały korpus"))
       "całym korpusie"
       [:span "obszarze " [:b [:i within]]])
     [:br]
     "Strona " (inc page)
     [:br]
     (if (seq filters)
       [:span
        "Aktywne filtry: " [:i (string/join ", " (keys filters))]
        " ("
        [:a {:href "#" :on-click #(dispatch [:reset-filters])} "resetuj"]
        ")"]
       "Brak aktywnych filtrów")

     ]))

(defn pagination []
  (let [document-filter (subscribe [:document-filter])]
    [:div {:class "pagination", :style {:width @(subscribe [:table-width])}}
     [:button (if (> (:page @document-filter) 0)
                {:on-click #(dispatch [:move-page -1])}
                {:disabled true}) "<<"]
     [document-summary]
     [:button {:on-click #(dispatch [:move-page 1])} ">>"]]))

(defn top-overlay []
  [:div
   [search]])

(defn filter-checkboxes-content [labels key]
  (let [document-filter (subscribe [:document-filter])
        cnt (count labels)
        flt (get-in @document-filter [:filters key])]
    (into [:div {:class "checkboxes"}]
          (map-indexed (fn [n label]
                         [:div
                          [:input {:key (str "filter-cb-" n)
                                   :type "checkbox"
                                   :checked (if flt (boolean (flt n)) true)
                                   :on-change #(dispatch [:checkboxes-toggle key n cnt])}]
                          [:label {:key (str "filter-lbl-" n)} label]])
                       labels))))

(defn filter-header [key]
  [:h1 (str "Filtruj: " (name key))])

(defn filter-checkboxes [labels key]
  [:div {:class "filter filter-cb"}
   [filter-header key]
   [filter-checkboxes-content labels key]
   [:div {:class "buttons"}
    [:button {:on-click #(dispatch [:checkboxes-set-all key])} "Zaznacz wszystkie"]
    [:button {:on-click #(dispatch [:checkboxes-clear-all key])} "Wyczyść wszystkie"]
    [:button {:on-click #(do (dispatch [:set-modal nil])
                             (dispatch [:refresh-table]))} "OK"]]])

(defn filter-text [key]
  (let [filter (subscribe [:document-filter])]
    [:div {:class "filter filter-text"}
     [filter-header key]
     [:input {:type "text"
              :value (get-in @filter [:filters key])
              :on-change (dispatch-value :set-filter key)}]
     [:button {:on-click #(do (dispatch [:set-modal nil])
                              (dispatch [:refresh-table]))} "OK"]]))

(defn filter-widget [col valset]
  (if valset
    (filter-checkboxes valset col)
    (filter-text col)))

(defn main-table-header [col title]
  (let [document-table (subscribe [:document-table])
        [col-id valset] (nth (:metadata @document-table) col)]
    [:a {:href "#"
         :on-click #(dispatch [:set-modal (partial filter-widget col-id valset)])} title]))

(defn main-table-proper []
  (table :document-table
         :cell-renderer (fn [{:keys [data]} row col]
                          (let [val (nth (nth data row) (inc col))]
                            [:a {:href "#"
                                 :on-click #(dispatch [:browse row (nth data row)])
                                 :title val} val]))
         :header-renderer (fn [col title]
                            [main-table-header col title])))

(defn main-table []
  [:div {:class "fullsize"}
   [pagination]
   [main-table-proper]])

(defn iframe-matches []
  (let [iframe (js/document.getElementById "browsed")
        doc (-> iframe .-contentWindow .-document)
        matches (.getElementsByClassName doc "match")]
    (vec (js/Array.prototype.slice.call matches))))

(defn indices [pred coll]
   (keep-indexed #(when (pred %2) %1) coll))

(defn advance-match [delta]
  (let [matches (iframe-matches)
        current (first (indices #(.contains (.-classList %) "selected") matches))
        nxt (-> (if current (+ current delta) 0) (max 0) (min (dec (count matches))))]
    (when (seq matches)
      (when current
        (.remove (.-classList (matches current)) "selected"))
      (.add (.-classList (matches nxt)) "selected")
      (.scrollIntoView (matches nxt)))))

(defn advance-document-button [label title delta]
  (let [browsed-document-num (subscribe [:browsed-document-num])]
    [:button {:title title
              :on-click #(dispatch [:browse-num (+ @browsed-document-num delta)])}
     label]))

(reg-sub :meta-keys
         #(subscribe [:document-table])
         #(map (comp keyword first) (:metadata %1)))

(defn meta-item [key]
  (let [meta-keys (subscribe [:meta-keys])
        browsed-document (subscribe [:browsed-document])]
    (when-let [doc @browsed-document]
      (let [meta-map (zipmap @meta-keys (rest doc))]
        [:span (meta-map key)]))))

(defn vignette
  []
  (let [v @(subscribe [:vignette])]
    (if v
      (postwalk #(if (and (keyword? %) (= (namespace %) "meta"))
                   [meta-item (keyword (name %))]
                   %)
                v)
      [:div.vignette
       [:table
        (for [k @(subscribe [:meta-keys])]
          [:tr [:th k] [:td [meta-item k]]])]])))

(defn document-browser []
  (let [browsed-document (subscribe [:browsed-document])
        document-filter (subscribe [:document-filter])
        corpus (subscribe [:current-corpus])]
    (if-let [doc @browsed-document]
      [:table {:class "fixed-top document"}
       [:tbody
        [:tr {:class "navigation"}
         [:td
          [advance-document-button "<<" "Poprzedni dokument" -1]
          (when (:phrase @document-filter)
            [:button {:on-click #(advance-match -1) :title "Poprzednie wystąpienie"} "<"])]
         [:td [vignette]]
         [:td
          (when (:phrase @document-filter)
            [:button {:on-click #(advance-match 1) :title "Następne wystąpienie"}  ">"])
          [advance-document-button ">>" "Następny dokument" 1]]]
        [:tr [:td {:col-span 3}
              [:div
               [:iframe {:id "browsed"
                         :on-load #(let [slf (js/document.getElementById "browsed")
                                         doc (-> slf .-contentWindow .-document)]
                                     nil #_
                                     (js/alert (str "Found matches: " (.-length (.getElementsByClassName doc "match")))))
                         :src
                         (if-let [phrase (:phrase @document-filter)]
                           (str "/highlight/" @corpus "/" phrase "/" (first doc))
                           (str "/corpus/" @corpus "/" (first doc)))}]]]]]]
      [:h2 "Brak dokumentu do wyświetlenia."])))

(defn document-table []
  [:table {:class "fixed-top documents"}
   [:tbody
    [:tr [:td [top-overlay]]]
    [:tr [:td [tabbar :document-tab
               ["Lista dokumentów" [main-table]
                "Pojedynczy dokument" [document-browser]]]]]]])
