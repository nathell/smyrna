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

(def corpora (atom {}))

(defn getc
  [x]
  (when x ;; FIXME: corpus/open creates an empty corpus file
    (let [x (if (string? x) (Long/parseLong x) x)
          name (@corpus/corpus-keys x)]
      (or (@corpora name)
          (let [path (format "%s/%s.smyrna" corpus/corpora-path name)
                corpus (corpus/open path)]
            (swap! corpora assoc name corpus)
            corpus)))))

(def loading-page
  (html5
   [:head
    [:meta {:charset "utf-8"}]
    [:meta {:name "viewport"
            :content "width=device-width, initial-scale=1"}]
    [:title "Smyrna"]
    (include-css (if (env :dev) "css/root.css" "css/root.min.css"))]
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
.selected { font-size: 150%; }
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

(defmacro api
  [f params & body]
  (let [url (str "/api/" (name f))
        content `(edn-response (do ~@body))
        req-body `body#]
    `(POST ~url ~(if (not= params []) `{~req-body :body} [])
           ~(if (not= params [])
              `(let [~params (edn/read-string (slurp ~req-body))]
                 ~content)
              content))))

(defroutes routes
  (GET "/" [] loading-page)
  ;; (GET "/frequency-list/:area" [area]
  ;;       {:status 200,
  ;;        :headers {"Content-Type" "text/csv; charset=utf-8",
  ;;                  "Content-Disposition" (format "attachment; filename=\"lista-frekwencyjna-%s.csv\"" area)},
  ;;        :body (with-out-str (csv/write-csv *out* (search/frequency-list corpus area)))})
  (GET "/highlight/:corpus/:phrase/*" [corpus phrase *]
       (let [corpus (getc corpus)
             i (.indexOf (:paths corpus) *)]
         (when (>= i 0)
           (let [doc (corpus/read-document corpus i :lookup false)]
             (str styles
                  (html (corpus/deserialize (search/highlight-doc corpus doc phrase))))))))
  (GET "/corpus/:corpus/*" [corpus *]
       (let [corpus (getc corpus)
             i (.indexOf (:paths corpus) *)]
         (when (>= i 0)
           (str styles
                (html (corpus/deserialize (corpus/read-document corpus i)))))))
  (api get-corpora []
       (corpus/list-corpora))
  (api get-documents params
       (:results (search/get-documents (getc (:corpus params)) params)))
  (api get-corpus-info {:keys [corpus]}
       {:metadata (meta/get-header (:meta (getc corpus))),
        :contexts (vec (sort-by first (map (fn [[k v]] [k (:description v)]) @search/contexts)))})
  (api get-task-info []
       (task/get-info))
  (api frequency-list {:keys [context corpus]}
       (search/frequency-list (getc corpus) context))
  (api create-context {:keys [name description corpus]}
       (search/create-context (getc corpus) name description)
       "OK")
  (api compare-contexts [c1 c2 corpus]
       (search/compare-contexts (getc corpus) c1 c2))
  (api tree path
       (files path))
  (api create-corpus {:keys [name file]}
       (task/launch (build/build name file))
       "OK"))

(def app (wrap-middleware #'routes))
