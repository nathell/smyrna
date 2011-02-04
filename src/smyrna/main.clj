(ns smyrna.main
  (:gen-class)
  (:use [clojure.contrib.shell-out :only [sh]])
  (:require [smyrna.web :as web]))

(defn chrome-dir []
  (try
    (let [output (sh "reg" "query" "HKEY_CLASSES_ROOT\\ChromeHTML\\DefaultIcon")]
      (when-let [val (nth (.split output "\r\n") 4)]
	(let [path (last (.split val "\t"))]
	  (subs path 0 (- (count path) 2)))))
    (catch Exception _ nil)))

(defn try-running [cmd]
  (try
   (.exec (Runtime/getRuntime) cmd)
   (catch java.io.IOException _ nil)))

(defn browse-url [url]     
  (let [try-browsing #(try-running (str % url))]
    (or (try-browsing (str (or (chrome-dir) "chrome") " --app="))
	(try-browsing "rundll32 url.dll,FileProtocolHandler ")
        (try-browsing "chromium-browser --app=")
	(try-browsing "x-www-browser ")
        (try-browsing "firefox "))))

(defn -main [& args]
  (try 
    (web/start-server)
    (catch java.net.BindException _ nil))
  (browse-url "http://localhost:8080"))
