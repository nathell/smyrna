(ns smyrna.web
  (:use [smyrna.jsonrpc :only [defn-json-rpc process-json-rpc]]
        [ring.middleware.params :only [wrap-params]]
        [clojure.contrib.seq-utils :only [separate]]
        [hiccup.core :only [html]])
  (:require [smyrna.core :as core]
            [pl.danieljanus.tagsoup :as tagsoup]
            [ring.adapter.jetty :as jetty]
            [clojure.string :as string]
            [ring.util.response :as response]))

(def w (atom nil))

(defn-json-rpc highlight [q & [doc-nr]]
  (let [docs (-> @w :lemma-index (get q) sort vec)
        doc (get docs (or doc-nr 0))
        file (when doc (-> @w :files (get doc)))]
    {:count (count docs),
     :html (when file
             (html (core/highlight (tagsoup/parse file) q)))}))

(def srv (atom nil))

(defn extension [f]
  (or (-> f .getName (string/split #"\.") next last) ""))

(defn directory [req]
  (let [dir ((:form-params req) "dir")
        _ (prn dir)
        f (java.io.File. dir)
        [dirs files] (map (partial sort-by #(.getName %)) (separate #(.isDirectory %) (.listFiles f)))
        result 
          (apply str
                 (flatten
                  ["<ul class='jqueryFileTree' style='display: none;'>"
                   (map #(format "<li class='directory collapsed'><a href='#' rel='%s/'>%s</a></li>" (str %) (.getName %)) dirs)
                   (map #(format "<li class='file ext_%s'><a href='#' rel='%s/'>%s</a></li>" (extension %) (str %) (.getName %)) files)
                   "</ul>"]))]
    {:status 200,
     :headers {"Content-Type" "text/html; charset=utf-8"},
     :body result}))
     
(defn handler [req]
  (condp = (:uri req)
    "/" (response/resource-response "index.html")
    "/json-rpc" (process-json-rpc req)
    "/dir" ((wrap-params directory) req)
    (response/resource-response (:uri req))))

(defn start-server []
  (when-not @srv
    (reset! srv
            (jetty/run-jetty handler {:port 8080 :join? false}))))

(defn stop-server []
  (when @srv
    (.stop @srv)
    (reset! srv nil)))
