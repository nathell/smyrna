(ns smyrna.web
  (:use [smyrna.jsonrpc :only [defn-json-rpc process-json-rpc]]
        [hiccup.core :only [html]])
  (:require [smyrna.core :as core]
            [pl.danieljanus.tagsoup :as tagsoup]
            [ring.adapter.jetty :as jetty]
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

(defn handler [req]
  (condp = (:uri req)
    "/" (response/resource-response "index.html")
    "/json-rpc" (process-json-rpc req)
    (response/resource-response (:uri req))))

(defn start-server []
  (when-not @srv
    (reset! srv
            (jetty/run-jetty handler {:port 8080 :join? false}))))

(defn stop-server []
  (when @srv
    (.stop @srv)
    (reset! srv nil)))
