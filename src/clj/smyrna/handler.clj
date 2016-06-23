(ns smyrna.handler
  (:require [hiccup.page :refer [include-js include-css html5]]
            [hiccup.core :refer [html]]
            [smyrna.middleware :refer [wrap-middleware]]
            [smyrna.build :as build]
            [smyrna.corpus :as corpus]
            [smyrna.search :as search]
            [smyrna.task :as task]
            [clojure.edn :as edn]
            [smyrna.meta :as meta]
            [compojure.core :refer [GET POST defroutes]]
            [clojure.java.io :as io]
            [clojure.data.csv :as csv]
            [taoensso.timbre :refer [infof]]
            [environ.core :refer [env]]))

; (def corpus (corpus/open "p4corpus.zip"))
(def corpus (corpus/open "corpus8.zip"))

(def loading-page
  (html5
   [:head
    [:meta {:charset "utf-8"}]
    [:meta {:name "viewport"
            :content "width=device-width, initial-scale=1"}]
    [:title "Smyrna"]
    (include-css (if (env :dev) "css/site.css" "css/site.min.css"))]
    [:body
     [:div#app
      [:div {:style "display: flex; height: 100%;"}
       [:h1 {:style "margin: auto;"} "Trwa uruchamianie Smyrny, proszę czekać..."]]]
     (include-js "js/d3.js")
     (include-js "js/d3.layout.cloud.js")
     (include-js "js/app.js")]))

(defn edn-response-raw
  ([body] (edn-response-raw {} body))
  ([extra-header body] {:status 200, :headers (into {"Content-Type" "application/edn; charset=UTF-8"} extra-header), :body body}))

(defn edn-response
  ([x] (edn-response {} x))
  ([extra-header x] (edn-response-raw extra-header (pr-str x))))

(def styles
  "<style>
.didaskalia::before { content: \" \"; }
.didaskalia::after { content: \" \"; }
.didaskalia { font-style: italic; }
.match { background-color: yellow; margin: 0 5px; }
</style>")

(defn files
  [dir]
  (->>
   (for [f (.listFiles (io/file dir))
         :let [name (.getName f)
               dir? (.isDirectory f)]
         :when (or dir? (.endsWith name ".csv"))]
     {:file name, :dir (.isDirectory f)})
   (sort-by :file)))

(defroutes routes
  (GET "/" [] loading-page)
  (GET "/meta-header" [] (edn-response (meta/get-header (:meta corpus)))) ;; OBSOLETE
  (GET "/meta" [] (edn-response-raw {"Content-Encoding" "gzip"} (io/input-stream ((:raw-meta-fn corpus)))))
  (POST "/api/get-documents" {body :body} (let [params (edn/read-string (slurp body))]
                                            (edn-response (:results (search/get-documents corpus params)))))
  (POST "/api/get-corpus-info" []
        (edn-response
         {:metadata (meta/get-header (:meta corpus)),
          :corpora-list (corpus/list-corpora),
          :contexts (vec (sort-by first (map (fn [[k v]] [k (:description v)]) @search/contexts)))}))
  (POST "/api/get-task-info" []
        (edn-response (task/get-info)))
  (GET "/frequency-list/:area" [area]
        {:status 200,
         :headers {"Content-Type" "text/csv; charset=utf-8",
                   "Content-Disposition" (format "attachment; filename=\"lista-frekwencyjna-%s.csv\"" area)},
         :body (with-out-str (csv/write-csv *out* (search/frequency-list corpus area)))})
  (POST "/api/frequency-list" {body :body}
        (let [{:keys [context offset limit]} (edn/read-string (slurp body))]
          (edn-response (search/frequency-list corpus context limit offset))))
  (POST "/api/get-contexts" [] (edn-response (vec (sort-by first (map (fn [[k v]] [k (:description v)]) @search/contexts))))) ;; OBSOLETE
  (POST "/api/create-context" {body :body}
        (let [{:keys [name description]} (edn/read-string (slurp body))]
          (search/create-context corpus name description)
          (edn-response "OK")))
  (POST "/api/compare-contexts" {body :body}
        (let [[c1 c2] (edn/read-string (slurp body))]
          (edn-response (search/compare-contexts corpus c1 c2))))
  (POST "/api/tree" {body :body}
        (let [path (edn/read-string (slurp body))]
          (edn-response (files path))))
  (POST "/api/create-corpus" {body :body}
        (let [{:keys [name file]} (edn/read-string (slurp body))]
          (task/launch (build/build name file))
          (edn-response "OK")))
  (GET "/highlight/:phrase/*" [phrase *]
       (let [i ((:key-index corpus) *)]
         (when i
           (let [doc (corpus/read-document corpus i :lookup false)]
             (str styles
                  (html (corpus/deserialize (search/highlight-doc corpus doc phrase))))))))
  (GET "/corpus/*" [*]
       (let [k *
             k (if (.endsWith k ".html")
                 (subs k 0 (- (count k) (count ".html")))
                 k)
             i ((:key-index corpus) k)]
         (when i
           (str styles
                (html (corpus/deserialize (corpus/read-document corpus i))))))))

(def app (wrap-middleware #'routes))
