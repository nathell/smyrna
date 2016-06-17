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

;; Generic textbox

(defn textbox [state & {:keys [caption placeholder value on-change],
                        :or {caption "Change"}}]
  (let [id (gensym)
        value (or value @state)
        on-change (comp (or on-change (partial reset! state))
                        #(.-value (js/getElementById id)))]
    [:div
     [:input {:id id, :placeholder placeholder, :type "text" :value value}]
     [:button {:on-click on-change} caption]]))

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

(defn api-call [method params & [f]]
  (let [f (or f identity)]
    (go (f (:body (<! (http/post (str "/api/" method) {:edn-params params})))))))

(defn refresh-table [params]
  (api-call "get-documents" params #(reset! table-contents %)))

(defn table-params [{:keys [page rows-per-page filters phrase within]}]
  {:offset (* page rows-per-page),
   :limit rows-per-page,
   :filters filters,
   :phrase phrase,
   :within within})

(def initial-search-params {:page 0, :rows-per-page 10, :filters {}})
(def search-params (atom initial-search-params))
(def table-state (atom {:shown-filter nil}))
(def contexts (atom []))

(defn refresh-contexts []
  (api-call "get-contexts" params #(reset! contexts %)))

(defn update-table-params
  [el f & args]
  (let [el (if (vector? el) el [el])
        f (if (fn? f) f (constantly f))]
    (apply swap! search-params update-in el f args)
    (refresh-table (table-params @search-params))))

(defn search []
  (let [text-id (gensym) select-id (gensym)]
    [:div
     [:input {:type "text", :id text-id, :placeholder "Wpisz szukaną frazę",
              #_:value #_(:phrase @search-params)}]
     "Obszar: "
     (into [:select {:id select-id}
            [:option "Cały korpus"]]
           (for [[opt _] @contexts]
             [:option {:value opt, :selected (= (:within @search-params) opt)} opt]))
     [:button
      {:on-click (fn [& _]
                   (swap! search-params assoc
                          :phrase (.-value (.getElementById js/document text-id))
                          :within (.-value (.getElementById js/document select-id)))
                   (refresh-table (table-params @search-params)))}
      "Szukaj"]
     [:button {:on-click #(do (reset! search-params initial-search-params) (refresh-table (table-params @search-params)))} "Resetuj filtry"]]))

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
#_  [:div]
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
(refresh-contexts)

(defn create-context
  [name]
  (api-call "create-context" {:name name, :description (table-params @search-params)})
  (swap! contexts conj [name (table-params @search-params)]))

(defn context-creator
  []
  (let [id (str (gensym))]
    [:div
     [:input {:id id, :type "text", :placeholder "Wpisz nazwę obszaru"}]
     [:button {:on-click #(create-context (.-value (.getElementById js/document id)))} "Utwórz kontekst"]]))

(defn table2 [header state]
  (let [{:keys [shown-filter]} @state]
    [:div {:class "documents-container"}
     [search]
     [context-creator]
     [:p
      [:a {:href "#" :on-click #(update-table-params :page dec)} "Poprzednie"]
      [:a {:href "#" :on-click #(update-table-params :page inc)} "Następne"]]
     [:table {:class "documents"}
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
                 [:td [:a {:href "#" :on-click #(do (reset! current-document [(:phrase @search-params) (row-key row)])
                                                    (reset! current-page 1))} "Zobacz"]]
                 (for [cell row]
                   [:td cell])])]]]))

(defn document-browser [state]
  [:div {:class "nav-container"}
   [:h1 "Dokument"]
   (if-let [[phrase link] @state]
     [:iframe {:width "100%" :height "50%" :src
               (if phrase
                 (str "/highlight/" phrase "/" link)
                 (str "/corpus/" link))}]
     [:h2 "Brak dokumentu do wyświetlenia."])])

(def wordcloud-data (atom nil))

(defn wordcloud-draw [words]
  (-> js/d3 (.select "#wcl") (.selectAll "svg") (.remove))
  (-> js/d3
      (.select "#wcl")
      (.append "svg")
      (.attr "width" 800)
      (.attr "height" 600)
      (.append "g")
      (.attr "transform" "translate(400,300)")
      (.selectAll "text")
      (.data words)
      (.enter)
      (.append "text")
      (.style "font-size" #(str (.-size %) "px"))
      (.style "font-weight" "bold")
      (.style "font-family" "Arial")
      (.attr "text-anchor" "middle")
      (.attr "transform" #(str "translate(" (.-x %) "," (.-y %) ")rotate(" (.-rotate %) ")"))
      (.text #(.-text %))))

(defn wordcloud-sizes
  [ll]
  (let [words (map first ll)
        counts (map second ll)
        maxc (apply max counts)
        scaled (map #(/ % maxc) counts)
        sizes (map #(* 100 (js/Math.sqrt %)) scaled)]
    (zipmap words sizes)))

(defn wordcloud-layout []
  (let [w (clj->js (for [[word count] (wordcloud-sizes @wordcloud-data)] {:text word, :size count}))]
    (-> js/d3.layout
        (.cloud)
        (.size #js [800 600])
        (.words w)
        (.padding 5)
        (.font "Arial")
        (.fontWeight "bold")
        (.fontSize #(.-size %))
        (.on "end" wordcloud-draw)
        (.start))))

(defn wordcloud-tree []
  (identity @wordcloud-data)
  (let [id (gensym)]
    [:div
     (into [:select
            {:id id}
            #_{:on-change #(update-table-params :within (-> % .-target .-value))}
            #_[:option "Cały korpus"]]
           (for [[opt _] @contexts]
             [:option {:value opt} opt]))
     [:button {:on-click (fn [] (api-call "compare-contexts" [(.-value (.getElementById js/document id)) nil] #(reset! wordcloud-data %)))}
      "Pokaż"]
     [:div {:id "wcl"}]]))

(defn wordcloud []
  (reagent/create-class {:reagent-render wordcloud-tree,
                         :component-did-mount wordcloud-layout,
                         :component-did-update wordcloud-layout}))

(defn root [header]
  [navbar current-page
   "Lista dokumentów" [table2 header table-state]
   "Przeglądanie" [document-browser current-document]
   "Chmury słów" [wordcloud]])

(defn mount-root []
  (go
    (let [resp (<! (http/get "/meta-header" {:with-credentials? false}))]
      (reagent/render [root (:body resp)] (.getElementById js/document "app")))))

(defn init! []
  (mount-root))
