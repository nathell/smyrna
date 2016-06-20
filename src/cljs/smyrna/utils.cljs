(ns smyrna.utils
  (:require-macros [reagent.ratom :refer [reaction]])
  (:require [re-frame.core :as re-frame :refer [register-handler path register-sub dispatch dispatch-sync subscribe]]
            [smyrna.api :as api]))

(defn register-setter
  ([k] (register-setter k (keyword (str "set-" (name k)))))
  ([k handler-name]
   (register-handler handler-name
                     (fn [state [_ new-value]]
                       (assoc state k new-value)))))

(defn register-getter
  [k]
  (register-sub k #(reaction (k @%1))))

(defn register-accessors
  [& ks]
  (doseq [k ks]
    (register-setter k)
    (register-getter k)))

(defn dispatch-value
  [ev]
  #(dispatch [ev (-> % .-target .-value)]))

;; common components:

(defn area-selector
  ([dispatcher] (area-selector dispatcher "Cały korpus"))
  ([dispatcher nil-name]
   (let [contexts (subscribe [:contexts])]
     (fn render-area-selector []
       (into [:select {:on-change (dispatch-value dispatcher)}
              [:option nil-name]]
             (for [[opt _] @contexts]
               [:option {:value opt} opt]))))))
