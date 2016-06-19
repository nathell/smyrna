(ns smyrna.document-table
  (:require [reagent.core :as reagent :refer [atom]]
            [re-frame.core :as re-frame :refer [register-handler path register-sub dispatch dispatch-sync subscribe]]
            [smyrna.api :as api]
            [smyrna.utils :refer [register-accessors register-getter dispatch-value]]))

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

(register-accessors :documents :new-area :shown-filter :browsed-document)
(register-getter :document-filter)
(register-getter :metadata)
(register-getter :contexts)

(register-handler :refresh-table
                  (fn [state _]
                    (api/call "get-documents" (filter-params (:document-filter state)) #(dispatch [:set-documents %]))
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
                    (assoc state :browsed-document document :tab 1)))

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
  (let [contexts (subscribe [:contexts])]
    (fn render-search []
      [:div
       [:input {:type "text", :placeholder "Wpisz szukaną frazę", :on-change (dispatch-value :set-phrase)}]
       "Obszar: "
       (into [:select {:on-change (dispatch-value :set-search-context)}
              [:option "Cały korpus"]]
             (for [[opt _] @contexts]
               [:option {:value opt, #_:selected #_(= (:within @search-params) opt)} opt]))
       [:button {:on-click #(dispatch [:refresh-table])} "Szukaj"]
       [:button {:on-click #(dispatch [:reset-filters])} "Resetuj filtry"]])))

(defn pagination []
  (let [document-filter (subscribe [:document-filter])]
    (fn render-pagination []
      [:div {:class "pagination"}
       (if (> (:page @document-filter) 0)
         [:a {:class "prev", :href "#", :on-click #(dispatch [:move-page -1])} "Poprzednie"]
         [:span {:class "disabled link"} "Poprzednie"])
       [:a {:class "next", :href "#", :on-click #(dispatch [:move-page 1])} "Następne"]])))

(defn top-overlay []
  [:div
   [area-creator]
   [search]
   [pagination]])

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
  [:div
   [filter-checkboxes-content labels key]
   [:div
    [:button {:on-click #(dispatch [:checkboxes-set-all key])} "Wszystkie"]
    [:button {:on-click #(dispatch [:checkboxes-clear-all key])} "Żodyn"]
    [:button {:on-click #(do (dispatch [:set-shown-filter nil])
                             (dispatch [:refresh-table]))} "OK"]]])

(defn filter-text [key]
  [:input {:type "text"
           ; :value (get-in @search-params [:filters key])
           :on-change #(dispatch [:set-filter key (-> % .-target .-value)])}])

(defn filter-widget [col valset]
  (if valset
    (filter-checkboxes valset col)
    (filter-text col)))

(defn header []
  (let [metadata (subscribe [:metadata])
        shown-filter (subscribe [:shown-filter])]
    (fn render-header []
      [:thead
       (vec (concat [:tr]
                    [[:th "Akcje"]]
                    (for [[col valset] @metadata]
                      [:th
                       [:a {:href "#" :on-click #(dispatch [:set-shown-filter col])} col]
                       (if (= @shown-filter col)
                         [filter-widget col valset])])))])))

(defn body []
  (let [documents (subscribe [:documents])]
    (fn body-render []
      [:tbody
       (for [[key & row] @documents]
         [:tr {:key key}
          [:td {:key (str key "-actions")}
           [:a {:href "#" :on-click #(dispatch [:browse key])} "Zobacz"]]
          (for [[i cell] (map-indexed vector row)]
            [:td {:key (str key "-" i)} cell])])])))

(defn document-table []
  [:div {:class "documents-container"}
   [top-overlay]
   [:table {:class "documents"}
    [header]
    [body]]])
