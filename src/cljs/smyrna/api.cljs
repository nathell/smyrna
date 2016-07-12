(ns smyrna.api
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [cljs-http.client :as http]
            [cljs.core.async :refer [<!]]))

(defn call [method params & [f]]
  (let [f (or f identity)]
    (go (f (:body (<! (http/post (str "/api/" method)
                                 {:edn-params (or params {})})))))))
