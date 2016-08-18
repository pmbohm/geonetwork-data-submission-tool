(ns metcalf.views
  (:require-macros [cljs.core.async.macros :refer [go alt! go-loop]])
  (:require [cljs.core.async :as async :refer [put! <! alts! chan pub sub timeout dropping-buffer]]
            [clojure.string :refer [blank?]]
            [clojure.set :as set]
            [ajax.core :as ajax]
            [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [cljs.core.match :refer-macros [match]]
            [sablono.core :refer-macros [html]]
            [clojure.string :as string]
            [clojure.zip :as zip :refer [zipper]]
            [metcalf.widget.date :refer [DateWidget]]
            [metcalf.widget.boxmap :as boxmap :refer [new-from-bounds-given-extents value->extent extent->value]]
            [metcalf.widget.select :refer [ReactSelect ReactSelectAsync VirtualizedSelect]]
            [metcalf.widget.table :refer [Table Column]]
            [metcalf.widget.modal :refer [Modal]]
            [metcalf.widget.tree :refer [Tree TermTree TermList]]
            [metcalf.logic :refer [derived-state extract-field-values field? field-edit page-fields
                                   extent->geographicElement is-valid?]]
            [metcalf.content :refer [default-payload contact-groups]]
            [metcalf.globals :refer [ref-path observe-path]]
            [metcalf.handlers :as handlers
             :refer [del-value! add-value! add-field!]]
            [metcalf.utils :as utils :refer [fmap map-keys enum js-lookup! js-lookup clj->js*
                                             error warn log info debug]]
            cljsjs.moment
            [goog.events :as gevents]
            [goog.style :as gstyle]
            [goog.userAgent :as guseragent]
            [cuerdas.core :as str]
            [goog.object :as gobj])
  (:import goog.events.EventType
           goog.events.FileDropHandler.EventType
           [goog.dom ViewportSizeMonitor]
           [goog.events FileDropHandler]))

(defmulti ModalDialog :type)

(defn label-template [{:keys [label required]}]
  (if label [:label label (if required " *")]))

(defn validation-state [{:keys [errors show-errors]}]
  (if (and show-errors (not (empty? errors)))
    "has-error"))

(defn InputWidget
  [{:keys [value addon-before addon-after help on-change disabled] :as props} owner]
  (reify

    om/IInitState
    (init-state [_] {:input-value value})

    om/IWillReceiveProps
    (will-receive-props [_ next-props]
      (utils/on-change props next-props [:value] #(om/set-state! owner :input-value %)))

    om/IRenderState
    (render-state [_ {:keys [input-value]}]
      (let [input-props (assoc props
                          :value (or input-value "")
                          :on-change #(om/set-state! owner :input-value (.. % -target -value))
                          :on-blur #(on-change input-value)
                          :key "ifc")]
        (html [:div.form-group {:class    (validation-state props)
                                :disabled disabled}
               (label-template props)
               (if (or addon-after addon-before)
                 [:div.input-group {:key "ig"} addon-before [:input.form-control input-props] addon-after]
                 [:input.form-control input-props])
               [:p.help-block help]])))))


(defn SimpleInputWidget
  [{:keys [value addon-before addon-after help on-change disabled] :as props} owner]
  (reify

    om/IRender
    (render [_]
      (let [input-props (assoc props
                          :value (or value "")
                          :on-change #(on-change (.. % -target -value))
                          :key "ifc")]
        (html [:div.form-group {:class    (validation-state props)
                                :disabled disabled}
               (label-template props)
               (if (or addon-after addon-before)
                 [:div.input-group {:key "ig"} addon-before [:input.form-control input-props] addon-after]
                 [:input.form-control input-props])
               [:p.help-block help]])))))


(defn ExpandingTextareaWidget
  "http://alistapart.com/article/expanding-text-areas-made-elegant"
  [{:keys [value on-change disabled] :as props} owner]
  (reify

    om/IInitState
    (init-state [_] {:input-value value})

    om/IWillReceiveProps
    (will-receive-props [_ next-props]
      (utils/on-change props next-props [:value] #(om/set-state! owner :input-value %)))

    om/IRenderState
    (render-state [_ {:keys [input-value]}]
      (let [{:keys [is-hidden help]} props]
        (html [:div.form-group {:class    (str (validation-state props) " "
                                               (when is-hidden "hidden"))
                                :disabled disabled}
               (label-template props)
               [:div.expandingArea.active {:style {:position "relative"}}
                [:pre (assoc props
                        :class "form-control")
                 [:span input-value] [:br]]
                [:textarea (assoc props
                             :value input-value
                             :on-change #(om/set-state! owner :input-value (.. % -target -value))
                             :on-blur #(on-change input-value)
                             :class "form-control"
                             :key "textarea")]]
               [:p.help-block help]])))))

(defn format-columns
  "Generate row with columns matching given width constraints.

  `flex` is a collection of column flex property values.
  You can pass any valid flex value string (containing up two three parameters grow/shrink/basis combined),
  but in the simplest case just put

  * `nil` to keep width fixed when appropriate `fixed` value provided, or to set flex grow to 1 otherwise
  * 0 to mark column as not growing wider than required to fit content
  * positive number to tell what part of available space should column take if possible

  `fixed` is a collection of column width property values.

  If both `flex` and `fixed` will have non-nil values for the same column,
  then both styles will be generated and usually you will get \"flex wins\" behaviour.

  `columns` is a collection of column contents."
  [flex fixed columns]
  (html
    [:div {:style {:display "flex"}}
     (map-indexed
       (fn [i column]
         (let [width (get fixed i)
               flex (get flex i (when-not width 1))]
           [:div {:style {:flex flex :width width}} column]))
       columns)]))

(defn filter-table
  "Default search for local datasource: case-insensitive substring match"
  [simple? table query]
  (let [col-match? (if simple?
                     #(str/starts-with? (-> % str str/lower) (-> query str str/lower))
                     #(str/contains? (-> % str str/lower) (-> query str str/lower)))]
    (filter
      (fn [row]
        (some col-match? (rest row)))
      table)))

(defn InputField [props owner]
  (reify
    om/IDisplayName (display-name [_] "InputField")
    om/IRenderState
    (render-state [_ {:keys []}]
      (let [field (observe-path owner (:path props))]
        (om/build InputWidget (-> field
                                  (merge (dissoc props :path))
                                  (assoc
                                    :on-change #(handlers/value-changed field %))))))))

(defn DateField [path owner]
  (reify
    om/IDisplayName (display-name [_] "DateField")
    om/IRender
    (render [_]
      (let [field (observe-path owner path)]
        (om/build DateWidget (assoc field :class "wauto"
                                          :display-format "DD-MM-YYYY"
                                          :on-blur (fn [] (handlers/show-errors field))
                                          :on-change (fn [value] (handlers/set-value field value))))))))

(defn OptionWidget [props owner]
  (om/component
    (let [[value display] props]
      (dom/option #js {:value value} display))))

(defn SelectWidget [props owner]
  (om/component
    (let [{:keys [label required value help disabled errors is-hidden on-change
                  options default-option default-value loading
                  show-errors]
           :or   {is-hidden false}} props
          disabled (or disabled loading)
          default-value (or default-value "")
          default-option (or default-option "Please select")]
      (html
        (when-not is-hidden
          [:div.form-group {:class (if (and show-errors (not (empty? errors)))
                                     "has-error")}
           (if label [:label label (if required " *")])
           (vec (concat
                  [:select.form-control (assoc props
                                          :on-change #(on-change (-> % .-target .-value))
                                          :value (or value default-value)
                                          :disabled disabled)
                   (if options
                     (dom/option #js {:value default-value :disabled true} default-option))]
                  (for [option options]
                    (om/build OptionWidget option))))
           (if help [:p.help-block help])])))))

(defn SelectField [path owner]
  (reify
    om/IDisplayName (display-name [_] "SelectField")
    om/IRender
    (render [_]
      (let [{:keys [options default-option disabled] :as field} (observe-path owner path)]
        (om/build SelectWidget (assoc field
                                 :class "wauto"
                                 :disabled (or disabled (empty? options))
                                 :default-option (if-not (empty? options) default-option "")
                                 :on-blur #(handlers/show-errors field)
                                 :on-change #(handlers/value-changed field %)))))))

(defn TextareaFieldProps [props owner]
  (reify
    om/IDisplayName (display-name [_] "TextareaField")
    om/IRenderState
    (render-state [_ {:keys []}]
      (let [{:keys [path]} props
            field (observe-path owner path)]
        (om/build ExpandingTextareaWidget (merge field (dissoc props :path)
                                                 {:on-change #(handlers/value-changed field %)}))))))

(defn TextareaField [path owner]
  (reify
    om/IDisplayName (display-name [_] "TextareaField")
    om/IRender
    (render [_]
      (let [field (observe-path owner path)]
        (om/build ExpandingTextareaWidget
                  (assoc field :on-change (fn [value] (handlers/value-changed field value))))))))

(defn Checkbox [props owner]
  (om/component
    (let [{:keys [label checked on-change disabled help]} props
          input-control (dom/input #js {:type     "checkbox"
                                        :checked  checked
                                        :disabled disabled
                                        :onChange on-change})]
      (html [:div.form-group {:class (validation-state props)}
             [:div.checkbox
              [:label input-control label]]
             [:p.help-block help]]))))

(defn CheckboxField [path owner]
  (reify
    om/IDisplayName (display-name [_] "CheckboxField")
    om/IRenderState
    (render-state [_ {:keys []}]
      (let [field (observe-path owner path)]
        (om/build Checkbox (assoc field :checked (:value field)
                                        :on-blur #(handlers/show-errors field)
                                        :on-change #(handlers/checkbox-change field %)))))))

(defn BackButton [props owner]
  (reify
    om/IDisplayName (display-name [_] "BackButton")
    om/IRender
    (render [_]
      (let [page (observe-path owner [:page])
            back (:back page)]
        (html (if back [:button.btn.btn-default.BackButton
                        {:on-click #(handlers/back back)}
                        [:span.glyphicon.glyphicon-chevron-left] " Back"]))))))

(defmulti PageView (fn [page owner] (get page :name)) :default "404")

(defn getter [k row] (get row k))

(defn update-table-width [owner]
  (let [autowidth (om/get-node owner "autowidth")
        width (.-width (gstyle/getSize autowidth))]
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

(defn KeywordsThemeTable [_ owner]
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
      (let [vsm (ViewportSizeMonitor.)]
        (gevents/listen vsm goog.events.EventType.RESIZE #(update-table-width owner))
        (update-table-width owner)))
    om/IRenderState
    (render-state [_ {:keys [query width columnWidths isColumnResizing scrollToRow]}]
      (let [keywords (observe-path owner [:form :fields :identificationInfo :keywordsTheme :keywords])
            uuids (zipmap (map :value (:value keywords)) (range))
            table (observe-path owner [:theme :table])
            results (if (blank? query)
                      table
                      (vec (filter-table false table query)))
            rowHeight 50
            on-submit #(do (om/set-state-nr! owner :scrollToRow 0)
                           (om/set-state! owner :query %))]
        (html [:div.KeywordsThemeTable
               (om/build SimpleInputWidget
                         {:label     "Search"
                          :value     query
                          :on-change #(on-submit %)})
               (if (> (count results) 0)
                 [:div {:ref "autowidth"}
                  (Table
                    {:width                     width
                     :maxHeight                 400
                     :rowHeight                 rowHeight
                     :rowGetter                 #(get results %)
                     :rowsCount                 (count results)
                     :headerHeight              30
                     :onColumnResizeEndCallback #(do (om/set-state! owner [:columnWidths %2] (max %1 5))
                                                     (om/set-state! owner :isColumnResizing false))
                     :overflowX                 "hidden"
                     :scrollToRow               scrollToRow
                     :onScrollEnd               #(om/set-state! owner :scrollToRow (quot %2 rowHeight))
                     :isColumnResizing          isColumnResizing}
                    (Column
                      {:label          ""
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
                      {:label          "Topic"
                       :cellDataGetter getter
                       :dataKey        1
                       :cellRenderer   (fn [cellData dataKey rowData]
                                         (om/build KeywordsThemeCell rowData))
                       :flexGrow       1
                       :width          (get columnWidths 1)
                       :isResizable    true}))]
                 [:div.no-results "No results found."])
               [:p "There are " (count table) " keywords in our database"]])))))

(defn handle-highlight-new [owner item]
  (om/set-state! owner :highlight (conj (om/get-state owner :highlight) item))
  (go (<! (timeout 5000))
      (om/set-state! owner :highlight (disj (om/get-state owner :highlight) item))))

(defmethod ModalDialog :TableModalEditForm
  [{:keys [form path title]} owner]
  (om/component
    (let [many-field (observe-path owner (drop-last 2 path))]
      (om/build Modal {:ok-copy      "Done"
                       :modal-header (html [:span [:span.glyphicon.glyphicon-list] " Edit " title])
                       :modal-body   #(om/build form path)
                       :modal-footer (html [:div
                                            [:a.btn.text-danger.pull-left
                                             {:on-click (fn [e]
                                                          (.preventDefault e)
                                                          (handlers/open-modal {:type       :confirm
                                                                                :title      "Delete " title "?"
                                                                                :message    "Are you sure you want to delete?"
                                                                                :on-confirm (fn []
                                                                                              (del-value! many-field (last path))
                                                                                              (handlers/close-modal))}))}
                                             [:span.glyphicon.glyphicon-remove] " Delete"]
                                            [:button.btn.btn-primary {:on-click #(handlers/close-modal)} "Done"]])
                       :on-dismiss   #(handlers/close-modal)
                       :on-save      #(handlers/close-modal)}))))

(defmethod ModalDialog :TableModalAddForm
  [{:keys [form path title]} owner]
  (om/component
    (let [many-field (observe-path owner (drop-last 2 path))
          handle-cancel (fn [] (del-value! many-field (last path))
                          (handlers/close-modal))]
      (om/build Modal {:ok-copy      "Done"
                       :modal-header (html [:span [:span.glyphicon.glyphicon-list] " Add " title])
                       :modal-body   #(om/build form path)
                       :on-dismiss   handle-cancel
                       :on-cancel    handle-cancel
                       :on-save      #(handlers/close-modal)}))))

(defn TableModalEdit [{:keys [ths tds-fn form title field-path placeholder default-field on-new-click add-label]
                       :or   {tds-fn    #(list (:value %))
                              add-label "Add new"}} owner]
  (reify
    om/IDisplayName (display-name [_] "TableModalEdit")
    om/IRender
    (render [_]
      (let [{:keys [disabled] :as many-field} (observe-path owner field-path)
            edit! (fn [field-path]
                    (handlers/open-modal {:type  :TableModalEditForm
                                          :title title
                                          :form  form
                                          :path  field-path}))
            new! (fn [default-field]
                   (let [values-ref (:value many-field)
                         values-len (count values-ref)
                         new-field-path (conj (om/path values-ref) values-len)]
                     (if on-new-click
                       (on-new-click {:default-field default-field
                                      :many-field    many-field
                                      :type          :TableModalAddForm
                                      :title         title
                                      :form          form
                                      :path          new-field-path})
                       (do (if default-field
                             (add-field! many-field default-field)
                             (add-field! many-field))
                           (handlers/open-modal {:type  :TableModalAddForm
                                                 :title title
                                                 :form  form
                                                 :path  new-field-path})))))]
        (html [:div.TableInlineEdit
               (when-let [help (:help many-field)]
                 [:p.help-block help])
               (if (or (not placeholder) (-> many-field :value count pos?))
                 [:table.table {:class (when-not (or disabled (empty? (:value many-field))) "table-hover")}
                  (if ths [:thead [:tr (for [th ths]
                                         [:th th])
                                   [:th.xcell " "]]])
                  [:tbody
                   (if (not-empty (:value many-field))
                     (for [field (:value many-field)]
                       (let [field-path (om/path field)
                             has-error (not (is-valid? {:fields (:value field)}))]
                         [:tr.clickable-text {:class    (when has-error "warning")
                                              :ref      field-path
                                              :on-click (when-not disabled
                                                          #(edit! field-path))}
                          (for [td-value (tds-fn field)]
                            [:td (if (empty? td-value) [:span {:style {:color "#ccc"}} "--"] td-value)])
                          [:td.xcell
                           (when has-error
                             [:span.glyphicon.glyphicon-alert.text-danger])]]))
                     [:tr.noselect {:on-click #(when-not disabled (new! default-field))}
                      (for [_ (or ths [nil])] [:td {:style {:color "#ccc"}} "--"])
                      [:td.xcell]])]]
                 [:div {:style {:margin-bottom "1em"}} placeholder])
               (when-not disabled
                 [:button.btn.btn-primary.btn-sm
                  {:on-click #(new! default-field)}
                  [:span.glyphicon.glyphicon-plus] " " add-label])])))))

(defn TableInlineEdit
  [{:keys [ths tds-fn form field-path placeholder default-field]
    :or   {tds-fn #(list (:value %))}} owner]
  (reify
    om/IDisplayName (display-name [_] "TableInlineEdit")
    om/IInitState (init-state [_] {:cursor    nil
                                   :highlight #{}})
    om/IRenderState
    (render-state [_ {:keys [cursor highlight]}]
      (let [{:keys [disabled] :as many-field} (observe-path owner field-path)
            col-span (if ths (count ths) 1)
            edit! (fn [field-path]
                    (om/set-state! owner :cursor field-path))
            delete! (fn [field]
                      (om/set-state! owner :cursor nil)
                      (del-value! many-field (last (om/path field))))
            new! (fn [default-field]
                   (let [values-ref (:value many-field)
                         values-len (count values-ref)]
                     (if default-field
                       (add-field! many-field default-field)
                       (add-field! many-field))
                     (let [new-cursor (conj (om/path values-ref) values-len)]
                       (om/set-state! owner :cursor new-cursor)
                       (handle-highlight-new owner new-cursor))))]
        (html [:div.TableInlineEdit
               [:p.help-block (:help many-field)]
               (if (or (not placeholder) (-> many-field :value count pos?))
                 [:table.table {:class (when-not disabled "table-hover")}
                  (if ths [:thead [:tr (for [th ths]
                                         [:th th])
                                   [:th.xcell " "]]])
                  [:tbody
                   (for [field (:value many-field)]
                     (let [field-path (om/path field)
                           highlight-class (if (highlight field-path) "highlight")]
                       (if (= cursor field-path)

                         [:tr.active {:ref "edit"}
                          [:td {:class    highlight-class
                                :col-span col-span}
                           (om/build form cursor)
                           [:button.btn.btn-primary {:on-click #(edit! nil)} "Done"] " "
                           [:a.text-danger.pull-right {:on-click #(delete! field)}
                            [:span.glyphicon.glyphicon-remove] " Delete"]]
                          [:td.xcell {:class highlight-class}
                           [:span.clickable-text
                            {:on-click #(edit! nil)}
                            [:span.glyphicon.glyphicon-remove]]]]


                         [:tr.noselect {:ref      field-path
                                        :on-click (when-not disabled
                                                    #(edit! field-path))
                                        :class    (if (= field-path cursor) "info")}
                          (for [td-value (tds-fn field)]
                            [:td td-value])
                          [:td.xcell
                           (when-not disabled
                             [:span.glyphicon.glyphicon-edit.hover-only])]])))]]
                 [:div {:style {:margin-bottom "1em"}} placeholder])
               (when-not disabled
                 [:button.btn.btn-primary
                  {:on-click #(new! default-field)}
                  [:span.glyphicon.glyphicon-plus] " Add new"])])))))

(defn theme-option-renderer
  [{:keys [focusedOption focusOption option selectValue optionHeight]}]
  (let [{:keys [rowData]} option
        className (if (identical? option focusedOption)
                    "VirtualizedSelectOption VirtualizedSelectFocusedOption"
                    "VirtualizedSelectOption")]
    (html [:div
           {:class         className
            :on-click      #(selectValue option)
            :on-mouse-over #(focusOption option)
            :style         {:height optionHeight}}
           (om/build KeywordsThemeCell rowData)])))

(defmethod ModalDialog :ThemeKeywords
  [{:keys [message on-confirm on-cancel]} owner]
  (om/component
    (om/build Modal {:ok-copy      "OK"
                     :dialog-class "modal-lg"
                     :modal-header (html [:span [:span.glyphicon.glyphicon-list] " " "Research theme keywords"])
                     :modal-body   #(html [:div
                                           [:p.help-block "Select keyword(s) to add to record"]
                                           (om/build KeywordsThemeTable nil)])
                     :on-dismiss   #(handlers/close-modal)
                     :hide-footer  true})))

(defn ThemeKeywords [_ owner]
  (reify
    om/IDisplayName (display-name [_] "ThemeKeywords")
    om/IInitState (init-state [_] {:new-value  nil
                                   :input      ""
                                   :show-modal false
                                   :highlight  #{}})
    om/IRenderState
    (render-state [_ {:keys [new-value show-modal highlight options]}]
      (let [{:keys [keywords]} (observe-path owner [:form :fields :identificationInfo :keywordsTheme])
            {:keys [value placeholder disabled help] :as props} keywords
            theme-table (observe-path owner [:theme :table])
            set-value! #(om/set-state! owner :new-value %)
            add! (fn [uuid] (when-not (empty? uuid)
                              (when (not-any? (comp #{uuid} :value)
                                              (:value keywords))
                                (add-value! keywords uuid))
                              (handle-highlight-new owner uuid)
                              (set-value! nil)))
            lookup (fn [uuid] (first (filterv #(= uuid (first %)) theme-table)))
            show-modal! #(handlers/open-modal {:type :ThemeKeywords})
            options (into [] (for [[value & path :as rowData] theme-table]
                               (js-lookup! #js {:value   value
                                                :rowData rowData
                                                :label   (string/join " > " path)})))]
        (html [:div.ThemeKeywords {:class (validation-state props)}
               (label-template props)
               [:p.help-block help]
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
                 [:div.flex-row
                  [:div.flex-row-field
                   (om/build VirtualizedSelect {:placeholder    placeholder
                                                :options        options
                                                :value          ""
                                                :maxHeight      200
                                                :optionHeight   40
                                                :optionRenderer theme-option-renderer
                                                :onChange       (fn [{:strs [value] :as option}]
                                                                  (add! value))})]
                  [:div.flex-row-button
                   [:button.btn.btn-default
                    {:on-click #(show-modal!)}
                    [:span.glyphicon.glyphicon-list] " Browse"]]])])))))

(defn ThemeInputField
  [{:keys [value placeholder errors help on-change on-blur on-submit] :as props} owner]
  (reify
    om/IRender
    (render [_]
      (html
        [:div.form-group {:class (validation-state props)}
         (label-template props)
         [:div.input-group {:key "ig"}
          [:input.form-control {:value       (or value "")
                                :placeholder placeholder
                                :errors      errors
                                :on-key-down #(match [(.-key %)]
                                               ["Enter"] (on-submit)
                                               :else nil)
                                :on-change   on-change
                                :on-blur     on-blur
                                :key         "ifc"}]
          [:span.input-group-btn
           [:button.btn.btn-primary {:disabled (str/blank? value)
                                     :on-click on-submit}
            [:span.glyphicon.glyphicon-plus]]]]]))))

(defn ThemeKeywordsExtra [_ owner]
  (reify
    om/IDisplayName (display-name [_] "ThemeKeywordsExtra")
    om/IInitState (init-state [_] {:highlight #{}})
    om/IRenderState
    (render-state [_ {:keys [highlight]}]
      (let [keywords-path [:form :fields :identificationInfo :keywordsThemeExtra :keywords]
            {:keys [value placeholder disabled errors new-value help] :as props} (observe-path owner keywords-path)
            set-value! (fn [v]
                         (handlers/setter props :new-value v))
            add-value! (fn []
                         (when-not (empty? new-value)
                           (handlers/add-keyword-extra value new-value)
                           (handle-highlight-new owner new-value)
                           (set-value! "")
                           (handlers/check-unsaved-keyword-input keywords-path)))
            del-value! #(handlers/del-keyword-extra value %)]
        (html [:div.ThemeKeywordsExtra {:class (validation-state props)}
               (label-template props)
               [:p.help-block help]
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
                 (om/build ThemeInputField {:value       new-value
                                            :on-submit   add-value!
                                            :placeholder placeholder
                                            :errors      errors
                                            :help        help
                                            :on-change   (fn [e]
                                                           (set-value! (.. e -target -value)))
                                            :on-blur     (fn [] (js/setTimeout #(handlers/check-unsaved-keyword-input keywords-path) 100))}))])))))

(defn TaxonKeywordsExtra [_ owner]
  (reify
    om/IDisplayName (display-name [_] "TaxonKeywordsExtra")
    om/IInitState (init-state [_] {:highlight #{}})
    om/IRenderState
    (render-state [_ {:keys [highlight]}]
      (let [keywords-path [:form :fields :identificationInfo :keywordsTaxonExtra :keywords]
            {:keys [value required help placeholder disabled errors new-value] :as props} (observe-path owner keywords-path)
            set-value! (fn [v] (handlers/setter props :new-value v))
            add-value! #(when-not (empty? new-value)
                         (handlers/add-keyword-extra value new-value)
                         (handle-highlight-new owner new-value)
                         (set-value! nil)
                         (handlers/check-unsaved-keyword-input keywords-path))
            del-value! #(handlers/del-keyword-extra value %)]
        (html [:div.TaxonKeywordsExtra {:class (validation-state props)}
               [:label "Taxon keywords" (if required " *")]
               [:p.help-block help]
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
                 (om/build ThemeInputField {:value       new-value
                                            :on-submit   add-value!
                                            :placeholder placeholder
                                            :errors      errors
                                            :help        help
                                            :on-change   (fn [e]
                                                           (set-value! (.. e -target -value)))
                                            :on-blur     (fn [] (js/setTimeout #(handlers/check-unsaved-keyword-input keywords-path) 100))}))])))))

(defn geographicElement->extent
  "Transform our API specific bbox data into something generic for Openlayers"
  [{:keys [northBoundLatitude westBoundLongitude eastBoundLongitude southBoundLatitude]}]
  (map :value [westBoundLongitude southBoundLatitude eastBoundLongitude northBoundLatitude]))

(defn ->float [s]
  (let [f (js/parseFloat s)]
    (if (js/isNaN f) nil f)))

(defn CoordInputWidget
  [{:keys [value addon-before addon-after help on-change] :as props} owner]
  (reify

    om/IInitState
    (init-state [_] {:input-value value})

    om/IWillReceiveProps
    (will-receive-props [_ next-props]
      (utils/on-change props next-props [:value] #(om/set-state! owner :input-value %)))

    om/IRenderState
    (render-state [_ {:keys [input-value]}]
      (let [input-props (assoc props
                          :value (or value "")
                          :key "ifc")]
        (html [:div.form-group {:class (validation-state props)}
               (label-template props)
               (if (or addon-after addon-before)
                 [:div.input-group {:key "ig"} addon-before [:input.form-control input-props] addon-after]
                 [:input.form-control
                  (assoc input-props
                    :value input-value
                    :on-change #(om/set-state! owner :input-value (.. % -target -value))
                    :on-blur (fn [e]
                               (let [v (.. e -target -value)
                                     f (->float v)]
                                 (om/set-state! owner :input-value (str f))
                                 (on-change f))))])
               [:p.help-block help]])))))

(defn CoordInputField [props owner]
  (reify
    om/IDisplayName (display-name [_] "CoordInputField")
    om/IRender
    (render [_]
      (let [field (observe-path owner (:path props))]
        (om/build CoordInputWidget
                  (-> field
                      (merge (dissoc props :path))
                      (assoc
                        :on-change (fn [value]
                                     (handlers/value-changed field value)))))))))


(defn CoordField [path owner]
  (reify
    om/IInitState
    (init-state [_] {})
    om/IRenderState
    (render-state [_ {:keys []}]
      (let [props (observe-path owner path)
            {:keys [northBoundLatitude westBoundLongitude eastBoundLongitude southBoundLatitude]} (:value props)]
        (let [n-field (om/build CoordInputField {:path (om/path northBoundLatitude)})
              e-field (om/build CoordInputField {:path (om/path eastBoundLongitude)})
              s-field (om/build CoordInputField {:path (om/path southBoundLatitude)})
              w-field (om/build CoordInputField {:path (om/path westBoundLongitude)})]
          (html [:div.CoordField
                 [:div.row [:div.col-sm-6.col-sm-offset-3.col-lg-4.col-lg-offset-2
                            [:div.n-block n-field]]]
                 [:div.row
                  [:div.col-sm-6.col-lg-4 [:div.w-block w-field]]
                  [:div.col-sm-6.col-lg-4 [:div.e-block e-field]]]
                 [:div.row
                  [:div.col-sm-6.col-sm-offset-3.col-lg-4.col-lg-offset-2
                   [:div.s-block s-field]]]]))))))

(defprotocol IPrintNice
  (print-nice [x]))

(extend-protocol IPrintNice
  number
  (print-nice [x] (.toFixed x 3))
  object
  (print-nice [x] (pr-str x))
  nil
  (print-nice [x] "--"))

(defn GeographicCoverage [_ owner]
  (reify
    om/IDisplayName (display-name [_] "GeographicCoverage")
    om/IInitState
    (init-state [_]
      {:map-props (assoc (boxmap/init-map-props)
                    :zoom 6
                    :center (js/google.maps.LatLng. -42 147))})
    om/IRenderState
    (render-state [_ {:keys [map-props boxmap]}]
      (let [{hasGeographicCoverage :value} (observe-path owner [:form :fields :identificationInfo :geographicElement :hasGeographicCoverage])
            {:keys [disabled] :as boxes} (observe-path owner [:form :fields :identificationInfo :geographicElement :boxes])
            extents (map (comp geographicElement->extent :value) (:value boxes))

            map-extents (->> extents
                             (filter (fn [extent] (every? number? extent)))
                             (into #{}))
            map-props (assoc map-props :extents map-extents)

            new-extent (new-from-bounds-given-extents (:bounds map-props) extents)
            new-geographicelement-value (extent->value new-extent)]
        (html [:div.GeographicCoverage
               [:h4 "Geographic Coverage"]
               (om/build CheckboxField [:form :fields :identificationInfo :geographicElement :hasGeographicCoverage])
               (when hasGeographicCoverage
                 [:div.row
                  [:div.col-sm-6
                   ; TODO: refactor value/on-change data types to reduce data munging.

                   (om/build boxmap/BoxMapWidget
                             {:map-props map-props
                              :ref       (fn [boxmap] (om/set-state! owner :boxmap boxmap))
                              :disabled  disabled
                              :on-change (fn [map-props']
                                           (utils/on-change map-props map-props' [:extents]
                                                            #(handlers/set-geographic-element boxes (mapv extent->value %)))
                                           (om/set-state! owner :map-props map-props'))})]

                  [:div.col-sm-6
                   (om/build TableModalEdit {:ths           ["North limit" "West limit" "South limit" "East limit"]
                                             :tds-fn        (fn [geographicElement]
                                                              (let [{:keys [northBoundLatitude westBoundLongitude
                                                                            eastBoundLongitude southBoundLatitude]}
                                                                    (:value geographicElement)]
                                                                [(print-nice (:value northBoundLatitude))
                                                                 (print-nice (:value westBoundLongitude))
                                                                 (print-nice (:value southBoundLatitude))
                                                                 (print-nice (:value eastBoundLongitude))]))
                                             :default-field (-> (handlers/new-value-field boxes)
                                                                (update-in [:value :northBoundLatitude] merge (:northBoundLatitude new-geographicelement-value))
                                                                (update-in [:value :southBoundLatitude] merge (:southBoundLatitude new-geographicelement-value))
                                                                (update-in [:value :eastBoundLongitude] merge (:eastBoundLongitude new-geographicelement-value))
                                                                (update-in [:value :westBoundLongitude] merge (:westBoundLongitude new-geographicelement-value)))
                                             :form          CoordField
                                             :title         "Geographic Coordinates"
                                             :on-new-click  (fn [modal-props]
                                                              (boxmap/handle-add-click boxmap)
                                                              (handlers/open-modal modal-props))
                                             :field-path    [:form :fields :identificationInfo :geographicElement :boxes]
                                             :placeholder   [:em {:style {:color "#a94442"}} "Specify the location(s) of this study."]})]])])))))

(defn VerticalCoverage [props owner]
  (reify
    om/IDisplayName (display-name [_] "VerticalCoverage")
    om/IRender
    (render [_]
      (let [{hasVerticalExtent :value} (observe-path owner [:form :fields :identificationInfo :verticalElement :hasVerticalExtent])]
        (html [:div.VerticalCoverage
               [:h4 "Vertical Coverage"]
               (om/build CheckboxField [:form :fields :identificationInfo :verticalElement :hasVerticalExtent])
               (when hasVerticalExtent
                 [:div
                  (om/build SelectField [:form :fields :identificationInfo :verticalElement :verticalCRS])
                  (om/build InputField
                            {:path  [:form :fields :identificationInfo :verticalElement :minimumValue]
                             :class "wauto"})
                  (om/build InputField
                            {:path  [:form :fields :identificationInfo :verticalElement :maximumValue]
                             :class "wauto"})])])))))

(defn term-option-parent?
  [child parent]
  (and (= (aget parent "tree_id") (aget child "tree_id"))
       (< (aget parent "lft") (aget child "lft"))
       (> (aget parent "rgt") (aget child "rgt"))))

(defn term-option-path
  [options option]
  (->> options
       (filter (partial term-option-parent? option))
       (sort-by #(aget % "lft"))))

(defn api-option-renderer [options option]
  (html [:div.topic-cell
         [:div.topic-path (string/join " > " (map #(aget % "term") (term-option-path options option)))]
         [:div.topic-value (aget option "term")]]))

(defn other-term?
  [{:keys [term vocabularyTermURL] :as dp-term}]
  (and (:value term) (empty? (:value vocabularyTermURL))))

(defn ApiTermSelectField
  [{:keys [param-type api-path dp-term-path]} owner]
  (reify
    om/IWillMount
    (will-mount [_]
      (handlers/load-api-options api-path))
    om/IRender
    (render [_]
      (let [{:keys [options]} (observe-path owner api-path)
            {:keys [term vocabularyTermURL vocabularyVersion termDefinition] :as dp-term} (observe-path owner dp-term-path)
            {:keys [value label help required errors show-errors]} term
            selectable-options (filterv #(gobj/get % "is_selectable") options)
            other-option #js {:vocabularyTermURL "(new term)" :term (str (:value term))}
            new-term? (other-term? dp-term)]
        (html
          [:div
           (if new-term? [:span.pull-right.new-term.text-primary
                          [:span.glyphicon.glyphicon-asterisk]
                          " New term"])
           (if label [:label label (if required " *")])
           [:div.flex-row
            [:div.flex-row-field
             [:div.form-group {:class (if (and show-errors (not (empty? errors))) "has-error")}
              (if-not new-term?
                (ReactSelect
                  {:value          (:value vocabularyTermURL)
                   :options        selectable-options
                   :valueKey       "vocabularyTermURL"
                   :labelKey       "term"
                   :onChange       (fn [option]
                                     (handlers/update-dp-term dp-term-path option))
                   :noResultsText  "No results found.  Click browse to add a new entry."
                   :optionRenderer (fn [option] (api-option-renderer options option))})

                (ReactSelect
                  {:value          "(new term)"
                   :options        (conj selectable-options other-option)
                   :valueKey       "vocabularyTermURL"
                   :labelKey       "term"
                   :onChange       (fn [option]
                                     (when-not (= option other-option)
                                       (handlers/update-dp-term dp-term-path option)))
                   :noResultsText  "No results found.  Click browse to add a new entry."
                   :optionRenderer (fn [option] (api-option-renderer options option))}))
              [:p.help-block help]]]
            [:div.flex-row-button
             [:button.btn.btn-default
              {:on-click #(handlers/open-modal
                           {:type         param-type
                            :api-path     api-path
                            :dp-term-path dp-term-path})}
              [:span.glyphicon.glyphicon-list] " Browse"]]]])))))

(defn ApiListWidget
  [{:keys [api-path value on-change] :as props} owner]
  (reify
    om/IWillMount
    (will-mount [_]
      (handlers/load-api-options api-path))
    om/IRender
    (render [_]
      (when-let [{:keys [options]} (observe-path owner api-path)]
        ; TODO: review performance
        (let [options (js->clj options :keywordize-keys true)]
          (om/build TermList
                    {:value     (first (filter #(-> % :URI (= value)) options))
                     :value-key :URI
                     :options   options
                     :on-select on-change}))))))

(defn ApiTreeWidget
  [{:keys [api-path value on-change] :as props} owner]
  (reify
    om/IWillMount
    (will-mount [_]
      (handlers/load-api-options api-path))
    om/IRender
    (render [_]
      (when-let [{:keys [options]} (observe-path owner api-path)]
        ; TODO: review performance
        (let [options (js->clj options :keywordize-keys true)]
          (om/build TermTree
                    {:value     (first (filter #(-> % :URI (= value)) options))
                     :value-key :URI
                     :options   options
                     :on-select on-change}))))))

(defn ApiTermListField
  [{:keys [api-path dp-term-path sort?]} owner]
  (om/component
    (let [{:keys [term vocabularyTermURL vocabularyVersion termDefinition] :as dp-term} (observe-path owner dp-term-path)
          {:keys [value label help required errors show-errors]} term
          {:keys [options]} (observe-path owner api-path)]
      (html
        [:div.form-group {:class (if (and show-errors (not (empty? errors))) "has-error")}
         (if label [:label label (if required " *")])
         (om/build
           ApiListWidget
           {:api-path  api-path
            :value     (:value vocabularyTermURL)
            :options   options
            :on-change (fn [option]
                         (handlers/update-dp-term dp-term-path option))})
         [:p.help-block "There are " (count options) " terms in this vocabulary"]]))))

(defn ApiTermTreeField
  [{:keys [api-path dp-term-path sort?]} owner]
  (om/component
    (let [{:keys [term vocabularyTermURL vocabularyVersion termDefinition] :as dp-term} (observe-path owner dp-term-path)
          {:keys [value label help required errors show-errors]} term
          {:keys [options]} (observe-path owner api-path)]
      (html
        [:div.form-group {:class (if (and show-errors (not (empty? errors))) "has-error")}
         (if label [:label label (if required " *")])
         (om/build
           ApiTreeWidget
           {:api-path  api-path
            :value     (:value vocabularyTermURL)
            :options   options
            :on-change (fn [option]
                         (handlers/update-dp-term dp-term-path option))})
         [:p.help-block "There are " (count options) " terms in this vocabulary"]]))))

(defn TermOrOtherForm
  "docstring"
  [{:keys [api-path dp-term-path] :as props} owner]
  (om/component
    (let [{:keys [term vocabularyTermURL] :as dp-term} (observe-path owner dp-term-path)]
      (html [:div
             [:p "Select a term from the vocabulary"]
             (om/build ApiTermTreeField props)
             [:p "Or define your own"]
             (om/build InputWidget
                       (assoc term
                         :value (if (other-term? dp-term) (:value term) "")
                         :on-change (fn [v]
                                      (handlers/update-dp-term dp-term-path #js {:term v}))))]))))

(defn UnitTermOrOtherForm
  "docstring"
  [{:keys [api-path dp-term-path] :as props} owner]
  (om/component
    (let [{:keys [term vocabularyTermURL] :as dp-term} (observe-path owner dp-term-path)]
      (html [:div
             [:p "Select a term from the vocabulary"]
             (om/build ApiTermListField props)
             [:p "Or define your own"]
             (om/build InputWidget
                       (assoc term
                         :value (if (other-term? dp-term) (:value term) "")
                         :on-change (fn [v]
                                      (handlers/update-dp-term dp-term-path #js {:term v}))))]))))

(defmethod ModalDialog :parametername
  [props owner]
  (om/component
    (om/build Modal {:ok-copy      "Done"
                     :modal-header (html [:span [:span.glyphicon.glyphicon-list] " " "Browse parameter names"])
                     :modal-body   #(om/build TermOrOtherForm props)
                     :on-dismiss   #(handlers/close-modal)
                     :on-save      #(handlers/close-modal)})))

(defmethod ModalDialog :parameterunit
  [props owner]
  (om/component
    (om/build Modal {:ok-copy      "Done"
                     :modal-header (html [:span [:span.glyphicon.glyphicon-list] " " "Browse parameter units"])
                     :modal-body   #(html [:div
                                           (om/build UnitTermOrOtherForm (assoc props :sort? false))])
                     :on-dismiss   #(handlers/close-modal)
                     :on-save      #(handlers/close-modal)})))

(defmethod ModalDialog :parameterinstrument
  [props owner]
  (om/component
    (om/build Modal {:ok-copy      "Done"
                     :modal-header (html [:span [:span.glyphicon.glyphicon-list] " " "Browse parameter instruments"])
                     :modal-body   #(html [:div
                                           (om/build TermOrOtherForm props)])
                     :on-dismiss   #(handlers/close-modal)
                     :on-save      #(handlers/close-modal)})))

(defmethod ModalDialog :parameterplatform
  [props owner]
  (om/component
    (om/build Modal {:ok-copy      "Done"
                     :modal-header (html [:span [:span.glyphicon.glyphicon-list] " " "Browse parameter platforms"])
                     :modal-body   #(html [:div
                                           (om/build TermOrOtherForm props)])
                     :on-dismiss   #(handlers/close-modal)
                     :on-save      #(handlers/close-modal)})))



(defn DataParameterRowEdit [path owner]
  (reify
    om/IDisplayName (display-name [_] "DataParameterDetail")
    om/IRender
    (render [_]
      (let [props (observe-path owner path)
            {:keys [longName
                    name
                    unit instrument platform]} (:value props)]
        (html [:div.DataParameterMaster
               [:div
                (om/build ApiTermSelectField {:param-type :parametername :api-path [:api :parametername] :dp-term-path (om/path longName)})
                [:div.shortName
                 (om/build InputField {:path (om/path name)})]]
               (om/build ApiTermSelectField {:param-type :parameterunit :api-path [:api :parameterunit] :dp-term-path (om/path unit)})
               (om/build ApiTermSelectField {:param-type :parameterinstrument :api-path [:api :parameterinstrument] :dp-term-path (om/path instrument)})
               (om/build ApiTermSelectField {:param-type :parameterplatform :api-path [:api :parameterplatform] :dp-term-path (om/path platform)})])))))

(defn DataParametersTable [path owner]
  (reify
    om/IDisplayName (display-name [_] "DataParameters")
    om/IRender
    (render [_]
      (html [:div.DataParametersTable
             (om/build TableModalEdit
                       {:ths        ["Long name" "Units" "Instrument" "Platform"]
                        :tds-fn     (fn [field]
                                      (let [{:keys [longName unit instrument platform]} (:value field)]
                                        (mapv (comp #(or % "--") :value :term) [longName unit instrument platform])))
                        :form       DataParameterRowEdit
                        :title      "Parameter"
                        :add-label  "Add data parameter"
                        :field-path [:form :fields :identificationInfo :dataParameters]})]))))

(defn upload! [owner {:keys [url fields]} file reset-file-drop]
  (om/set-state! owner :uploading true)
  (let [fd (js/FormData.)
        xhr (js/XMLHttpRequest.)]
    (.open xhr "POST" url true)
    (set! (.-onreadystatechange xhr)
          (fn []
            (when (= (.-readyState xhr) 4)
              (if (#{200 201} (.-status xhr))
                (->> (.-response xhr)
                     (.parse js/JSON)
                     js->clj
                     (map-keys keyword)
                     (handlers/add-attachment))
                (handlers/open-modal
                  {:type    :alert
                   :message "File upload failed. Please try again or contact administrator."}))
              (om/set-state! owner :uploading false)
              (put! reset-file-drop true))))
    (doto fd
      (.append "csrfmiddlewaretoken" (get-in fields [:csrfmiddlewaretoken :initial]))
      (.append "document" (get-in fields [:document :initial]))
      (.append "name" (.-name file))
      (.append "file" file))
    (.send xhr fd)))

(defn handle-file [owner file]
  (let [{:keys [reset-ch max-filesize]} (om/get-props owner)]
    (if (or (not max-filesize)
            (<= (.-size file) (* 1024 1024 max-filesize)))
      (om/set-state! owner :file file)
      (when max-filesize
        (handlers/open-modal
          {:type    :alert
           :message (str "Please, choose file less than " max-filesize "mb")})
        (put! reset-ch true)))))

(defn FileDrop [{:keys [on-change reset-ch placeholder
                        reset-ch max-filesize]} owner]
  (reify
    om/IDisplayName (display-name [_] "FileDrop")
    om/IDidMount
    (did-mount [_]
      (gevents/listen
        (FileDropHandler. js/document)
        goog.events.FileDropHandler.EventType.DROP
        #(handle-file owner (.. % getBrowserEvent -dataTransfer -files (item 0))))
      (go-loop []
               (when (<! reset-ch)
                 (om/set-state! owner {:file nil})
                 (recur))))
    om/IDidUpdate
    (did-update [_ _ prev-state]
      (let [file (om/get-state owner :file)]
        (when (and on-change (not= file (:file prev-state)))
          (on-change file))))
    om/IRenderState
    (render-state [_ {:keys [file uploading]}]
      (html [:div
             {:style {:position "relative"}}
             [:div.text-center.dropzone {:on-click #(.click (om/get-node owner "file"))}
              [:h3
               (or (and file (.-name file)) placeholder
                   "Drop file here or click to upload")]
              [:span.help-block "Maximum file size 100 MB"]]
             [:input
              {:ref       "file"
               :type      "file"
               :on-change #(handle-file owner (.. % -target -files (item 0)))
               :style     {:position "absolute"
                           :z-index  999
                           :opacity  0
                           :left     0
                           :top      0
                           :width    "100%"
                           :height   "100%"}}]]))))

(defn delete-attachment!
  "Quick and dirty delete function"
  [attachments-ref attachment]
  (handlers/open-modal
    {:type       :confirm
     :title      "Delete?"
     :message    "Are you sure you want to delete this file?"
     :on-confirm #(handlers/delete-attachment attachments-ref attachment)}))

(defn UploadData [_ owner]
  (reify
    om/IDisplayName (display-name [_] "UploadData")
    om/IInitState
    (init-state [_]
      {:reset-file-drop (chan)})
    om/IRenderState
    (render-state [_ {:keys [file reset-file-drop uploading]}]
      (let [{:keys [disabled] :as attachments} (observe-path owner [:form :fields :attachments])
            upload-form (observe-path owner [:upload_form])]
        (html [:div.UploadData {:class (if disabled "disabled")}
               (if-not (empty? (:value attachments))
                 [:div
                  [:table.table.table-hover
                   [:thead
                    [:tr [:th "Name"]]]
                   [:tbody
                    (for [attachment (:value attachments)]
                      (let [{:keys [file name]} (:value attachment)]
                        [:tr
                         [:td
                          [:a {:href (:value file) :target "blank"} (:value name)]
                          [:button.btn.btn-warn.btn-xs.pull-right
                           {:on-click #(delete-attachment! attachments attachment)
                            :disabled disabled}
                           [:span.glyphicon.glyphicon-minus]]]]))]]]
                 [:p "There are no data files attached to this record"])
               (when-not disabled
                 [:div
                  (om/build FileDrop
                            {:name         "file"
                             :max-filesize 100
                             :reset-ch     reset-file-drop
                             :on-change    #(om/set-state-nr! owner :file %)})
                  [:button.btn.btn-primary
                   {:on-click #(upload! owner upload-form file reset-file-drop)
                    :disabled (or uploading (not file))}
                   "Upload"]])])))))

(defn save!
  "Quick and dirty save function"
  [owner & [callback]]
  (om/set-state! owner :saving true)
  (let [done (chan)
        wait (async/map vector [(timeout 500) done])]
    (go (<! wait) (om/set-state! owner :saving false))
    (handlers/save-current-document done callback)))

(defn submit!
  "Submit a doc"
  [owner event {:keys [transition_url] :as doc}]
  (.preventDefault event)
  (save! owner
         (fn []
           (om/set-state! owner :saving true)
           (handlers/submit-current-document
             transition_url
             #(om/set-state! owner :saving false)
             (fn [{:keys [status failure] :as data}]
               (om/set-state! owner :saving false)
               (handlers/open-modal
                 {:type    :alert
                  :message (str "Unable to submit: " status " " failure)}))))))

(defn Lodge [_ owner]
  (reify
    om/IDisplayName (display-name [_] "Lodge")
    om/IRenderState
    (render-state [_ {:keys [saving]}]
      (let [{:keys [document urls site]} (observe-path owner [:context])
            {:keys [portal_title portal_url]} site
            {:keys [errors]} (observe-path owner [:progress])
            {:keys [disabled dirty]} (observe-path owner [:form])
            noteForDataManager (observe-path owner [:form :fields :noteForDataManager])
            is-are (if (> errors 1) "are" "is")
            plural (if (> errors 1) "s")
            has-errors? (and errors (> errors 0))
            submitted? (= (:status document) "Submitted")]
        (html [:div.Lodge
               [:p "Are you finished? Use this page to lodge your completed metadata record."]
               [:p "The Data Manager will be notified of your submission and will be in contact
               if any further information is required. Once approved, your data will be archived
               for discovery in the "
                (if portal_url
                  [:a {:href portal_url :target "_blank"} [:span.portal_title portal_title]]
                  [:span.portal_title portal_title])
                "."]
               [:p "How complete is your data?"]
               [:div
                {:style {:padding-top    5
                         :padding-bottom 5}}
                (if (= "Draft" (:status document))
                  (om/build TextareaField [:form :fields :noteForDataManager])
                  (when-not (str/blank? (:value noteForDataManager))
                    [:div
                     [:strong "Note for the data manager:"]
                     [:p (:value noteForDataManager)]]))]
               [:p

                [:button.btn.btn-primary.btn-lg
                 {:disabled (or has-errors? saving disabled submitted?)
                  :on-click #(submit! owner % document)}
                 (when saving
                   (list
                     [:img
                      {:src (str (:STATIC_URL urls)
                                 "metcalf/resources/public/img/saving.gif")}]
                     " "))
                 "Lodge data"]
                " "

                (if has-errors?
                  [:span.text-danger [:b "Unable to lodge: "]
                   "There " is-are " " [:span errors " error" plural
                                        " which must be corrected first."]]
                  [:span.text-success
                   [:b
                    (cond
                      saving "Submitting..."
                      (= (:status document) "Draft") "Ready to lodge"
                      (= (:status document) "Submitted") "Your record has been submitted."
                      :else (:status document))]])]

               (let [download-props {:href     (str (:export_url document) "?download")
                                     :on-click #(when dirty
                                                 (.preventDefault %)
                                                 (handlers/open-modal
                                                   {:type    :alert
                                                    :message "Please save changes before exporting."}))}]
                 [:div.user-export
                  [:p [:strong "Want to keep a personal copy of your metadata record?"]]
                  [:p
                   [:a download-props "Click here"] " to generate an XML version of your metadata submission. "
                   "The file generated includes all of the details you have provided under the
                    tabs, but not files you have uploaded."]
                  [:p
                   "Please note: this XML file is not the recommended way to share your metadata.
                    We want you to submit your data via 'lodging' the information.
                    This permits multi-user access via the portal in a more friendly format."]])])))))

(defn AddressField [address owner]
  (reify
    om/IDisplayName (display-name [_] "AddressField")
    om/IRender
    (render [_]
      (let [{:keys [city postalCode administrativeArea country deliveryPoint deliveryPoint2]} address]
        (html [:div.AddressField
               (om/build InputWidget (assoc deliveryPoint
                                       :on-change #(handlers/value-changed deliveryPoint %)))
               (om/build InputWidget (assoc deliveryPoint2
                                       :on-change #(handlers/value-changed deliveryPoint2 %)))
               [:div.row
                [:div.col-xs-6
                 (om/build InputWidget (assoc city
                                         :help "City"
                                         :on-change #(handlers/value-changed city %)))]
                [:div.col-xs-6
                 (om/build InputWidget (assoc administrativeArea
                                         :help "State/territory"
                                         :on-change #(handlers/value-changed administrativeArea %)))]]
               [:div.row
                [:div.col-xs-6
                 (om/build InputWidget (assoc postalCode
                                         :help "Postal / Zip code"
                                         :on-change #(handlers/value-changed postalCode %)))]
                [:div.col-xs-6
                 (om/build InputWidget (assoc country
                                         :help "Country"
                                         :on-change #(handlers/value-changed country %)))]]])))))

(defn organisation-option-renderer
  [{:keys [focusedOption focusOption option selectValue optionHeight] :as args}]
  (let [{:keys [organisationName]} option
        className (if (identical? option focusedOption)
                    "VirtualizedSelectOption VirtualizedSelectFocusedOption"
                    "VirtualizedSelectOption")]
    (html [:div
           {:class         className
            :on-click      #(selectValue option)
            :on-mouse-over #(focusOption option)
            :style         {:height optionHeight}}
           [:span organisationName]])))

(defn set-input-value
  [owner input]
  (when-let [select (some-> (om/get-ref owner "picker") (.-refs) (gobj/get "select"))]
    (.setState select #js {:inputValue (or input "")})))

(defn OrganisationPickerWidget
  [{:keys [value on-change on-input-change on-blur disabled] :as props} owner]
  (reify
    om/IDidMount
    (did-mount [_]
      (set-input-value owner value))
    om/IWillReceiveProps
    (will-receive-props [_ next-props]
      (utils/on-change props next-props [:value] #(set-input-value owner %)))
    om/IRender
    (render [_]
      (let [{:keys [URL_ROOT]} (observe-path owner [:context])]
        (ReactSelectAsync
          {:ref               "picker"
           :value             (or value "")                 ; NOTE: This gives us a clearing "x"
           :disabled          disabled
           :valueKey          "id"
           :labelKey          "organisationName"
           :loadOptions       (fn [input callback]
                                (ajax/GET (str URL_ROOT "/api/institution.json")
                                          {:handler
                                           (fn [{:strs [results] :as data}]
                                             (let [options (clj->js results)]
                                               (callback nil #js {:options  options
                                                                  :complete (= (get data "count")
                                                                               (count results))})))
                                           :error-handler
                                           (fn [e]
                                             (callback "Options loading error."))
                                           :params
                                           {:search input
                                            :offset 0
                                            :limit  100}}))
           :onChange          on-change
           :noResultsText     ""
           :onBlurResetsInput false
           :tabSelectsValue   false
           :onInputChange     on-input-change
           :onBlur            on-blur
           :placeholder       "Organisation"})))))

(defn SelectRoleWidget [role owner]
  (om/component
    (let [{:keys [options]} (observe-path owner [:api :rolecode])]
      (om/build SelectWidget (assoc role
                               :options (for [option options
                                              :let [Identifier (gobj/get option "Identifier")]]
                                          [Identifier (str/humanize Identifier)])
                               :on-change #(handlers/value-changed role %))))))

(defn OrganisationInputField
  "Input field for organisation which offers autocompletion of known
  institutions.  On autocomplete address details are updated."
  [party-path owner]
  (reify
    om/IDisplayName (display-name [_] "OrganisationInputField")
    om/IRender
    (render [_]
      (let [party-field (observe-path owner party-path)
            organisationName (-> party-field :value :organisationName)]
        (html [:div.OrganisationInputField
               ; FIXME: replace with autocomplete if we can find one
               (om/build OrganisationPickerWidget
                         {:value           (:value organisationName)
                          :disabled        (:disabled organisationName)
                          :on-input-change (fn [input]
                                             ; NOTE: this facilitates 'non-listed' orgs.
                                             (when-not (empty? input)
                                               (handlers/set-value organisationName input))
                                             nil)
                          :on-change       (fn [option]
                                             (handlers/update-address party-field (js-lookup option)))})])))))

(defn ResponsiblePartyField [path owner]
  (reify
    om/IDisplayName (display-name [_] "ResponsiblePartyField")
    om/IRender
    (render [_]
      (let [party (observe-path owner path)
            {:keys [individualName phone facsimile orcid
                    electronicMailAddress address role organisationName]} (:value party)]
        (html [:div.ResponsiblePartyField

               [:h4 (:value individualName)]
               (om/build InputWidget (assoc individualName
                                       :on-change #(handlers/value-changed individualName %)))

               (om/build InputWidget (assoc orcid
                                       :on-change #(handlers/value-changed orcid %)))

               (om/build SelectRoleWidget role)

               [:label "Organisation" (when (:required organisationName) " *")]
               (om/build OrganisationInputField path)

               [:label "Postal address"]
               (om/build AddressField address)

               [:div.ContactDetails

                (om/build InputWidget (assoc phone
                                        :on-change #(handlers/value-changed phone %)))

                (om/build InputWidget (assoc facsimile
                                        :on-change #(handlers/value-changed facsimile %)))

                (om/build InputWidget (assoc electronicMailAddress
                                        :on-change #(handlers/value-changed electronicMailAddress %)))]])))))

(defn FieldError [{:keys [errors label]} owner]
  (reify
    om/IDisplayName (display-name [_] "FieldError")
    om/IInitState (init-state [_] {})
    om/IRender
    (render [_]
      (html [:span.FieldError label ": " (first errors)]))))

(defn ManyFieldError [{:keys [errors label]} owner]
  (reify
    om/IDisplayName (display-name [_] "FieldError")
    om/IInitState (init-state [_] {})
    om/IRender
    (render [_]
      (html [:span.FieldError label ": " (or (first errors) "check field errors")]))))

(defn PageErrors [{:keys [page path]} owner]
  (reify
    om/IDisplayName (display-name [_] "PageErrors")
    om/IRender
    (render [_]
      (let [{:keys [show-errors] :as form} (observe-path owner path)
            fields (page-fields form page)
            error-fields (remove #(is-valid? {:fields %}) fields)
            msgs (for [field error-fields]
                   (if (and (:many field) (not (is-valid? {:fields field})))
                     (om/build ManyFieldError field)
                     (om/build FieldError field)))]
        (when (and show-errors (seq msgs))
          (html [:div.alert.alert-warning.alert-dismissable
                 [:button {:type     "button" :class "close"
                           :on-click #(handlers/hide-errors form)} "×"]
                 (if (> (count msgs) 1)
                   [:div
                    [:b "There are multiple fields on this page that require your attention:"]
                    [:ul (for [msg msgs] [:li msg])]]
                   (first msgs))]))))))

(defn NavbarHeader [props owner]
  (reify
    om/IDisplayName (display-name [_] "NavbarHeader")
    om/IRender
    (render [_]
      (let [{:keys [Dashboard]} (observe-path owner [:context :urls])
            {:keys [title tag_line]} (observe-path owner [:context :site])]
        (html [:div.navbar-header
               [:a.navbar-brand {:href Dashboard}
                title " " tag_line]])))))

(defn Navbar [props owner]
  (reify
    om/IDisplayName (display-name [_] "PageNavigation")
    om/IRender
    (render [_]
      (let [{:keys [username]} (observe-path owner [:context :user])
            {:keys [Dashboard account_profile account_logout]} (ref-path [:context :urls])
            {:keys [guide_pdf]} (observe-path owner [:context :site])]
        (html [:nav.navbar.navbar-inverse
               [:div.container
                (om/build NavbarHeader nil)
                [:ul.nav.navbar-nav.navbar-right
                 [:li [:a {:href Dashboard :title "My Records"}
                       [:span.glyphicon.glyphicon-folder-open]
                       " My Records"]]
                 [:li [:a {:href guide_pdf :target "_blank" :title "Help"}
                       [:span.glyphicon.glyphicon-book]
                       " Help"]]
                 [:li [:a {:href  account_profile
                           :title "Profile"}
                       [:span.glyphicon.glyphicon-user]
                       " " username]]
                 [:li [:a {:href  account_logout
                           :title "Logout"} [:span.glyphicon.glyphicon-log-out]
                       " Sign out"]]]]])))))

(defmulti PageTabView (fn [page owner] [(get page :name)
                                        (get page :tab :data-identification)]))

(defmethod PageView "404"
  [page owner]
  (om/component
    (let [state (observe-path owner)]
      (js/console.log "App State is" state)
      (dom/h1 nil "Page not found: " (get page :name)))))

(defmethod PageView "Error"
  [{:keys [text code detail]} owner]
  (om/component
    (html [:div
           (om/build Navbar nil)
           [:div.container
            [:div.PageViewBody
             [:p.lead "Oops! " (pr-str text)]
             [:p "The server responded with a " [:code code " " (pr-str text)] " error."]
             [:pre (pr-str detail)]]]])))

(defmethod PageTabView ["Edit" :data-identification]
  [page owner]
  (om/component
    (html [:div
           (om/build PageErrors {:page :data-identification :path [:form]})
           [:h2 "1. Data Identification"]
           (om/build TextareaField [:form :fields :identificationInfo :title])
           (om/build DateField [:form :fields :identificationInfo :dateCreation])
           (om/build SelectField [:form :fields :identificationInfo :topicCategory])
           (om/build SelectField [:form :fields :identificationInfo :status])
           (om/build SelectField [:form :fields :identificationInfo :maintenanceAndUpdateFrequency])])))

(defmethod PageTabView ["Edit" :what]
  [page owner]
  (om/component
    (html [:div
           (om/build PageErrors {:page :what :path [:form]})
           [:h2 "2. What"]
           [:span.abstract-textarea
            (om/build TextareaField [:form :fields :identificationInfo :abstract])]
           (om/build ThemeKeywords nil)
           (om/build ThemeKeywordsExtra nil)
           (om/build TaxonKeywordsExtra nil)])))

(defmethod PageTabView ["Edit" :when]
  [page owner]
  (om/component
    (html [:div
           (om/build PageErrors {:page :when :path [:form]})
           [:h2 "3. When"]
           (om/build DateField [:form :fields :identificationInfo :beginPosition])
           (om/build DateField [:form :fields :identificationInfo :endPosition])
           (om/build SelectField [:form :fields :identificationInfo :samplingFrequency])])))

(defmethod PageTabView ["Edit" :where]
  [page owner]
  (om/component
    (html [:div
           (om/build PageErrors {:page :where :path [:form]})
           [:h2 "4. Where"]
           (om/build GeographicCoverage nil)
           (om/build VerticalCoverage nil)])))

(defn CreditField [path owner]
  (reify
    om/IDisplayName (display-name [_] "CreditField")
    om/IRender
    (render [_]
      (html [:div.CreditField (om/build TextareaField path)]))))

(defn delete-contact! [owner group item e]
  (.stopPropagation e)
  (let [parties (-> group contact-groups :path ref-path)
        {:keys [selected-group selected-item]} (om/get-state owner)]
    (handlers/open-modal
      {:type       :confirm
       :title      "Delete?"
       :message    "Are you sure you want to delete this person?"
       :on-confirm (fn []
                     (when (and (= group selected-group) (<= item selected-item))
                       (om/set-state! owner :selected-item
                                      (when (> (count (:value parties)) 1)
                                        (-> selected-item dec (max 0)))))
                     (handlers/remove-party parties item))})))

(defn parties-list [owner group]
  (let [{:keys [disabled] :as parties} (-> group contact-groups :path ref-path)
        {:keys [selected-group selected-item]} (om/get-state owner)
        selected-item (when (= group selected-group) selected-item)]
    [:div.list-group
     (for [[item party] (-> parties :value enum)]
       [:a.list-group-item
        {:class    (if (= item selected-item) "active")
         :on-click #(om/set-state! owner {:selected-group group
                                          :selected-item  item})}
        [:span
         (let [name (get-in party [:value :individualName :value])]
           (if (blank? name) [:em "Last name, First name"] name))
         (when-not disabled
           [:button.btn.btn-warn.btn-xs.pull-right
            {:on-click (partial delete-contact! owner group item)}
            [:i.glyphicon.glyphicon-minus]])]])]))

(defmethod PageTabView ["Edit" :who]
  [page owner]
  (reify
    om/IDisplayName (display-name [_] "")
    om/IInitState
    (init-state [_]
      {:selected-group (ffirst
                         (filter
                           #(-> % second :path (conj :value) ref-path first)
                           (enum contact-groups)))
       :selected-item  0})
    om/IRenderState
    (render-state [_ {:keys [selected-group selected-item open hold]}]
      (let [cursors (mapv (comp (partial observe-path owner) :path) contact-groups)
            new! (fn [group & [field]]
                   (let [many-field (cursors group)]
                     (if field
                       (add-value! many-field (:value field))
                       (add-field! many-field))
                     (om/set-state!
                       owner {:selected-group group
                              :selected-item  (-> many-field :value count)})))
            all-parties (mapv (comp set
                                    (partial remove (comp blank? #(get-in % [:value :individualName :value])))
                                    :value)
                              cursors)
            all-parties-set (apply clojure.set/union all-parties)]
        (html [:div
               (om/build PageErrors {:page :who :path [:form]})
               [:h2 "6: Who"]
               [:div.row
                [:div.col-sm-4
                 (for [[group {:keys [title]}] (enum contact-groups)]
                   (let [parties (clojure.set/difference
                                   all-parties-set (all-parties group))]
                     (list
                       [:h4 title (when (get-in cursors [group :required]) " *")]
                       (parties-list owner group)
                       (when-not (get-in cursors [group :disabled])
                         [:div.dropdown
                          {:class   (if (= open group) "open")
                           :on-blur #(om/update-state!
                                      owner :open
                                      (fn [x] (when (or hold (not= x group)) x)))}
                          [:button.btn.btn-default.dropdown-toggle
                           {:on-click #(if (zero? (count parties))
                                        (new! group)
                                        (om/update-state!
                                          owner :open
                                          (fn [x] (when (not= x group) group))))}
                           [:span.glyphicon.glyphicon-plus]
                           " Add person"]
                          [:ul.dropdown-menu
                           {:on-mouse-enter #(om/set-state! owner :hold true)
                            :on-mouse-leave #(om/set-state! owner :hold false)}
                           [:li.dropdown-header "Copy person"]
                           (for [x parties]
                             [:li [:a {:tab-index -1
                                       :href      "#"
                                       :on-click  (fn [e]
                                                    (.preventDefault e)
                                                    (new! group x)
                                                    (om/set-state! owner :open false))}
                                   (get-in x [:value :individualName :value])]])
                           [:li.divider]
                           [:li [:a {:href     "#"
                                     :on-click (fn [e]
                                                 (.preventDefault e)
                                                 (new! group)
                                                 (om/set-state! owner :open false))}
                                 "New person"]]]]))))]
                [:div.col-sm-8
                 (when (and selected-group selected-item)
                   (om/build ResponsiblePartyField
                             (-> contact-groups
                                 (get-in [selected-group :path])
                                 (conj :value selected-item))))]]

               [:h2 "Other credits"]
               (om/build
                 TableModalEdit
                 {:form       CreditField
                  :title      "Credit"
                  :field-path [:form :fields :identificationInfo :credit]})])))))

(defmethod PageTabView ["Edit" :how]
  [page owner]
  (om/component
    (html [:div
           (om/build PageErrors {:page :how :path [:form]})
           [:h2 "5: How"]
           (om/build TextareaField
                     [:form :fields :dataQualityInfo :statement])])))


(defn UseLimitationsFieldEdit [path owner]
  (reify
    om/IDisplayName (display-name [_] "UseLimitationsFieldEdit")
    om/IRender
    (render [_]
      (om/build TextareaFieldProps {:path path :rows 3}))))

(defn UseLimitations [path owner]
  (reify
    om/IDisplayName (display-name [_] "UseLimitations")
    om/IRender
    (render [_]
      (let [list-field (observe-path owner path)]
        (html [:div.SupplementalInformation
               (label-template list-field)
               (om/build TableModalEdit
                         {:form        UseLimitationsFieldEdit
                          :title       "Use Limitation"
                          :placeholder ""
                          :add-label   "Add use limitation"
                          :field-path  path})])))))


(defn ResourceConstraints [props owner]
  (reify
    om/IDisplayName (display-name [_] "ResourceConstraints")
    om/IRender
    (render [_]
      (html [:div.ResourceConstraints
             (om/build SelectField [:form :fields :identificationInfo :creativeCommons])
             [:p.help-block "Learn more about which license is right for you at "
              [:a {:href   "https://creativecommons.org/choose/"
                   :target "_blank"}
               "Creative Commons"]]]))))

(defn SupplementalFieldEdit [path owner]
  (reify
    om/IDisplayName (display-name [_] "SupplementalFieldEdit")
    om/IRender
    (render [_]
      (om/build TextareaFieldProps {:path path
                                    :rows 3}))))

(defn SupplementalInformation [path owner]
  (reify
    om/IDisplayName (display-name [_] "SupplementalInformation")
    om/IRender
    (render [_]
      (let [list-field (observe-path owner path)]
        (html [:div.SupplementalInformation
               (label-template list-field)
               (om/build TableModalEdit
                         {:form        SupplementalFieldEdit
                          :title       "Publication"
                          :placeholder ""
                          :add-label   "Add publication"
                          :field-path  path})])))))

(defn SupportingResourceRowEdit [path owner]
  (reify
    om/IDisplayName (display-name [_] "SupportingResourceDetail")
    om/IRender
    (render [_]
      (let [props (observe-path owner path)
            {:keys [description url]} (:value props)]
        (html [:div
               (om/build InputField {:path (om/path description)})
               (om/build InputField {:path (om/path url)})])))))

(defn SupportingResources [path owner]
  (om/component
    (html
      [:div
       [:label "Supporting Resources"]
       (om/build TableModalEdit
                 {:ths         ["Title" "URL"]
                  :tds-fn      (comp (partial map (comp #(or % "--") :value)) (juxt :description :url) :value)
                  :form        SupportingResourceRowEdit
                  :title       "Supporting Resource"
                  :placeholder ""
                  :add-label   "Add supporing resource"
                  :field-path  path})])))

(defmethod PageTabView ["Edit" :about]
  [page owner]
  (om/component
    (html [:div
           (om/build PageErrors {:page :about :path [:form]})
           [:h2 "7: About Dataset"]
           [:h4 "Data parameters"]
           (om/build DataParametersTable [:form :fields :identificationInfo :dataParameters])
           [:br]
           [:h4 "Resource constraints"]
           (om/build ResourceConstraints nil)
           (om/build UseLimitations [:form :fields :identificationInfo :useLimitations])
           (om/build TextareaField [:form :fields :identificationInfo :otherConstraints])
           [:br]
           [:h4 "Supplemental information"]
           (om/build SupplementalInformation [:form :fields :identificationInfo :supplementalInformation])
           (om/build SupportingResources [:form :fields :supportingResources])
           [:br]
           [:h4 "Distribution"]
           (om/build InputField {:path [:form :fields :distributionInfo :distributionFormat :name]})
           (om/build InputField {:path [:form :fields :distributionInfo :distributionFormat :version]})])))

(defn DataSourceRowEdit [path owner]
  (reify
    om/IDisplayName (display-name [_] "DataSourceDetail")
    om/IRender
    (render [_]
      (let [props (observe-path owner path)
            {:keys [description url name protocol]} (:value props)]
        (html [:div
               (om/build InputField {:path (om/path description)})
               (om/build SelectField (om/path protocol))
               (om/build InputField {:path (om/path url)})
               (om/build InputField {:path (om/path name)})])))))

(defn DataSources [props owner]
  (om/component
    (html
      [:div
       (om/build TableModalEdit {:ths        ["Title" "URL" "Layer"]
                                 :tds-fn     (comp (partial map (comp #(or % "--") :value)) (juxt :description :url :name) :value)
                                 :form       DataSourceRowEdit
                                 :title      "Data services"
                                 :field-path [:form :fields :dataSources]})])))

(defmethod PageTabView ["Edit" :upload]
  [page owner]
  (om/component
    (html [:div
           (om/build PageErrors {:page :upload :path [:form]})
           [:h2 "8: Upload Data"]
           (om/build UploadData nil)
           [:h2 "Data Services"]
           (om/build DataSources nil)])))

(defmethod PageTabView ["Edit" :lodge]
  [page owner]
  (om/component
    (html [:div
           (om/build PageErrors {:page :lodge :path [:form]})
           [:h2 "9: Lodge Metadata Draft"]
           (om/build Lodge nil)])))

(defn ProgressBar [props owner]
  (reify
    om/IDisplayName (display-name [_] "ProgressBar")
    om/IRender
    (render [_]
      (let [{:keys [fields errors empty required required-errors]
             :as   progress} (observe-path owner [:progress])
            can-submit? (= errors 0)
            pct (-> (- fields empty) (/ fields) (* 100) int (str "%"))]
        (html
          [:div {:style {:height 20}}
           [:div.ProgressBar {:style {:width   120
                                      :display "inline-block"}
                              :title "Required"}
            [:div.progress
             [:div.progress-bar {:class (if can-submit?
                                          "progress-bar-success"
                                          "progress-bar-danger")
                                 :style {:width pct}}
              pct]]]])))))

(defn handle-archive-click
  []
  (handlers/open-modal
    {:type       :confirm
     :title      "Archive?"
     :message    "Are you sure you want to archive this record?"
     :on-confirm handlers/archive-current-document}))

(defmethod PageView "Edit"
  [page owner]
  (reify
    om/IRenderState
    (render-state [_ {:keys [saving]}]
      (let [progress (observe-path owner [:progress])
            {:keys [user urls]} (observe-path owner [:context])
            {:keys [dirty disabled] :as form} (observe-path owner [:form])
            {:keys [status title last_updated]} (observe-path owner [:context :document])]
        (html [:div
               (om/build Navbar nil)
               [:div.pagehead
                [:div.container
                 [:div.pull-right
                  [:button.btn.btn-default.text-warn {:on-click handle-archive-click
                                                      :disabled disabled}
                   [:span.glyphicon.glyphicon-trash]
                   " Archive"] " "
                  [:button.btn.btn-primary {:disabled (or disabled (not dirty) saving)
                                            :on-click #(save! owner)}
                   (cond
                     saving [:img {:src (str (:STATIC_URL urls) "metcalf/resources/public/img/saving.gif")}]
                     dirty [:span.glyphicon.glyphicon-floppy-disk]
                     :else [:span.glyphicon.glyphicon-floppy-saved])
                   " Save"]]
                 [:p.lead [:b (:username user)] " / " (if (blank? title) "Untitled" title)
                  " "
                  [:span.label.label-info {:style {:font-weight "normal"}} status]
                  [:br]
                  [:small [:i {:style {:color     "#aaa"
                                       :font-size "0.7em"}}
                           "Last edited " (-> last_updated js/moment .fromNow)]]]]]
               [:div.Home.container
                [:ul.nav.nav-tabs
                 (for [[id text] [[:data-identification "Data identification"]
                                  [:what "What"]
                                  [:when "When"]
                                  [:where "Where"]
                                  [:how "How"]
                                  [:who "Who"]
                                  [:about "About"]
                                  [:upload "Data sources"]
                                  [:lodge "Lodge"]]]
                   (let [error-count (get-in progress [:page-errors id])
                         has-errors? (and error-count (> error-count 0))
                         text [:span text " " (if has-errors?
                                                [:b.text-warning "*"])]]
                     [:li {:class (if (= id (get page :tab :data-identification)) "active")}
                      [:a {:style    {:cursor "pointer"}
                           :on-click #(do #_(save!)
                                       (if has-errors? (handlers/show-errors form))
                                       (handlers/set-tab page id))} text]]))
                 [:div.pull-right.hidden-xs.hidden-sm
                  (when-not disabled (om/build ProgressBar nil))]]
                [:div.PageViewBody
                 (om/build PageTabView page)]]])))))

(defn create-document-ch
  [{:keys [url] :as form}]
  (let [result-ch (chan)]
    (handlers/create-document url form result-ch)
    result-ch))

(defn FormErrors [{:keys [path] :as props} owner]
  (reify
    om/IDisplayName (display-name [_] "FormErrors")
    om/IRender
    (render [_]
      (let [{:keys [fields show-errors] :as form} (observe-path owner path)
            fields-with-errors (filter (comp :errors second) fields)]
        (html (if (and show-errors (seq fields-with-errors))
                [:div.alert.alert-danger
                 [:p [:b "The following fields need your attention"]]
                 [:ul (for [[k {:keys [label errors]}] fields-with-errors]
                        [:li
                         (or label (name k)) ": "
                         (string/join ". " errors)])]]))))))

(defn NewDocumentForm [props owner]
  (reify
    om/IDisplayName (display-name [_] "NewDocumentForm")
    om/IRender
    (render [_]
      (html [:div/NewDocumentForm
             (om/build FormErrors {:path [:create_form]})
             (om/build InputField {:path [:create_form :fields :title]})
             (om/build SelectField [:create_form :fields :template])]))))

(defn handle-dashboard-create-save []
  (let [form-ref (ref-path [:create_form])]
    (if (is-valid? form-ref)
      (go (let [{:keys [success data]} (<! (create-document-ch (om/value form-ref)))]
            (if success
              (do
                (handlers/reset-form form-ref)
                (handlers/hide-errors form-ref)
                (handlers/close-modal)
                (aset js/location "href" (-> data :document :url)))
              (if (= (:status data) 400)
                (handlers/load-error-page data)
                (do
                  (handlers/load-errors form-ref (:response data))
                  (handlers/show-errors form-ref))))))
      (handlers/show-errors form-ref))
    nil))

(defmethod ModalDialog :DashboardCreateModal
  [props owner]
  (om/component
    (om/build Modal {:ok-copy      "OK"
                     :modal-header (html [:span [:span.glyphicon.glyphicon-list] " " "Create a new record"])
                     :modal-body   #(om/build NewDocumentForm nil)
                     :on-dismiss   #(handlers/close-modal)
                     :on-cancel    #(handlers/close-modal)
                     :on-save      #(handle-dashboard-create-save)})))

(defn NewDocumentButton [props owner]
  (reify
    om/IDisplayName (display-name [_] "NewDocumentForm")
    om/IInitState (init-state [_] {:title ""})
    om/IRenderState
    (render-state [_ {:keys [title ch]}]
      (html [:button.btn.btn-primary {:on-click #(handlers/open-modal {:type :DashboardCreateModal})}
             [:span.glyphicon.glyphicon-plus]
             " Create new record"]))))

(defn transite-doc [url transition event]
  (let [trans-name (first (clojure.string/split transition "_"))]
    (handlers/open-modal
      {:type       :confirm
       :title      trans-name
       :message    (str "Are you sure you want to " trans-name " this record?")
       :on-confirm #(handlers/transition-current-document
                     url transition
                     (fn [{:keys [status failure response status-text] :as data}]
                       (handlers/open-modal
                         {:type    :alert
                          :message (str "Unable to " trans-name)})))})
    (.preventDefault event)))

(defn clone-doc [url event]
  (handlers/open-modal
    {:type       :confirm
     :title      "Clone?"
     :message    (str "Are you sure you want to clone this record?")
     :on-confirm #(handlers/clone-document url)})
  (.preventDefault event))

(defn DocumentTeaser [{:keys [url title last_updated status transitions
                              transition_url clone_url] :as doc} owner]
  (reify
    om/IDisplayName (display-name [_] "DocumentTeaser")
    om/IInitState (init-state [_] {})
    om/IRenderState
    (render-state [_ {:keys []}]
      (let [transitions (set transitions)
            transite (partial transite-doc transition_url)]
        (html [:div.list-group-item.DocumentTeaser
               [:div.pull-right
                (if (contains? transitions "archive")
                  [:span.btn.btn-default.noborder.btn-xs
                   {:on-click (partial transite "archive")}
                   [:span.glyphicon.glyphicon-trash] " archive"])
                (if (contains? transitions "delete_archived")
                  [:span.btn.btn-default.noborder.btn-xs
                   {:on-click (partial transite "delete_archived")}
                   [:span.glyphicon.glyphicon-remove] " delete"])
                (if (contains? transitions "restore")
                  [:span.btn.btn-default.noborder.btn-xs
                   {:on-click (partial transite "restore")}
                   [:span.glyphicon.glyphicon-open] " restore"])
                [:span.btn.btn-default.noborder.btn-xs
                 {:on-click (partial clone-doc clone_url)}
                 [:span.glyphicon.glyphicon-duplicate] " clone"]
                [:span.btn.btn-default.noborder.btn-xs {:on-click #(aset js/location "href" url)}
                 [:span.glyphicon.glyphicon-pencil] " edit"]]
               [:p.lead.list-group-item-heading
                [:span.link {:on-click #(aset js/location "href" url)}
                 [:b (:username (:owner doc))] " / " title]
                " "
                [:span.label.label-info {:style {:font-weight "normal"}} status]]
               [:p.list-group-item-text
                [:i {:style {:color     "#aaa"
                             :font-size "0.9em"}}
                 (if-not (empty? last_updated)
                   [:span
                    "Last edited " (.fromNow (js/moment last_updated))
                    " by " (:username (:owner doc))]
                   "Has not been edited yet")]]])))))

(def active-status-filter #{"Draft" "Submitted"})

(defmethod PageView "Dashboard"
  [{:keys [status-filter]
    :or   {status-filter active-status-filter}
    :as   page} owner]
  (om/component
    (let [{:keys [documents status urls user]} (observe-path owner [:context])
          status-freq (frequencies (map :status documents))
          all-statuses (set (keys status-freq))
          relevant-status-filter (set/intersection status-filter all-statuses)
          filtered-docs (->> documents
                             (filter (fn [{:keys [status]}]
                                       (contains? relevant-status-filter status)))
                             (sort-by :last_updated)
                             (reverse))]
      (html [:div
             (om/build Navbar nil)
             [:div.container
              [:span.pull-right (om/build NewDocumentButton nil)]
              [:h1 "My Records"]
              [:div.row
               [:div.col-sm-9
                [:div.list-group
                 (for [filtered-doc filtered-docs]
                   ^{:key (:url filtered-doc)} (om/build DocumentTeaser filtered-doc))
                 (if (empty? documents)
                   [:a.list-group-item {:on-click #(do (handlers/open-modal {:type :DashboardCreateModal})
                                                       (.preventDefault %))
                                        :href     (:Create urls)}
                    [:span.glyphicon.glyphicon-star.pull-right]
                    [:p.lead.list-group-item-heading [:b (:username user)] " / My first record "]
                    [:p.list-group-item-text "Welcome!  Since you're new here, we've created your first record. "
                     [:span {:style {:text-decoration "underline"}} "Click here"] " to get started."]]
                   (if (empty? filtered-docs)
                     (if (= status-filter active-status-filter)
                       [:div
                        [:p "You don't have any active records: "
                         [:a {:on-click #(handlers/show-all-documents page status-freq)}
                          "show all documents"] "."]
                        (om/build NewDocumentButton nil)]
                       [:div
                        [:p "No documents match your filter: "
                         [:a {:on-click #(handlers/show-all-documents page status-freq)}
                          "show all documents"] "."]
                        (om/build NewDocumentButton nil)])))]]
               [:div.col-sm-3
                (if-not (empty? status-freq)
                  [:div
                   (for [[sid sname] status]
                     (let [freq (get status-freq sid)]
                       [:div [:label
                              [:input {:type     "checkbox"
                                       :disabled (not freq)
                                       :checked  (contains? relevant-status-filter sid)
                                       :on-click #(handlers/toggle-status-filter page status-filter sid)}]
                              " " sname
                              (if freq [:span.freq " (" freq ")"])]]))])]]]]))))

(defmethod PageView "Theme"
  [page owner]
  (om/component
    (html [:div.PageViewTheme.container
           (om/build BackButton nil)
           [:h1 "Research theme keywords"]
           [:p.help-block "Select keyword(s) to add to record"]
           (om/build KeywordsThemeTable nil)])))

(defn LegacyIECompatibility [props owner]
  (reify
    om/IDisplayName
    (display-name [_] "LegacyIECompatibility")
    om/IRender
    (render [_]
      (html [:div.LegacyIECompatibility

             [:div.row
              [:div.col-md-6.col-md-offset-3
               [:div.container.box
                [:h1 "Browser not supported"]
                [:p.lead "The work request system doesn't support early versions of Internet Explorer."]
                [:p.lead "Please use Google Chrome to access this system or upgrade your browser."]
                [:hr]
                [:p "Related links:"]
                [:ul
                 [:li [:a {:href "http://www.techtimes.com/articles/12659/20140811/dingdong-internet-explorer-8-is-dead-microsoft-will-end-its-life-in-january-2016.htm"}
                       "Dingdong, Internet Explorer 8 is dead: Microsoft will end its life in January 2016 (TechTimes)"]
                  " and IE 9 will follow a year later."]
                 [:li [:a {:href "http://www.computerworld.com/article/2492571/web-apps/google-to-drop-support-for-ie8-on-nov--15.html"}
                       "Google to drop support for IE8 on Nov 15 [2012]"]]
                 [:li [:a {:href "http://www.w3counter.com/globalstats.php"}
                       "Market share of IE8 and IE9 is around 2% each world wide."]]]
                [:br]]]]]))))

(defmethod ModalDialog :alert
  [{:keys [message]} owner]
  (om/component
    (om/build Modal
              {:modal-header (html [:span [:span.glyphicon.glyphicon-exclamation-sign]
                                    " " "Alert"])
               :dialog-class "modal-sm"
               :modal-body   message
               :on-dismiss   #(handlers/close-modal)
               :on-save      #(handlers/close-modal)})))

(defmethod ModalDialog :confirm
  [{:keys [message title on-confirm on-cancel]} owner]
  (om/component
    (om/build Modal
              {:modal-header (html [:span [:span.glyphicon.glyphicon-question-sign] " " title])
               :dialog-class "modal-sm"
               :modal-body   message
               :on-dismiss   #(do (handlers/close-modal) (when on-cancel (on-cancel)))
               :on-cancel    #(do (handlers/close-modal) (when on-cancel (on-cancel)))
               :on-save      #(do (handlers/close-modal) (when on-confirm (on-confirm)))})))

(defn ModalStack [_ owner]
  (reify
    om/IDisplayName (display-name [_] "DashboardCreateModal")
    om/IRender
    (render [_]
      (let [modal-stack (observe-path owner [:alert])
            modal-props (peek modal-stack)]
        (when modal-props
          (let [breadcrumbs (mapv :title modal-stack)]
            (om/build ModalDialog (assoc modal-props :breadcrumbs breadcrumbs))))))))

(comment
  (for [[uuid & rows] (observe-path owner [:theme :table])]
    (let [path (remove empty? rows)]
      {:path  path
       :value uuid
       :label (last path)})))

(defn AppRoot [app owner]
  (reify
    om/IDisplayName (display-name [_] "AppRoot")
    om/IRender
    (render [_]
      (html
        [:div (om/build ModalStack nil)
         (if (and guseragent/IE (not (guseragent/isVersionOrHigher 10)))
           (om/build LegacyIECompatibility nil)
           (om/build PageView (:page app)))]))))
