(ns smyrna.dev
  (:require [smyrna.core :as core]
            [weasel.repl :as repl]))

(enable-console-print!)

(when-not (repl/alive?)
  (repl/connect "ws://localhost:9001"))

(core/init!)
