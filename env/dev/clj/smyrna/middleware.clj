(ns smyrna.middleware
  (:require [ring.middleware.defaults :refer [api-defaults wrap-defaults]]
            [prone.middleware :refer [wrap-exceptions]]
            [ring.middleware.reload :refer [wrap-reload]]))

(def defaults
  (-> api-defaults
      (assoc-in [:static :resources] "public")))

(defn wrap-middleware [handler]
  (-> handler
      (wrap-defaults defaults)
      wrap-exceptions
      wrap-reload))
