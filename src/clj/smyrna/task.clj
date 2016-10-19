(ns smyrna.task)

(def info (atom nil))

(defn set-info [x]
  (when-not (= x @info)
    (println x)
    (reset! info x)))

(defn get-info []
  @info)

(defn launch* [f]
  (set-info :in-progress)
  (future
    (try
      (f)
      (finally
        (set-info nil)))))

(defmacro launch [& body]
  `(launch* (fn [] ~@body)))
