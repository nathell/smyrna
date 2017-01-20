(ns smyrna.task
  (:require [clojure.string :as string]
            [re-frame.core :as re-frame :refer [reg-event-fx dispatch subscribe]]
            [smyrna.utils :refer [reg-getters]]))

(reg-getters :task-info)

(defn spinner [info]
  [:div {:class "spinner"}
   [:img {:src "/images/spinner.gif"}]
   (when info [:p info])])

(defn browser []
  (let [info (subscribe [:task-info])]
    [:div {:class "task"}
     (when-let [i @info]
       [spinner i])]))

(reg-event-fx
 :update-task-info
 (fn [{db :db} [_ x]]
   (if x
     {:db (assoc db :task-info x :modal browser)
      :dispatch-later [{:ms 200 :dispatch [:get-task-info]}]}
     {:db (assoc db :task-info nil :modal nil)})))

(reg-event-fx
 :get-task-info
 (fn [_ _]
   {:api ["get-task-info" nil :update-task-info]}))
