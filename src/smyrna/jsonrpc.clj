(ns smyrna.jsonrpc
  (:require [clojure.contrib.json :as json]
            [clojure.java.io :as io]))

(defn answer-json [obj]
  {:status 200
   ;; the "Content-Type" should not be case-altered!
   :headers {"Content-Type" "application/json; charset=utf-8"}
   :body (json/json-str obj)})

(defmacro assert-msg [x msg]
  `(when-not ~x
     (throw (Exception. ~msg))))

(def *json-rpc-ns* (find-ns 'fablo.web))

(defn process-json-rpc [req]
  (let [{:keys [id method params]} (-> req :body io/reader json/read-json)
        idify (if id #(assoc % :id id) identity)]
    (try
     (let [method (symbol method)
           f (ns-resolve *json-rpc-ns* method)
           _ (assert-msg (:json-rpc (meta f)) "Method not available")]
       (answer-json (idify {:result (apply f ((req :session) "logged-in") params)})))
     (catch Exception e
       (answer-json (idify {:error (.getMessage e)}))))))

(defmacro defn-json-rpc [name & rest]
  `(do
     (alter-var-root #'*json-rpc-ns* (fn [_#] *ns*))
     (defn ~name {:json-rpc true} ~@rest)))