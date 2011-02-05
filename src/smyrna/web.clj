(ns smyrna.web
  (:use [smyrna.jsonrpc :only [defn-json-rpc process-json-rpc]]
        [ring.middleware.params :only [wrap-params]]
        [clojure.contrib.seq-utils :only [separate]]
        [hiccup.core :only [html]]
        [clojure.java.io :only [file writer reader]])
  (:require [smyrna.core :as core]
            [pl.danieljanus.tagsoup :as tagsoup]
            [ring.adapter.jetty :as jetty]
            [clojure.string :as string]
            [ring.util.response :as response]))

(def state (atom {}))

(defn write-to [file datum]
  (with-open [stream (writer file)]
    (binding [*out* stream]
      (prn datum))))

(defn read-from [file]
  (with-open [stream (java.io.PushbackReader. (reader file))]
    (read stream)))

(def config-file (str (System/getProperty "user.home") java.io.File/separator ".smyrna.dat"))

(defn try-to-load-state []
  (try
    (reset! state (read-from config-file))
    (catch Exception _ nil)))

(defn-json-rpc highlight [corpus q & [doc-nr]]
  (let [index (-> @state (get corpus) :index)
	docs (-> index :lemma-index (get q) sort vec)
        doc (get docs (or doc-nr 0))
        file (when doc (-> index :files (get doc)))]
    {:count (count docs),
     :numword (-> index :lemma-global-frequency (get q)),
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
	  (future (write-to config-file @state))
          true))))

(defn-json-rpc get-corpora []
  (sort-by :name
           (map (fn [x] {:name x, :files ((@state x) :num-files)}) (keys @state))))

(defn-json-rpc frequency-list [corpus]
  (sort-by second >
	   (into [] (map vec (-> @state (get corpus) :index :lemma-global-frequency)))))

(def srv (atom nil))

(defn directory [req]
  (let [dir ((:form-params req) "dir")
	dirs (if (empty? dir)
	       (java.io.File/listRoots)
	       (sort-by #(.getName %) (filter #(.isDirectory %) (.listFiles (file dir)))))	
        result 
          (apply str
                 (flatten
                  ["<ul class='jqueryFileTree' style='display: none;'>"
                   (map #(format "<li class='directory collapsed'><a href='#' rel='%s/'>%s</a></li>" (str %) (if (empty? dir) (str %) (.getName %))) dirs)
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
