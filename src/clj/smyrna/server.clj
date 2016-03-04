(ns smyrna.server
  (:require [smyrna.handler :refer [app]]
            [environ.core :refer [env]]
            [ring.server.standalone :refer [serve]]
            [ring.adapter.jetty :refer [run-jetty]])
  (:gen-class))

(defn -main [& args]
  (serve app {:port 6510, :join? false, :open-browser? true}))
