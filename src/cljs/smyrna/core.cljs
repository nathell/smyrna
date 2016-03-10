(ns smyrna.core
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [reagent.core :as reagent :refer [atom]]
            [cljs-http.client :as http]
            [clojure.string :as string]
            [cljs.core.async :refer [<!]]))

(def meta-location "/meta")

;; State

(def current-page (atom 0))
(def current-document (atom nil))

;; Navbar

(defn navbar [active & labels-and-components]
  (let [pairs (partition 2 labels-and-components)
        labels (map first pairs)
        components (map second pairs)]
    [:div {:class "nav-container"}
     [:ul {:class "nav"}
      (doall
       (for [[n label] (map-indexed vector labels)]
         [:li {:key (str "navbar-" n),
               :class (if (= n @active) "item selected" "item")}
          [:a {:href "#" :on-click #(reset! active n)} label]]))]
     (nth components @active)]))

;; Filter

(def table-contents (atom nil))

(defn refresh-table [params]
  (go
    (reset! table-contents
            (:body (<! (http/post "/api/get-documents" {:edn-params params}))))))

(defn table-params [{:keys [page rows-per-page filters phrase]}]
  {:offset (* page rows-per-page), :limit rows-per-page, :filters filters, :phrase phrase})

(def search-params (atom {:page 0, :rows-per-page 10, :filters {}}))
(def table-state (atom {:shown-filter nil}))

(defn update-table-params
  [el f & args]
  (let [el (if (vector? el) el [el])
        f (if (fn? f) f (constantly f))]
    (apply swap! search-params update-in el f args)
    (refresh-table (table-params @search-params))))

(defn search []
  [:input {:type "text", :placeholder "Wpisz szukaną frazę",
           :on-change #(update-table-params :phrase (-> % .-target .-value)),
           :value (:phrase @search-params)}])

(defn toggle [set el]
  ((if (set el) disj conj) set el))

(defn toggle-nilable [s el n]
  (toggle (or s (set (range n))) el))

(defn toggle! [a i]
  (swap! a toggle i))

(defn filter-checkboxes [labels key state]
  (vec
   (concat
    [:div]
    (vec (mapcat (fn [n label]
                   [^{:key (str "filter-cb-" n)}
                    [:input {:type "checkbox"
                             :checked (let [flt (get-in @state [:filters key])]
                                        (if flt (boolean (flt n)) true))
                             :on-change #(update-table-params [:filters key] toggle-nilable n (count labels))}]
                    [:label {:key (str "filter-lbl-" n)} label]])
                 (range) labels))
    [[:div
      [:button {:on-click #(update-table-params :filters dissoc key)} "Wszystkie"]
      [:button {:on-click #(update-table-params [:filters key] #{})} "Żodyn"]
      [:button {:on-click #(swap! table-state update-in [:shown-filter] (constantly nil))} "OK"]]])))

(defn filter-text [key state]
  [:input {:type "text"
           :value (get-in @search-params [:filters key])
           :on-change #(update-table-params [:filters key] (-> % .-target .-value))}])

(defn make-filter [col valset state]
  (if valset
    (filter-checkboxes valset col state)
    (filter-text col state)))

(defn filter-fn
  [header-indexed valsets [k v]]
  (let [i (header-indexed k)]
    (if (set? v)
      (fn [row] (v (row i)))
      (let [valset (valsets i)
            v (.toLowerCase v)
            matches (set (remove nil? (map-indexed (fn [i x] (when (not= -1 (.indexOf (.toLowerCase x) v)) i)) valset)))]
        (fn [row] (matches (row i)))))))

(defn andf
  ([] (constantly true))
  ([f] f)
  ([f g] (fn [x] (and (f x) (g x))))
  ([f g & fs] (andf f (apply andf g fs))))

(defn compute-filter
  [header valsets m]
  (let [header-indexed (zipmap header (range))
        single-filters (map (partial filter-fn header-indexed valsets) m)]
    (apply andf single-filters)))

;; Table

(defn row-key [[term pos dzien _ wyp]]
  ;; XXX: configurable!
  (string/join "/" [term pos dzien wyp]))

(refresh-table (table-params @search-params))

(defn table2 [header state]
  (let [{:keys [shown-filter]} @state]
    [:div
     [:h1 "Lista dokumentów"]
     [search]
     [:p
      [:a {:href "#" :on-click #(update-table-params :page dec)} "Poprzednie"]
      [:a {:href "#" :on-click #(update-table-params :page inc)} "Następne"]]
     [:table
      [:thead
       (vec (concat [:tr]
                    [[:th "Akcje"]]
                    (for [[col valset] header]
                      [:th
                       [:a {:href "#" :on-click #(swap! state update-in [:shown-filter] (constantly col))} col]
                       (if (= shown-filter col)
                         [make-filter col valset search-params])])))]
      [:tbody (for [row @table-contents]
                [:tr
                 [:td [:a {:href "#" :on-click #(do (reset! current-document (row-key row))
                                                    (reset! current-page 1))} "Zobacz"]]
                 (for [cell row]
                   [:td cell])])]]]))

(defn document-browser [state]
  [:div {:class "nav-container"}
   [:h1 "Dokument"]
   (if @state
     [:iframe {:width "100%" :height "100%" :src (str "/corpus/" @state)}]
     [:h2 "Brak dokumentu do wyświetlenia."])])

(defn root [header]
  [navbar current-page
   "Lista dokumentów" [table2 header table-state]
   "Przeglądanie" [document-browser current-document]])

(defn mount-root []
  (go
    (let [resp (<! (http/get "/meta-header" {:with-credentials? false}))]
      (reagent/render [root (:body resp)] (.getElementById js/document "app")))))

(defn init! []
  (mount-root))
