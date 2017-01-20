(ns smyrna.corpora
  (:require [clojure.string :as string]
            [re-frame.core :as re-frame :refer [reg-event-db reg-event-fx reg-sub dispatch subscribe]]
            [smyrna.api :as api]
            [smyrna.task :as task]
            [smyrna.table :refer [table]]
            [smyrna.utils :refer [reg-accessors reg-getters dispatch-value area-selector]]))

(reg-accessors :filepicker-path :filepicker-file :new-corpus-name)
(reg-getters :current-corpus :corpora-table)

(reg-sub :files
         (fn [db [_ path]]
           (get-in db [:files (or path "/")])))

(defn get-columns
  [metadata custom]
  (let [maxw (- js/window.innerWidth 76)
        w (min 200 (/ maxw (count metadata)))
        totalw (when custom (apply + (map :width (vals (:columns custom)))))]
    (into {} (for [[name _] metadata :let [k (keyword name)]]
               [k {:title (or (when custom (:label (k (:columns custom)))) (string/capitalize name)),
                   :width (or (when custom (let [w' (:width (k (:columns custom)))]
                                             (if (zero? w')
                                               (max 200 (- maxw totalw))
                                               w')))
                              w)}]))))

(defn get-shown-columns
  [metadata]
  (mapv (comp keyword first) metadata))

(reg-event-db
 :set-files
 (fn [db [_ path files]]
   (assoc-in db [:files path] files)))

(reg-event-db
 :set-metadata
 (fn [db [_ {:keys [metadata contexts custom]}]]
   (-> db
       (assoc-in [:document-table :metadata] metadata)
       (assoc-in [:document-table :columns] (get-columns metadata custom))
       (assoc-in [:document-table :shown-columns] (get-shown-columns metadata))
       (assoc-in [:document-table :column-order] (get-shown-columns metadata))
       (assoc :contexts contexts :custom custom))))

(reg-event-fx
 :get-corpora
 (fn [_ _]
   {:api ["get-corpora" {} :set-corpora-list]}))

(reg-event-fx
 :get-corpus-info
 (fn [_ [_ corpus]]
   {:api ["get-corpus-info" {:corpus corpus} :set-metadata]}))

(reg-event-fx
 :get-files
 (fn [_ [_ path]]
   {:api ["tree" path :set-files path]}))

(reg-event-fx
 :create-corpus
 (fn [{db :db} _]
   {:api ["create-corpus" {:name (:new-corpus-name db)
                           :file (:file (:filepicker-file db))}]
    :dispatch [:get-task-info]}))

(reg-event-fx
 :init-corpus
 (fn [{db :db} _]
   {:dispatch-n [[:get-corpus-info (:current-corpus db)]
                 [:refresh-table]]}))

(reg-event-fx
 :switch-corpus
 (fn [{db :db} [_ corpus]]
   {:dispatch [:init-corpus]
    :db (assoc db :current-corpus corpus :tab 2)}))

(defn drop-ext [f]
  (string/replace f #"\..*" ""))

(defn directory-tree
  ([] (directory-tree "/"))
  ([root]
   (let [path (subscribe [:filepicker-path])
         files (subscribe [:files root])]
     (reduce into [:ul {:class "tree"}]
             (for [{:keys [dir file]} @files
                   :let [subtree (str root (if (= root "/") "" "/") file)
                         on-path? (and dir @path (string/starts-with? @path subtree))]]
               [[:li {:class (if dir (if on-path? "open-dir" "dir") "file")}
                 (if on-path?
                   [:b file]
                   [:a {:href "#", :on-click #(do (dispatch [:get-files subtree])
                                                  (when dir (dispatch [:set-filepicker-path subtree]))
                                                  (dispatch [:set-filepicker-file {:type (if dir :dir :file), :file subtree}])
                                                  (dispatch [:set-new-corpus-name (drop-ext file)]))}
                    file])]
                (when on-path?
                  [directory-tree subtree])])))))

(defn creator []
  (let [file (subscribe [:filepicker-file])
        name (subscribe [:new-corpus-name])]
    [:div {:class "corpora-creator"}
     [:h2 "Nowy korpus"]
     [:p "Wybierz plik CSV z metadanymi lub katalog korpusu:"]
     [:div {:class "directory-tree-container"}
      [directory-tree "/"]]
     (when @file
       [:p "Wybrany " ({:file "plik", :dir "katalog"} (:type @file)) ": " [:b (:file @file)]])
     [:p "Nazwa korpusu: " [:input {:type "text", :value @name, :on-change (dispatch-value :set-new-corpus-name)}]]
     [:div {:class "button-container"}
      [:button {:on-click #(dispatch [:create-corpus])} "Utwórz korpus"]]]))

(defn corpora-list []
  (table :corpora-table
         :cell-renderer (fn [{:keys [data]} row col]
                          (let [arow (nth data row)
                                val (nth arow col)]
                            (if (= col 1)
                              [:a {:href "#" :on-click #(dispatch [:switch-corpus (first arow)])} val]
                              val)))))

(defn corpus-selector []
  (let [current-corpus (subscribe [:current-corpus])
        corpora (subscribe [:corpora-table])]
    [:select {:class "corpus-selector", :on-change (dispatch-value :switch-corpus), :value (or @current-corpus "__empty__")}
     (if-not @current-corpus
       [:option {:value "__empty__"} "[Wybierz korpus]"])
     (doall
      (for [[id name _] (:data @corpora)]
        [:option {:key name, :value id} name]))]))

(defn corpora []
  [:div {:class "corpora"}
   [:p "Aby rozpocząć pracę ze Smyrną, trzeba stworzyć korpus. W tym celu należy wskazać plik z metadanymi korpusu lub wybrać katalog na dysku, zawierający dokumenty w formacie HTML."]
   [:h1 "Lista korpusów"]
   [corpora-list]
   [:div {:class "button-container"}
    [:button {:on-click #(dispatch [:set-modal creator])} "Stwórz nowy korpus"]]])
