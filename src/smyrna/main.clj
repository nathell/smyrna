(ns smyrna.main
  (:gen-class)
  (:require [smyrna.web :as web]))

(defn try-running [cmd]
  (try
   (.exec (Runtime/getRuntime) cmd)
   (catch java.io.IOException _ nil)))

(defn browse-url [url]     
  (let [try-browsing #(try-running (str % " " url))]
    (or (try-browsing "rundll32 url.dll,FileProtocolHandler")
        (try-browsing "x-www-browser")
        (try-browsing "chromium-browser")
        (try-browsing "firefox"))))

(defn -main [& args]
  (web/start-server)
  (browse-url "http://localhost:8080"))
  
