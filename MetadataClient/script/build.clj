(require 'cljs.build.api)

(cljs.build.api/build
  (cljs.build.api/inputs "src/cljs" "env/dev/cljs")
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
   :parallel-build true
   :compiler-stats true
   :verbose true})

(System/exit 0)
