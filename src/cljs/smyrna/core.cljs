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
                             :on-change #(swap! state update-in [:filters key] toggle-nilable n (count labels))}]
                    [:label {:key (str "filter-lbl-" n)} label]])
                 (range) labels))
    [[:div
      [:button {:on-click #(swap! state update-in [:filters] dissoc key)} "Wszystkie"]
      [:button {:on-click #(swap! state update-in [:filters key] (constantly #{}))} "Żodyn"]
      [:button {:on-click #(swap! state update-in [:shown-filter] (constantly nil))} "OK"]]])))

(defn filter-text [key state]
  [:input {:type "text"
           :value (get-in @state [:filters key])
           :on-change #(swap! state update-in [:filters key] (constantly (-> % .-target .-value)))}])

(defn make-filter [valset key state]
  (if (< (count valset) 200)
    (filter-checkboxes valset key state)
    (filter-text key state)))

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

(defn row-key [header valsets row]
  (let [parts ["term" "pos" "dzien" "wyp"]
        header-indexed (zipmap header (range))]
    ;; XXX: configurable!
    (string/join "/" (map #(let [i (header-indexed %)] (nth (nth valsets i) (row i))) parts))))

(defn table [[header types valsets data] state]
  (let [{:keys [page rows-per-page shown-filter filters]} @state
        flt (compute-filter header valsets filters)
        data (filter flt data)
        num-pages (js/Math.ceil (/ (count data) rows-per-page))
        offset (* page rows-per-page)
        update-state (fn [el f] #(swap! state update-in [el] (if (fn? f) f (constantly f))))]
    [:div
     [:h1 "Lista dokumentów"]
     [:p
      [:a {:href "#" :on-click (update-state :page dec)} "Poprzednie"]
      [:a {:href "#" :on-click (update-state :page inc)} "Następne"]]
     [:table
      [:thead
       (vec (concat [:tr]
                    [[:th "Akcje"]]
                    (for [[i col] (map-indexed vector header)]
                      [:th
                       [:a {:href "#" :on-click (update-state :shown-filter col)} col]
                       (if (= shown-filter col)
                         [make-filter (valsets i) col state])])))]
      [:tbody
       (for [row (take rows-per-page (drop offset data))]
         [:tr
          [:td [:a {:href "#" :on-click #(do (reset! current-document (row-key header valsets row))
                                             (reset! current-page 2))} "Zobacz"]]
          (map (fn [valset i] [:td (nth valset i)]) valsets row)])]]
     [:p (str (count data) " dokumentów (strona " (inc page) " z " num-pages ")")]]))

(defn document-browser [state]
  [:div {:class "nav-container"}
   [:h1 "Dokument"]
   (if @state
     [:iframe {:width "100%" :height "100%" :src (str "/corpus/" @state)}]
     [:h2 "Brak dokumentu do wyświetlenia."])])

(defn search []
  [:h1 "Tu będzie wyszukiwarka."])

(def table-state (atom {:page 0, :rows-per-page 10, :shown-filter nil, :filters {}}))

(defn root [cmeta]
  [navbar current-page
   "Dokumenty" [table cmeta table-state]
   "Wyszukiwanie" [search]
   "Wyniki" [document-browser current-document]])

(defn mount-root []
  (go
    (let [resp (<! (http/get meta-location {:with-credentials? false}))]
      (def cmeta (:body resp))
      (reagent/render [root (:body resp)] (.getElementById js/document "app")))))

(defn init! []
  (mount-root))
