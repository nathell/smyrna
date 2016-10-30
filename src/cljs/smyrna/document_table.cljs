(ns smyrna.document-table
  (:require-macros [reagent.ratom :refer [reaction]])
  (:require [clojure.walk :refer [postwalk]]
            [reagent.core :as reagent :refer [atom]]
            [re-frame.core :as re-frame :refer [register-handler path register-sub dispatch dispatch-sync subscribe debug]]
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

(register-accessors :new-area :browsed-document :browsed-document-num :document-tab :advanced)
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

(register-handler :browse-basic
                  (fn [state [_ n document]]
                    (assoc state
                           :browsed-document-num n
                           :browsed-document document
                           :document-tab 1)))

(register-handler :browse
                  (fn [state [_ row document]]
                    (let [df (:document-filter state)]
                      (assoc state
                             :browsed-document-num (+ row (* (:page df) (:rows-per-page df)))
                             :browsed-document document
                             :document-tab 1))))

(register-handler :browse-num
                  (fn [state [_ n]]
                    (api/call "get-documents"
                              (assoc (filter-params (:document-filter state))
                                     :corpus (:current-corpus state)
                                     :offset n :limit 1)
                              #(when (seq %)
                                 (dispatch [:browse-basic n (first %)])))
                    state))

(register-handler :create-area
                  (fn [state _]
                    (let [params (filter-params (:document-filter state))
                          name (:new-area state)]
                      (api/call "create-context" {:name name, :description params, :corpus (:current-corpus state)})
                      (update-in state [:contexts] conj [name params]))))

(register-handler :set-search-context
                  (fn [state [_ value]]
                    (assoc-in state [:document-filter :within] value)))

(defn area-creator []
  [:div
   [:input {:type "text", :placeholder "Wpisz nazwę obszaru", :on-change (dispatch-value :set-new-area)}]
   [:button {:on-click #(do (dispatch [:create-area])
                            (dispatch [:set-modal nil]))}
    "Utwórz obszar"]])

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
          [:button {:on-click #(dispatch [:set-modal area-creator])} "Utwórz obszar"]
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
  [:div
   [search]])

(defn filter-checkboxes-content [labels key]
  (let [document-filter (subscribe [:document-filter])
        cnt (count labels)]
    (fn render-filter-checkboxes-content []
      (let [flt (get-in @document-filter [:filters key])]
        (into [:div {:class "checkboxes"}]
              (map-indexed (fn [n label]
                             [:div
                              [:input {:key (str "filter-cb-" n)
                                       :type "checkbox"
                                       :checked (if flt (boolean (flt n)) true)
                                       :on-change #(dispatch [:checkboxes-toggle key n cnt])}]
                              [:label {:key (str "filter-lbl-" n)} label]])
                           labels))))))

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
    (fn []
      [:div {:class "filter filter-text"}
       [filter-header key]
       [:input {:type "text"
                :value (get-in @filter [:filters key])
                :on-change (dispatch-value :set-filter key)}]
       [:button {:on-click #(do (dispatch [:set-modal nil])
                                (dispatch [:refresh-table]))} "OK"]])))

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

(defn advance-document-button [label delta]
  (let [browsed-document-num (subscribe [:browsed-document-num])]
    (fn []
      [:button {:on-click #(dispatch [:browse-num (+ @browsed-document-num delta)])} label])))

(defn meta-item [key]
  (let [document-table (subscribe [:document-table])
        browsed-document (subscribe [:browsed-document])]
    (fn render-meta-item []
      (when-let [doc @browsed-document]
        (let [meta-map (zipmap (map (comp keyword first) (:metadata @document-table)) (rest doc))]
          [:span (meta-map key)])))))

(register-sub :vignette #(reaction (-> %1 deref :custom :vignette)))

(defn vignette
  []
  (let [v (subscribe [:vignette])]
    (fn render-vignette []
      (postwalk #(if (and (keyword? %) (= (namespace %) "meta"))
                   [meta-item (keyword (name %))]
                   %)
                @v))))

(defn document-browser []
  (let [browsed-document (subscribe [:browsed-document])
        document-filter (subscribe [:document-filter])
        corpus (subscribe [:current-corpus])]
    (fn render-document-browser []
      (if-let [doc @browsed-document]
        [:table {:class "fixed-top document"}
         [:tbody
          [:tr {:class "navigation"}
           [:td
            [advance-document-button "<<" -1]
            (when (:phrase @document-filter)
              [:button {:on-click #(advance-match -1)} "<"])]
           [:td [vignette]]
           [:td
            (when (:phrase @document-filter)
              [:button {:on-click #(advance-match 1)}  ">"])
            [advance-document-button ">>" 1]]]
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
        [:h2 "Brak dokumentu do wyświetlenia."]))))

(defn document-table []
  [:table {:class "fixed-top documents"}
   [:tbody
    [:tr [:td [top-overlay]]]
    [:tr [:td [tabbar :document-tab
               ["Lista dokumentów" [main-table]
                "Pojedynczy dokument" [document-browser]
                "KWIC" [:h1 "KWIC"]]]]]]])
