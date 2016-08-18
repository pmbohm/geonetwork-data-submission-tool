(defproject imas-metcalf "2.0.0"
  :description "Metadata management tool designed for academics"
  :url "https://github.com/IMASau/geonetwork-data-submission-tool"
  :license {:name "Eclipse Public License"
            :url  "http://www.eclipse.org/legal/epl-v10.html"}

  :source-paths ["src/cljs" "script"]

  :clean-targets ^{:protect false} [:target-path
                                    "resources/public/js/dev"
                                    "resources/public/js/dev.js"
                                    "resources/public/js/prod"
                                    "resources/public/js/prod.js"]

  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.clojure/clojurescript "1.9.89"]
                 [org.clojure/test.check "0.9.0"]
                 [org.clojure/core.async "0.2.385"]
                 [org.clojure/core.match "0.3.0-alpha4"]
                 [org.omcljs/om "1.0.0-alpha37"]
                 [sablono "0.7.2"]
                 [cljsjs/fixed-data-table "0.6.0-1"]
                 [cljsjs/pikaday "1.4.0-1"]
                 [cljsjs/moment "2.10.6-4"]
                 [cljsjs/lodash "4.11.2-0"]                 ; react-select dep
                 [cljsjs/google-maps "3.18-1"]
                 [cljsjs/react-virtualized "7.8.3-0"]
                 [cljsjs/react-select "1.0.0-beta13-0"]
                 [funcool/cuerdas "0.7.2"]
                 [com.taoensso/encore "2.52.1"]
                 [tailrecursion/cljs-priority-map "1.1.0"]
                 [cljs-ajax "0.5.4"]
                 [com.andrewmcveigh/cljs-time "0.4.0"]
                 [binaryage/devtools "0.7.0"]]

  :plugins [[lein-cljsbuild "1.1.3"]
            [lein-marginalia "0.9.0"]]

  :min-lein-version "2.5.0"

  :cljsbuild {:builds        {:app {:source-paths ["src/cljs"]
                                    :compiler     {:output-to      "resources/public/js/dev.js"
                                                   :output-dir     "resources/public/js/dev"
                                                   :source-map     true
                                                   :cache-analysis true
                                                   :optimizations  :none
                                                   :pretty-print   true
                                                   :foreign-libs   []}}}
              :test-commands {}}

  :profiles {:dev     {:dependencies [[figwheel-sidecar "0.5.4-5"]
                                      [com.cemerick/piggieback "0.2.1"]]
                       :plugins      [[lein-figwheel "0.5.4-5"]]

                       :figwheel     {:http-server-root  "public"
                                      :server-port       3449
                                      :nrepl-port        7888
                                      :css-dirs          ["resources/public/css"]
                                      :open-file-command "open-in-intellij"}

                       :cljsbuild    {:builds {:app {:source-paths ["env/dev/cljs" "src/spec"]
                                                     :figwheel     {:on-jsload "metcalf.dev/test-and-main"}}}}}

             :uberjar {:hooks       [leiningen.cljsbuild]
                       :omit-source true
                       :aot         :all
                       :cljsbuild   {:builds {:app
                                              {:source-paths ["env/prod/cljs"]
                                               :compiler
                                                             {:output-to        "resources/public/js/prod.js"
                                                              :output-dir       "resources/public/js/prod"
                                                              :source-map       "resources/public/js/prod.js.map"
                                                              :optimizations    :advanced
                                                              :pretty-print     false
                                                              ;:pseudo-names     true
                                                              :closure-warnings {:externs-validation :off
                                                                                 :non-standard-jsdoc :off}}}}}}})
