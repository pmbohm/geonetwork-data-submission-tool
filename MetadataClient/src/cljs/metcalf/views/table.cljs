(ns metcalf.views.table
  (:require [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [sablono.core :refer-macros [html]]
            [clojure.string :as string]
            [metcalf.globals :refer [observe-path]]
            [condense.fields :refer [Input Checkbox del-value! add-value!]]
            cljsjs.fixed-data-table
            select-om-all.utils
            goog.dom
            goog.dom.ViewportSizeMonitor
            goog.events
            goog.events.EventType
            goog.style))

(def Table (js/React.createFactory js/FixedDataTable.Table))
(def Column (js/React.createFactory js/FixedDataTable.Column))
(def ColumnGroup (js/React.createFactory js/FixedDataTable.ColumnGroup))

(defn getter [k row] (get row k))

(defn update-table-width [owner]
  (let [autowidth (om/get-node owner "autowidth")
        width (.-width (goog.style.getSize autowidth))]
    (om/set-state! owner :width width)))

(defn KeywordsThemeCell [rowData owner]
  (reify
    om/IDisplayName (display-name [_] "KeywordThemeCell")
    om/IRender
    (render [_]
      (let [rowData (take-while (complement empty?) rowData)]
        (html [:div.topic-cell
               [:div.topic-path (string/join " > " (drop-last (rest rowData)))]
               [:div.topic-value (last rowData)]])))))

(defn KeywordsThemeTable [props owner]
  (reify
    om/IDisplayName (display-name [_] "TestThemeTable")
    om/IInitState (init-state [_]
                    {:columnWidths     [26 (- 900 26)]
                     :isColumnResizing false
                     :query            ""
                     ;:selected-filter  false
                     :width            900
                     :scrollToRow      0})
    om/IDidMount
    (did-mount [_]
      (let [vsm (goog.dom.ViewportSizeMonitor.)]
        (goog.events.listen vsm goog.events.EventType.RESIZE #(update-table-width owner))
        (update-table-width owner)))
    om/IRenderState
    (render-state [_ {:keys [query width columnWidths isColumnResizing scrollToRow]}]
      (let [keywords (observe-path owner [:form :fields :identificationInfo :keywordsTheme :keywords])
            uuids (zipmap (map :value (:value keywords)) (range))
            table (observe-path owner [:theme :table])
            search-fn (partial select-om-all.utils/default-local-search false)
            results (if (string/blank? query)
                      table
                      (vec (search-fn (map (juxt rest identity) table) query)))
            rowHeight 50]
        (html [:div.KeywordsThemeTable
               (om/build Input {:label    "Search"
                                :value    query
                                :onChange #(do
                                            (om/set-state-nr! owner :scrollToRow 0)
                                            (om/set-state! owner :query (.. % -target -value)))})
               #_(om/build Checkbox {:label "Selected keywords only"
                                     :checked selected-filter
                                     :on-change #(om/set-state! owner :selected-filter (not selected-filter))})
               [:div {:ref "autowidth"}
                (Table
                  #js {:width                     width
                       :maxHeight                 400
                       :rowHeight                 rowHeight
                       :rowGetter                 #(get results %)
                       :rowsCount                 (count results)
                       :headerHeight              30
                       :onColumnResizeEndCallback #(do (om/set-state! owner [:columnWidths %2] (max %1 5))
                                                       (om/set-state! owner :isColumnResizing false))
                       :overflowX                 "hidden"
                       :scrollToRow               scrollToRow
                       :onScrollEnd               #(om/set-state! owner :scrollToRow (mod %2 rowHeight))
                       :isColumnResizing          isColumnResizing}
                  (Column
                    #js {:label          ""
                         :dataKey        0
                         :align          "right"
                         :cellDataGetter getter
                         :cellRenderer   #(om/build Checkbox {:checked   (contains? uuids %)
                                                              :on-change (fn [_]
                                                                           (if (contains? uuids %)
                                                                             (del-value! keywords (uuids %))
                                                                             (add-value! keywords %)))})
                         :width          (get columnWidths 0)
                         :isResizable    true})
                  (Column
                    #js {:label          "Topic"
                         :cellDataGetter getter
                         :dataKey        1
                         :cellRenderer   (fn [cellData dataKey rowData]
                                           (om/build KeywordsThemeCell rowData))
                         :flexGrow       1
                         :width          (get columnWidths 1)
                         :isResizable    true}))]
               [:p "There are " (count table) " keywords in our database"]])))))
