(ns metcalf.views.form
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [cljs.core.async :as async :refer [<! timeout]]
            [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [sablono.core :refer-macros [html]]
            [metcalf.globals :refer [observe-path app-state]]
            [condense.utils :refer [fmap]]
            [condense.fields :refer [Input Checkbox ExpandingTextarea validate-required-field
                                     help-block-template label-template
                                     del-value! add-value! add-field!]]
            [metcalf.views.widget :refer [InputField TextareaFieldProps]]
            [metcalf.views.highlight :refer [handle-highlight-new]]))

(defn TableInlineEdit [{:keys [ths tds-fn form field-path
                               placeholder default-field]
                        :or   {tds-fn #(list (:value %))}} owner]
  (reify
    om/IDisplayName (display-name [_] "TableInlineEdit")
    om/IInitState (init-state [_] {:cursor nil
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
               (help-block-template many-field)
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
                          [:td {:class highlight-class
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

(defn DataParameterRowEdit [path owner]
  (reify
    om/IDisplayName (display-name [_] "DataParameterDetail")
    om/IRender
    (render [_]
      (let [props (observe-path owner path)
            {:keys [name longName parameterDescription unit]} (:value props)]
        (html [:div.DataParameterMaster
               [:h3 "Edit parameter"]
               (om/build InputField {:path (om/path longName)})
               [:div.row
                [:div.col-sm-6
                 (om/build InputField {:path (om/path name)})]
                [:div.col-sm-6
                 (om/build InputField {:path (om/path unit)})]]
               [:label "Additional parameter info"]
               (om/build TextareaFieldProps
                         {:path (om/path parameterDescription)})])))))


(defn DataParametersTable [path owner]
  (reify
    om/IDisplayName (display-name [_] "DataParameters")
    om/IRender
    (render [_]
      (html [:div.DataParametersTable
             (om/build TableInlineEdit {:ths        ["Name" "Long name" "Unit of measurement" "Description"]
                                        :tds-fn     (fn [field]
                                                      (let [{:keys [parameterDescription unit name longName]}
                                                            (fmap (comp #(or % "--") :value) (:value field))]
                                                        [name longName unit parameterDescription]))
                                        :form       DataParameterRowEdit
                                        :field-path [:form :fields :identificationInfo :dataParameters]})]))))
