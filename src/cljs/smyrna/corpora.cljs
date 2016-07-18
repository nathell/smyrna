(ns smyrna.corpora
  (:require-macros [reagent.ratom :refer [reaction]])
  (:require [clojure.string :as string]
            [re-frame.core :as re-frame :refer [register-handler path register-sub dispatch subscribe]]
            [smyrna.api :as api]
            [smyrna.task :as task]
            [smyrna.table :refer [table]]
            [smyrna.utils :refer [register-accessors register-getter dispatch-value area-selector]]))

(defn get-corpora []
  (api/call "get-corpora" {} #(dispatch [:set-corpora-list %])))

(defn get-corpus-info [corpus]
  (api/call "get-corpus-info" {:corpus corpus} #(dispatch [:set-metadata %])))

(defn get-files [path]
  (api/call "tree" path #(dispatch [:set-files path %])))

(register-accessors :filepicker-path :filepicker-file :new-corpus-name)

(register-getter :current-corpus)

(register-sub :files (fn [state [_ path]] (reaction (get-in @state [:files (or path "/")]))))
(register-handler :set-files (fn [state [_ path files]] (assoc-in state [:files path] files)))

(register-handler :create-corpus
                  (fn [state _]
                    (api/call "create-corpus" {:name (:new-corpus-name state), :file (:file (:filepicker-file state))})
                    (task/get-task-info)
                    state))

(register-handler :init-corpus
                  (fn [state _]
                    (get-corpus-info (:current-corpus state))
                    (dispatch [:refresh-table])
                    state))

(register-handler :switch-corpus
                  (fn [state [_ corpus]]
                    (dispatch [:init-corpus])
                    (assoc state :current-corpus corpus)))

(register-getter :corpora-table)

(defn drop-ext [f]
  (string/replace f #"\..*" ""))

(defn directory-tree
  ([] (directory-tree "/"))
  ([root]
   (let [path (subscribe [:filepicker-path])
         files (subscribe [:files root])]
     (fn render-directory-tree []
       (reduce into [:ul {:class "tree"}]
               (for [{:keys [dir file]} @files
                     :let [subtree (str root (if (= root "/") "" "/") file)
                           on-path? (and dir @path (string/starts-with? @path subtree))]]
                 [[:li {:class (if dir (if on-path? "open-dir" "dir") "file")}
                   (if on-path?
                     [:b file]
                     [:a {:href "#", :on-click #(do (get-files subtree)
                                                    (when dir (dispatch [:set-filepicker-path subtree]))
                                                    (dispatch [:set-filepicker-file {:type (if dir :dir :file), :file subtree}])
                                                    (dispatch [:set-new-corpus-name (drop-ext file)]))}
                      file])]
                  (when on-path?
                    [directory-tree subtree])]))))))

(defn creator []
  (let [file (subscribe [:filepicker-file])
        name (subscribe [:new-corpus-name])]
    (fn render-creator []
      [:div {:class "corpora-creator"}
       [:h2 "Nowy korpus"]
       [:p "Wybierz plik CSV z metadanymi lub katalog korpusu:"]
       [:div {:class "directory-tree-container"}
        [directory-tree "/"]]
       (when @file
         [:p "Wybrany " ({:file "plik", :dir "katalog"} (:type @file)) ": " [:b (:file @file)]])
       [:p "Nazwa korpusu: " [:input {:type "text", :value @name, :on-change (dispatch-value :set-new-corpus-name)}]]
       [:div {:class "button-container"}
        [:button {:on-click #(dispatch [:create-corpus])} "Utwórz korpus"]]])))

(defn corpora-list []
  (table :corpora-table
         :cell-renderer (fn [row i]
                          (let [val (nth row i)]
                            (if (zero? i)
                              [:a {:href "#" :on-click #(dispatch [:switch-corpus val])} val]
                              val)))))

(defn corpus-selector []
  (let [current-corpus (subscribe [:current-corpus])
        corpora (subscribe [:corpora-table])]
    (fn render-corpus-selector []
      [:select {:class "corpus-selector", :on-change (dispatch-value :switch-corpus), :value @current-corpus}
       (if-not @current-corpus
         [:option "[Wybierz korpus]"])
       (doall
        (for [[name _] (:data @corpora)]
          [:option {:key name} name]))])))

(defn corpora []
  [:div {:class "corpora"}
   [:p "Aby rozpocząć pracę ze Smyrną, trzeba stworzyć korpus. W tym celu należy wskazać plik z metadanymi korpusu lub wybrać katalog na dysku, zawierający dokumenty w formacie HTML."]
   [:h1 "Lista korpusów"]
   [corpora-list]
   [:div {:class "button-container"}
    [:button {:on-click #(dispatch [:set-modal creator])} "Stwórz nowy korpus"]]])
