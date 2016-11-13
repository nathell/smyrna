(ns smyrna.task
  (:require-macros [reagent.ratom :refer [reaction]])
  (:require [clojure.string :as string]
            [re-frame.core :as re-frame :refer [register-handler path register-sub dispatch subscribe]]
            [smyrna.api :as api]
            [smyrna.utils :refer [register-accessors register-getter dispatch-value area-selector]]))

(register-accessors :task-info)

(defn spinner [info]
  [:div {:class "spinner"}
   [:img {:src "/images/spinner.gif"}]
   (when info [:p info])])

(defn browser []
  (let [info (subscribe [:task-info])]
    (fn []
      [:div {:class "task"}
       (when-let [i @info]
         [spinner i])])))

(defn get-task-info
  []
  (api/call "get-task-info" nil
            (fn [x]
              (dispatch [:set-task-info x])
              (if x
                (do
                  (js/window.setTimeout get-task-info 200)
                  (dispatch [:set-modal browser]))
                (dispatch [:set-modal nil])))))
