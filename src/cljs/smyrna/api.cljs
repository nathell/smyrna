(ns smyrna.api
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [cljs-http.client :as http]
            [cljs.core.async :refer [<!]]
            [re-frame.core :refer [reg-fx dispatch]]))

(defn call [[method params & eventv]]
  (let [callback (if (seq eventv)
                   #(dispatch (conj (vec eventv) %))
                   identity)]
    (go (callback (:body (<! (http/post (str "/api/" method)
                                        {:edn-params (or params {})})))))))

(reg-fx :api call)
