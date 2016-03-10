(ns smyrna.middleware
  (:require [ring.middleware.defaults :refer [api-defaults wrap-defaults]]))

(def defaults
  (-> api-defaults
      (assoc-in [:static :resources] "public")))

(defn wrap-middleware [handler]
  (wrap-defaults handler defaults))
