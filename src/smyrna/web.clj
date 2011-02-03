(ns smyrna.web
  (:use [smyrna.jsonrpc :only [defn-json-rpc process-json-rpc]]
        [ring.middleware.params :only [wrap-params]]
        [clojure.contrib.seq-utils :only [separate]]
        [hiccup.core :only [html]]
        [clojure.java.io :only [file]])
  (:require [smyrna.core :as core]
            [pl.danieljanus.tagsoup :as tagsoup]
            [ring.adapter.jetty :as jetty]
            [clojure.string :as string]
            [ring.util.response :as response]))

(def state (atom {}))

(defn-json-rpc highlight [corpus q & [doc-nr]]
  (let [index (-> @state (get corpus) :index)
	docs (-> index :lemma-index (get q) sort vec)
        doc (get docs (or doc-nr 0))
        file (when doc (-> index :files (get doc)))]
    {:count (count docs),
     :html (when file
             (html (core/highlight (tagsoup/parse file) q)))}))

(defn extension [f]
  (or (-> f .getName (string/split #"\.") next last) ""))

(defn-json-rpc add-corpus [name directory]
  (let [files (filter #(#{"html" "htm" "HTML" "HTM"} (extension %)) (file-seq (file directory)))]
    (cond
      (empty? files)
        "Wskazany katalog nie zawiera plików HTML."
      (@state name)
        "Istnieje już korpus o tej nazwie."
      true
        (do
          (swap! state assoc name
                 {:directory directory
                  :num-files (count files)
                  :index (core/index-fileset files)})
          true))))

(defn-json-rpc get-corpora []
  (sort-by :name
           (map (fn [x] {:name x, :files ((@state x) :num-files)}) (keys @state))))

(def srv (atom nil))

(defn directory [req]
  (let [dir ((:form-params req) "dir")
        f (file dir)
        [dirs files] (map (partial sort-by #(.getName %)) (separate #(.isDirectory %) (.listFiles f)))
        result 
          (apply str
                 (flatten
                  ["<ul class='jqueryFileTree' style='display: none;'>"
                   (map #(format "<li class='directory collapsed'><a href='#' rel='%s/'>%s</a></li>" (str %) (.getName %)) dirs)
                   #_ (map #(format "<li class='file ext_%s'><a href='#' rel='%s'>%s</a></li>" (extension %) (str %) (.getName %)) files)
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
