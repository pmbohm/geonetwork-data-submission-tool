(require 'cljs.build.api)

(cljs.build.api/build
  (cljs.build.api/inputs "src/cljs" "env/prod/cljs")
  {:main 'metcalf.core
   :output-to "out/main.js"
   :foreign-libs   [{:file     "lib/react-select.js"
                     ;:file-min "resources/lib/react-select.min.js"
                     :requires ["cljsjs.react" "cljsjs.classnames" "cljsjs.react-input-autosize"]
                     :provides ["cljsjs.react-select"]}
                    {:file "lib/classnames.js"
                     :provides ["cljsjs.classnames"]}
                    {:file "lib/react-input-autosize.js"
                     ;:file-min "resources/lib/react-input-autosize.min.js"
                     :requires ["cljsjs.react" "cljsjs.lodash"]
                     :provides ["cljsjs.react-input-autosize"]}]
   :externs          ["ext/react-select.ext.js"]
   :optimizations    :advanced
   :closure-warnings {:externs-validation :off
                      :non-standard-jsdoc :off}
   ;:closure-defines {:goog.DEBUG false
   ;                  :ol.ENABLE_DOM false
   ;                  :ol.ENABLE_VECTOR false
   ;                  :ol.ENABLE_PROJ4JS false
   ;                  :ol.ENABLE_WEBGL false}
   :parallel-build true
   :compiler-stats true
   :verbose true})

(System/exit 0)
