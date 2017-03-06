(defproject smyrna "0.3.0"
  :description "A lightweight concordancer for Polish."
  :url "http://smyrna.danieljanus.pl"
  :license {:name "MIT"
            :url "https://opensource.org/licenses/MIT"}
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [nio "1.0.4"]
                 [clj-tagsoup "0.3.0" :exclusions [org.clojure/clojure]]
                 [polelum "0.1.0-SNAPSHOT"]
                 [com.taoensso/timbre "4.8.0"]
                 [org.clojure/data.csv "0.1.3"]
                 [org.clojure/core.memoize "0.5.8"]
                 [org.carrot2/morfologik-fsa-builders "2.0.1"]
                 [ring "1.5.1"]
                 [ring/ring-defaults "0.2.2"]
                 [ring-server "0.4.0"]
                 [environ "1.1.0"]
                 [hiccup "1.0.5"]
                 [compojure "1.5.2"]
                 [re-frame "0.9.1"]
                 ;; ClojureScript dependencies
                 [org.clojure/clojurescript "1.9.293" :scope "provided"]
                 [reagent "0.6.0" :exclusions [org.clojure/tools.reader]]
                 [cljs-http "0.1.42"]
                 [cljsjs/fixed-data-table "0.6.3-0" :exclusions [cljsjs/react]]
                 [cljsjs/d3 "3.5.16-0" :exclusions [cljsjs/react]]]
  :plugins [[lein-environ "1.1.0"]
            [lein-cljsbuild "1.1.5"]
            [lein-asset-minifier "0.3.2" :exclusions [org.clojure/clojure]]
            [deraen/lein-less4j "0.6.1"]]
  :less {:source-paths ["src/css"]
         :target-path "resources/public/css"}
  :main smyrna.server
  :clean-targets ^{:protect false}
    [:target-path
     [:cljsbuild :builds :app :compiler :output-dir]
     [:cljsbuild :builds :app :compiler :output-to]]
  :source-paths ["src/clj"]
  :resource-paths ["resources" "target/cljsbuild" "target/resources"]
  :minify-assets {:assets
                  {"resources/public/css/root.min.css"
                   "resources/public/css/root.css"}}
  :cljsbuild {:builds {:app {:source-paths ["src/cljs"]
                             :compiler {:output-to "target/cljsbuild/public/js/app.js"
                                        :output-dir "target/cljsbuild/public/js/out"
                                        :asset-path "js/out"
                                        :optimizations :none
                                        :pretty-print true}}}}
  :profiles {:dev {:repl-options {:init-ns smyrna.repl}
                   :dependencies [[ring/ring-mock "0.3.0"]
                                  [ring/ring-devel "1.5.1"]
                                  [prone "1.1.4"]
                                  [weasel "0.7.0" :exclusions [org.clojure/clojurescript]]
                                  ;; Make sass4clj happy
                                  [org.slf4j/slf4j-nop "1.7.13" :scope "test"]
                                  [org.clojure/clojurescript "1.9.293"
                                   :exclusions [org.clojure/clojure org.clojure/tools.reader]]
                                  [org.clojure/tools.nrepl "0.2.12"]
                                  [binaryage/devtools "0.9.0"]
                                  [com.cemerick/piggieback "0.2.1"]]
                   :source-paths ["env/dev/clj"]
                   :env {:dev true}
                   :cljsbuild {:builds {:app {:source-paths ["env/dev/cljs"]
                                              :compiler {:main "smyrna.dev"
                                                         :preloads [devtools.preload]
                                                         :source-map true}}}}}
             :uberjar {:hooks [minify-assets.plugin/hooks]
                       :source-paths ["env/prod/clj"]
                       :prep-tasks ["compile" ["cljsbuild" "once"]]
                       :env {:production true}
                       :aot :all
                       :omit-source true
                       :uberjar-exclusions [#"goog" #"externs.zip" #"LICENSE" #"README.md" #"AUTHORS" #"re-frame"
                                            #"reagent" #"^pretty" #"leiningen.*" #"\.java" #"\.cljs" #"^css"
                                            #"^cognitect" #"^cljs" #"^com/cognitect" #"\.clj$"]
                       :cljsbuild {:jar true
                                   :builds {:app
                                            {:source-paths ["env/prod/cljs"]
                                             :compiler
                                             {:optimizations :advanced
                                              :pretty-print false}}}}}})
