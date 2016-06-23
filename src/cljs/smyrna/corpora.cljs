(ns smyrna.corpora
  (:require-macros [reagent.ratom :refer [reaction]])
  (:require [clojure.string :as string]
            [re-frame.core :as re-frame :refer [register-handler path register-sub dispatch subscribe]]
            [smyrna.api :as api]
            [smyrna.task :as task]
            [smyrna.utils :refer [register-accessors register-getter dispatch-value area-selector]]))

(register-accessors :filepicker-path :filepicker-file :new-corpus-name :corpora-list)

(register-sub :files (fn [state [_ path]] (reaction (get-in @state [:files (or path "/")]))))
(register-handler :set-files (fn [state [_ path files]] (assoc-in state [:files path] files)))

(register-handler :create-corpus
                  (fn [state _]
                    (api/call "create-corpus" {:name (:new-corpus-name state), :file (:file (:filepicker-file state))})
                    (task/get-task-info)
                    state))

(defn get-files [path]
  (api/call "tree" path #(dispatch [:set-files path %])))

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
  (let [corpora (subscribe [:corpora-list])]
    (fn render-corpora-list []
      [:div {:class "corpora-list"}
       [:h1 "Lista korpusów"]
       [:table
        [:thead [:tr [:th "Nazwa korpusu"] [:th "Liczba dokumentów"]]]
        [:tbody (for [{:keys [name num-documents]} @corpora]
                  [:tr [:td name] [:td num-documents]])]]])))

(defn corpora []
  [:div {:class "corpora"}
   [:p "Aby rozpocząć pracę ze Smyrną, trzeba stworzyć korpus. W tym celu należy wskazać plik z metadanymi korpusu lub wybrać katalog na dysku, zawierający dokumenty w formacie HTML."]
   [corpora-list]
   [:div {:class "button-container"}
    [:button {:on-click #(dispatch [:set-modal creator])} "Stwórz nowy korpus"]]])
