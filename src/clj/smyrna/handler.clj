(ns smyrna.handler
  (:require [hiccup.page :refer [include-js include-css html5]]
            [hiccup.core :refer [html]]
            [smyrna.middleware :refer [wrap-middleware]]
            [smyrna.corpus :as corpus]
            [smyrna.search :as search]
            [clojure.edn :as edn]
            [smyrna.meta :as meta]
            [compojure.core :refer [GET POST defroutes]]
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

(defn edn-response-raw
  ([body] (edn-response-raw {} body))
  ([extra-header body] {:status 200, :headers (into {"Content-Type" "application/edn; charset=UTF-8"} extra-header), :body body}))

(defn edn-response
  ([x] (edn-response {} x))
  ([extra-header x] (edn-response-raw extra-header (pr-str x))))

(defroutes routes
  (GET "/" [] loading-page)
  (GET "/meta-header" [] (edn-response (meta/get-header (:meta corpus))))
  (GET "/meta" [] (edn-response-raw {"Content-Encoding" "gzip"} (io/input-stream ((:raw-meta-fn corpus)))))
  (POST "/api/get-documents" {body :body} (let [params (edn/read-string (slurp body))]
                                            (edn-response (:results (search/get-documents corpus params)))))
  (POST "/api/get-contexts" [] (edn-response (vec (sort-by first (map (fn [[k v]] [k (:description v)]) @search/contexts)))))
  (POST "/api/create-context" {body :body} (let [{:keys [name description]} (edn/read-string (slurp body))]
                                             (search/create-context corpus name description)
                                             (edn-response "OK")))
  (GET "/corpus/*" [*]
       (let [k *
             k (if (.endsWith k ".html")
                 (subs k 0 (- (count k) (count ".html")))
                 k)
             i ((:key-index corpus) k)]
         (when i
           (html (corpus/deserialize (corpus/read-document corpus i)))))))

(def app (wrap-middleware #'routes))
