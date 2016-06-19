(defproject smyrna "0.3.0-SNAPSHOT"
  :description "A lightweight concordancer for Polish."
  :url "http://smyrna.danieljanus.pl"
  :license {:name "MIT"
            :url "https://opensource.org/licenses/MIT"}
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [nio "1.0.3"]
                 [clj-tagsoup "0.3.0" :exclusions [org.clojure/clojure]]
                 [polelum "0.1.0-SNAPSHOT"]
                 [com.taoensso/timbre "4.1.4"]
                 [org.clojure/data.csv "0.1.3"]
                 [org.clojure/core.memoize "0.5.8"]
                 [org.carrot2/morfologik-fsa-builders "2.0.1"]
                 [ring "1.4.0"]
                 [ring/ring-defaults "0.1.5"]
                 [ring-server "0.4.0"]
                 [environ "1.0.2"]
                 [hiccup "1.0.5"]
                 [compojure "1.4.0"]
                 [re-frame "0.7.0"]
                 ;; ClojureScript dependencies
                 [org.clojure/clojurescript "1.7.228" :scope "provided"]
                 [reagent "0.6.0-alpha" :exclusions [org.clojure/tools.reader]]
                 [cljs-http "0.1.39"]]
  :plugins [[lein-environ "1.0.2"]
            [lein-cljsbuild "1.1.1"]
            [lein-asset-minifier "0.2.4" :exclusions [org.clojure/clojure]]]
  :main smyrna.server
  :clean-targets ^{:protect false}
    [:target-path
     [:cljsbuild :builds :app :compiler :output-dir]
     [:cljsbuild :builds :app :compiler :output-to]]
  :source-paths ["src/clj" "src/cljc"]
  :resource-paths ["resources" "target/cljsbuild"]
  :minify-assets {:assets
                  {"resources/public/css/site.min.css"
                   "resources/public/css/site.css"}}
  :cljsbuild {:builds {:app {:source-paths ["src/cljs" "src/cljc"]
                             :compiler {:output-to "target/cljsbuild/public/js/app.js"
                                        :output-dir "target/cljsbuild/public/js/out"
                                        :asset-path "js/out"
                                        :optimizations :none
                                        :pretty-print true}}}}
  :profiles {:dev {:repl-options {:init-ns smyrna.repl}
                   :dependencies [[ring/ring-mock "0.3.0"]
                                  [ring/ring-devel "1.4.0"]
                                  [prone "1.0.2"]
                                  [weasel "0.7.0" :exclusions [org.clojure/clojurescript]]
                                  [org.clojure/clojurescript "1.7.228"
                                   :exclusions [org.clojure/clojure org.clojure/tools.reader]]
                                  [org.clojure/tools.nrepl "0.2.12"]
                                  [com.cemerick/piggieback "0.2.1"]]
                   :source-paths ["env/dev/clj"]
                   :env {:dev true}
                   :cljsbuild {:builds {:app {:source-paths ["env/dev/cljs"]
                                              :compiler {:main "smyrna.dev"
                                                         :source-map true}}}}}
             :uberjar {:hooks [minify-assets.plugin/hooks]
                       :source-paths ["env/prod/clj"]
                       :prep-tasks ["compile" ["cljsbuild" "once"]]
                       :env {:production true}
                       :aot :all
                       :omit-source true
                       :cljsbuild {:jar true
                                   :builds {:app
                                            {:source-paths ["env/prod/cljs"]
                                             :compiler
                                             {:optimizations :advanced
                                              :pretty-print false}}}}}})
