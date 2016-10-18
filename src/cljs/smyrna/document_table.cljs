(ns smyrna.document-table
  (:require [reagent.core :as reagent :refer [atom]]
            [re-frame.core :as re-frame :refer [register-handler path register-sub dispatch dispatch-sync subscribe]]
            [smyrna.api :as api]
            [smyrna.utils :refer [register-accessors register-getter dispatch-value]]
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

(register-accessors :new-area :browsed-document :document-tab :advanced)
(register-getter :document-filter)
(register-getter :metadata)
(register-getter :contexts)
(register-getter :document-table)

(register-handler :set-documents
                  (fn [state [_ documents]]
                    (assoc-in state [:document-table :data] documents)))

(register-handler :refresh-table
                  (fn [state _]
                    (api/call "get-documents"
                              (assoc (filter-params (:document-filter state)) :corpus (:current-corpus state)) #(dispatch [:set-documents %]))
                    state))

(register-handler :reset-filters
                  (fn [state _]
                    (let [res (assoc state :document-filter {:page 0, :rows-per-page 10, :filters {}})]
                      (dispatch [:refresh-table])
                      res)))

(register-handler :move-page
                  (fn [state [_ delta]]
                    (let [res (update-in state [:document-filter :page] + delta)]
                      (dispatch [:refresh-table])
                      res)))

(register-handler :set-phrase
                  (fn [state [_ value]]
                    (assoc-in state [:document-filter :phrase] value)))

(register-handler :set-filter
                  (fn [state [_ column value]]
                    (if (empty? value)
                      (update-in state [:document-filter :filters] dissoc column)
                      (assoc-in state [:document-filter :filters column] value))))

(register-handler :checkboxes-set-all
                  (fn [state [_ column]]
                    (update-in state [:document-filter :filters] dissoc column)))

(register-handler :checkboxes-clear-all
                  (fn [state [_ column]]
                    (assoc-in state [:document-filter :filters column] #{})))

(register-handler :checkboxes-toggle
                  (fn [state [_ column value cnt]]
                    (update-in state [:document-filter :filters column] toggle-nilable value cnt)))

(register-handler :browse
                  (fn [state [_ document]]
                    (assoc state :browsed-document document :document-tab 1)))

(register-handler :create-area
                  (fn [state _]
                    (let [params (filter-params (:document-filter state))]
                      (api/call "create-context" {:name (:new-area state), :description params})
                      (update-in state [:contexts] conj [name params]))))

(register-handler :set-search-context
                  (fn [state [_ value]]
                    (assoc-in state [:document-filter :within] value)))

(defn area-creator []
  [:div
   [:input {:type "text", :placeholder "Wpisz nazwę obszaru", :on-change (dispatch-value :set-new-area)}]
   [:button {:on-click #(dispatch [:create-area])} "Utwórz obszar"]])

(defn search []
  (let [contexts (subscribe [:contexts])
        advanced (subscribe [:advanced])]
    (fn render-search []
      [:div {:class "search"}
       [:div {:class "group"}
        [:input {:class "phrase", :type "text", :autoFocus true, :placeholder "Wpisz szukaną frazę", :on-change (dispatch-value :set-phrase)}]]
       [:div {:class "group"}
        [:button {:class "search-button" :on-click #(dispatch [:refresh-table])} "Szukaj"]
        [:a {:class "advanced" :href "#" :on-click #(dispatch [:set-advanced (not @advanced)])} (if @advanced "Ukryj zaawansowane opcje" "Pokaż zaawansowane opcje »")]]
       (if @advanced
         [:div {:class "group"}
          "Obszar: "
          (into [:select {:on-change (dispatch-value :set-search-context)}
                 [:option "Cały korpus"]]
                (for [[opt _] @contexts]
                  [:option {:value opt, #_:selected #_(= (:within @search-params) opt)} opt]))])
       (if @advanced
         [:div {:class "group"}
          [:button {:on-click #(dispatch [:reset-filters])} "Resetuj filtry"]])])))

(defn pagination []
  (let [document-filter (subscribe [:document-filter])]
    (fn render-pagination []
      [:div {:class "pagination"}
       (if (> (:page @document-filter) 0)
         [:a {:class "prev", :href "#", :on-click #(dispatch [:move-page -1])} "Poprzednie"]
         [:span {:class "disabled link"} "Poprzednie"])
       [:a {:class "next", :href "#", :on-click #(dispatch [:move-page 1])} "Następne"]])))

(defn top-overlay []
  [search]
  #_
  [:div
   [area-creator]
   [search]])

(defn filter-checkboxes-content [labels key]
  (let [document-filter (subscribe [:document-filter])
        cnt (count labels)]
    (fn render-filter-checkboxes-content []
      (let [flt (get-in @document-filter [:filters key])]
        (into [:div]
              (map-indexed (fn [n label]
                             [:span
                              [:input {:key (str "filter-cb-" n)
                                       :type "checkbox"
                                       :checked (if flt (boolean (flt n)) true)
                                       :on-change #(dispatch [:checkboxes-toggle key n cnt])}]
                              [:label {:key (str "filter-lbl-" n)} label]])
                           labels))))))

(defn filter-checkboxes [labels key]
  [:div {:class "filter filter-text"}
   [filter-checkboxes-content labels key]
   [:div
    [:button {:on-click #(dispatch [:checkboxes-set-all key])} "Wszystkie"]
    [:button {:on-click #(dispatch [:checkboxes-clear-all key])} "Żodyn"]
    [:button {:on-click #(do (dispatch [:set-modal nil])
                             (dispatch [:refresh-table]))} "OK"]]])

(defn filter-text [key]
  [:div {:class "filter filter-text"}
   [:input {:type "text"
            :on-change #(dispatch [:set-filter key (-> % .-target .-value)])}]
   [:button {:on-click #(do (dispatch [:set-modal nil])
                             (dispatch [:refresh-table]))} "OK"]])

(defn filter-widget [col valset]
  (if valset
    (filter-checkboxes valset col)
    (filter-text col)))

(defn main-table-header [col title]
  (let [document-table (subscribe [:document-table])]
    (fn render-header []
      (let [[col-id valset] (nth (:metadata @document-table) col)]
        [:a {:href "#"
             :on-click #(dispatch [:set-modal (partial filter-widget col-id valset)])} title]))))

(defn main-table-proper []
  (table :document-table
         :cell-renderer (fn [{:keys [data]} row col]
                          (let [val (nth (nth data row) (inc col))]
                            [:a {:href "#"
                                 :on-click #(dispatch [:browse (first (nth data row))])
                                 :title val} val]))
         :header-renderer (fn [col title]
                            [main-table-header col title])))

(defn main-table []
  [:div {:class "fullsize"}
   [pagination]
   [main-table-proper]])

(defn document-browser []
  (let [browsed-document (subscribe [:browsed-document])
        document-filter (subscribe [:document-filter])
        corpus (subscribe [:current-corpus])]
    (fn render-document-browser []
      [:table {:class "fixed-top document"}
       [:tbody
        [:tr [:td [:h1 "Dokument"]]]
        [:tr [:td
              (if-let [link @browsed-document]
                [:iframe {:src
                          (if-let [phrase (:phrase @document-filter)]
                            (str "/highlight/" @corpus "/" phrase "/" link)
                            (str "/corpus/" @corpus "/" link))}]
                [:h2 "Brak dokumentu do wyświetlenia."])]]]])))

(defn document-table []
  [:table {:class "fixed-top documents"}
   [:tbody
    [:tr [:td [top-overlay]]]
    [:tr [:td [tabbar :document-tab
               ["Lista dokumentów" [main-table]
                "Pojedynczy dokument" [document-browser]
                "KWIC" [:h1 "KWIC"]]]]]]])
