(ns smyrna.utils
  (:require [re-frame.core :refer [reg-event-db reg-sub dispatch-sync subscribe]]))

(defn setter-event
  [k]
  (keyword (str "set-" (name k))))

(defn reg-setter
  ([k] (reg-setter k (setter-event k)))
  ([k handler-name]
   (reg-event-db handler-name
                 (fn [db [_ new-value]]
                   (assoc db k new-value)))))

(defn reg-getter
  [k]
  (reg-sub k (fn [db _] (k db))))

(defn reg-getters
  [& ks]
  (doseq [k ks]
    (reg-getter k)))

(defn reg-setters
  [& ks]
  (doseq [k ks]
    (reg-setter k)))

(defn reg-accessors
  [& ks]
  (apply reg-getters ks)
  (apply reg-setters ks))

(defn dispatch-value
  [ev & args]
  #(dispatch-sync (vec (concat [ev] args [(-> % .-target .-value)]))))

;; common components:

(defn area-selector
  ([dispatcher] (area-selector dispatcher "Cały korpus"))
  ([dispatcher nil-name]
   (let [contexts (subscribe [:contexts])]
     (into [:select {:on-change (dispatch-value dispatcher)}
            [:option nil-name]]
           (for [[opt _] @contexts]
             [:option {:value opt} opt])))))
