(ns smyrna.handler
  (:require [hiccup.page :refer [include-js include-css html5]]
            [smyrna.middleware :refer [wrap-middleware]]
            [environ.core :refer [env]]))

(def loading-page
  (html5
   [:head
    [:meta {:charset "utf-8"}]
    [:meta {:name "viewport"
            :content "width=device-width, initial-scale=1"}]
    (include-css (if (env :dev) "css/site.css" "css/site.min.css"))]
    [:body
     [:div#app
      [:h1 "Loading Smyrna, please wait..."]]
     (include-js "js/app.js")]))

(def not-found
  (html5
   [:h1 "Not found"]))

(defn main-handler [req]
  (if (= (:uri req) "/")
    {:status 200, :headers {"Content-Type" "text/html"}, :body loading-page}
    {:status 404, :headers {"Content-Type" "text/html"}, :body not-found}))

(def app (wrap-middleware #'main-handler))
