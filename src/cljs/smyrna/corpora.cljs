(ns smyrna.corpora
  (:require-macros [reagent.ratom :refer [reaction]])
  (:require [clojure.string :as string]
            [re-frame.core :as re-frame :refer [register-handler path register-sub dispatch subscribe]]
            [smyrna.api :as api]
            [smyrna.utils :refer [register-accessors register-getter dispatch-value area-selector]]))

(register-sub :files (fn [state [_ path]] (reaction (get-in @state [:files (or path "/")]))))
(register-handler :set-files (fn [state [_ path files]] (assoc-in state [:files path] files)))

(defn get-files [path]
  (api/call "tree" path #(dispatch [:set-files path %])))

(register-accessors :filepicker-path)

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
                 [[:li {:class (if dir "dir" "file")}
                   (cond
                     on-path? [:b file]
                     dir [:a {:href "#", :on-click #(do (get-files subtree) (dispatch [:set-filepicker-path subtree]))} file]
                     :otherwise file)]
                  (when on-path?
                    [directory-tree subtree])]))))))

(defn corpora []
  [:div {:class "corpora"}
   [:p "Aby rozpocząć pracę ze Smyrną, trzeba stworzyć korpus. W tym celu należy wybrać katalog na dysku, zawierający dokumenty w formacie HTML."]
   [:button {:on-click #(dispatch [:set-modal directory-tree])} "Utwórz korpus"]])
