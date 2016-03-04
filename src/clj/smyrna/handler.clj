(ns smyrna.handler
  (:require [hiccup.page :refer [include-js include-css html5]]
            [hiccup.core :refer [html]]
            [smyrna.middleware :refer [wrap-middleware]]
            [smyrna.corpus :as corpus]
            [compojure.core :refer [GET defroutes]]
            [clojure.java.io :as io]
            [environ.core :refer [env]]))

(def corpus (corpus/open "p4corpus.zip"))

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

(defroutes routes
  (GET "/" [] loading-page)
  (GET "/meta" [] {:status 200
                   :headers {"Content-Type" "application/edn"
                             "Content-Encoding" "gzip"}
                   :body (io/input-stream ((:raw-meta-fn corpus)))})
  (GET "/corpus/*" [*]
       (let [k *
             k (if (.endsWith k ".html")
                 (subs k 0 (- (count k) (count ".html")))
                 k)]
         (html (corpus/deserialize (corpus/read-document corpus (corpus/locate-by-key (:meta corpus) k)))))))

(def app (wrap-middleware #'routes))
