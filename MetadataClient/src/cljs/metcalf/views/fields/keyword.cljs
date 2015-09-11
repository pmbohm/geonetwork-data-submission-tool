(ns metcalf.views.fields.keyword
  (:require [cljs.core.async :as async :refer [<! timeout]]
            [cljs.core.match :refer-macros [match]]
            [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [sablono.core :refer-macros [html]]
            [metcalf.globals :refer [observe-path]]
            [om-tick.bootstrap :refer [Select validation-state]]
            [condense.fields :refer [Input Checkbox ExpandingTextarea validate-required-field
                                     help-block-template label-template
                                     del-value! add-value! add-field!]]
            [metcalf.views.highlight :refer [handle-highlight-new]]
            [metcalf.views.modal :refer [Modal]]
            [condense.utils :refer [enum]]
            [clojure.string :as string]
            cljsjs.fixed-data-table
            select-om-all.utils
            select-om-all.core
            goog.dom
            goog.dom.ViewportSizeMonitor
            goog.events
            goog.events.EventType
            goog.style
            [metcalf.handlers :as handlers]))


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


(defn ThemeKeywords [_ owner]
  (reify
    om/IDisplayName (display-name [_] "ThemeKeywords")
    om/IInitState (init-state [_] {:new-value nil
                                   :show-modal false
                                   :highlight #{}})
    om/IRenderState
    (render-state [_ {:keys [new-value show-modal highlight]}]
      (let [{:keys [keywords]} (observe-path owner [:form :fields :identificationInfo :keywordsTheme])
            {:keys [value placeholder disabled] :as props} keywords
            theme-options (observe-path owner [:theme :options])
            theme-table (observe-path owner [:theme :table])
            set-value! #(om/set-state! owner :new-value %)
            add! (fn [new-value] (when-not (empty? new-value)
                                   (let [uuid (first new-value)]
                                     (when (not-any? (comp #{uuid} :value)
                                                     (:value keywords))
                                       (add-value! keywords uuid)))
                                   (handle-highlight-new owner new-value)
                                   (set-value! nil)))
            lookup (fn [uuid] (first (filterv #(= uuid (first %)) theme-table)))
            show-modal! #(om/set-state! owner :show-modal true)
            hide-modal! #(om/set-state! owner :show-modal false)]
        (html [:div.ThemeKeywords {:class (validation-state props)}
               (if show-modal (om/build Modal (assoc props
                                                :ok-copy "OK"
                                                :dialog-class "modal-lg"
                                                :modal-header (html [:span [:span.glyphicon.glyphicon-list] " " "Research theme keywords"])
                                                :modal-body (html [:div
                                                                   [:p.help-block "Select keyword(s) to add to record"]
                                                                   (om/build KeywordsThemeTable nil)])
                                                :on-dismiss #(hide-modal!)
                                                :hide-footer true)))
               (label-template props)
               (help-block-template props)
               [:table.table.keyword-table {:class (if-not disabled "table-hover")}
                [:tbody
                 (for [[i keyword] (enum value)]
                   [:tr {:class (if disabled "active" (if (highlight (:value keyword)) "highlight"))}
                    [:td (om/build KeywordsThemeCell (lookup (:value keyword)))]
                    (if-not disabled
                      [:td [:button.btn.btn-warn.btn-xs.pull-right
                            {:on-click #(del-value! keywords i)}
                            [:span.glyphicon.glyphicon-minus]]])])]]
               (if-not disabled
                 [:div.row
                  [:div.col-sm-8
                   (om/build select-om-all.core/AutoComplete
                             {:placeholder placeholder

                              :value       ""
                              :datasource  theme-table
                              :get-cols    (fn [x]
                                             [(om/build KeywordsThemeCell x)])
                              :rowHeight   50
                              :index-fn    rest
                              :display-fn  (fn [] "")
                              :on-change   #(do (add! %)
                                                [:select-om-all.core/set ""])})]
                  [:div.col-sm-4
                   [:div {:style {:whitespace :no-wrap}}
                    [:button.btn.btn-default
                     {:on-click #(show-modal!)}
                     [:span.glyphicon.glyphicon-list] " Browse"]]]])])))))

(defn ThemeKeywordsExtra [_ owner]
  (reify
    om/IDisplayName (display-name [_] "ThemeKeywordsExtra")
    om/IInitState (init-state [_] {:new-value ""
                                   :highlight #{}})
    om/IRenderState
    (render-state [_ {:keys [new-value highlight]}]
      (let [{:keys [value placeholder disabled] :as props} (observe-path owner [:form :fields :identificationInfo :keywordsThemeExtra :keywords])
            set-value! (fn [v]
                         (om/set-state! owner :new-value v))
            add-value! (fn []
                         (when-not (empty? new-value)
                           (handlers/add-keyword! value new-value)
                           (handle-highlight-new owner new-value)
                           (set-value! "")))
            del-value! #(handlers/del-keyword! value %)]
        (html [:div.ThemeKeywordsExtra {:class (validation-state props)}
               (label-template props)
               (help-block-template props)
               [:table.table.keyword-table {:class (if-not disabled "table-hover")}
                [:tbody
                 (for [keyword value]
                   (do
                     [:tr {:class (if disabled "active" (if (highlight (:value keyword)) "highlight"))}
                      [:td (:value keyword)]
                      (if-not disabled
                        [:td
                         [:button.btn.btn-warn.btn-xs.pull-right
                          {:on-click #(del-value! (:value keyword))}
                          [:span.glyphicon.glyphicon-minus]]])]))]]
               (if-not disabled
                 [:div
                  (om/build Input {:placeholder placeholder
                                   :value       new-value
                                   :on-change   #(set-value! (.. % -target -value))
                                   :on-key-down #(match [(.-key %)]
                                                        ["Enter"] (add-value!)
                                                        :else nil)
                                   :addon-after (html [:span.input-group-btn
                                                       [:button.btn.btn-primary
                                                        {:on-click add-value!}
                                                        [:span.glyphicon.glyphicon-plus]]])})])])))))

(defn TaxonKeywordsExtra [_ owner]
  (reify
    om/IDisplayName (display-name [_] "TaxonKeywordsExtra")
    om/IInitState (init-state [_] {:new-value ""
                                   :highlight #{}})
    om/IRenderState
    (render-state [_ {:keys [new-value highlight]}]
      (let [{:keys [value required placeholder disabled] :as props} (observe-path owner [:form :fields :identificationInfo :keywordsTaxonExtra :keywords])
            set-value! #(om/set-state! owner :new-value %)
            add-value! #(when-not (empty? new-value)
                         (handlers/add-keyword! value new-value)
                         (handle-highlight-new owner new-value)
                         (set-value! nil))
            del-value! #(handlers/del-keyword! value %)]
        (html [:div.TaxonKeywordsExtra {:class (validation-state props)}
               [:label "Taxon keywords" (if required " *")]
               (help-block-template props)
               [:table.table.keyword-table {:class (if-not disabled "table-hover")}
                [:tbody
                 (for [keyword value]
                   (do
                     [:tr {:class (if disabled "active" (if (highlight (:value keyword)) "highlight"))}
                      [:td (:value keyword)]
                      (if-not disabled
                        [:td [:button.btn.btn-warn.btn-xs.pull-right
                              {:on-click #(del-value! (:value keyword))}
                              [:span.glyphicon.glyphicon-minus]]])]))]]
               (if-not disabled
                 [:div
                  (om/build Input {:placeholder placeholder
                                   :value       new-value
                                   :on-change   #(set-value! (.. % -target -value))
                                   :on-key-down #(match [(.-key %)]
                                                        ["Enter"] (add-value!)
                                                        :else nil)
                                   :addon-after (html [:span.input-group-btn
                                                       [:button.btn.btn-primary
                                                        {:on-click add-value!}
                                                        [:span.glyphicon.glyphicon-plus]]])})])])))))

