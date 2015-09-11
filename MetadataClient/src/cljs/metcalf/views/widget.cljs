(ns metcalf.views.widget
  (:require [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [sablono.core :refer-macros [html]]
            [cljs.core.async :as async :refer [put!]]
            [metcalf.globals :refer [observe-path]]
            [condense.fields :refer [Input Checkbox ExpandingTextarea validate-required-field
                                     help-block-template label-template
                                     del-value! add-value! add-field!]]
            [om-tick.bootstrap :refer [Select Date validation-state]]
            [condense.autocomplete :refer [AutoComplete]]))

(defn field-update! [owner field v]
  (om/update! field :value v)
  (put! (:pub-chan (om/get-shared owner)) {:topic (om/path field) :value v}))

(defn handle-value-change [owner field event]
  (field-update! owner field (-> event .-target .-value)))

(defn handle-checkbox-change [owner field event]
  (field-update! owner field (-> event .-target .-checked)))

(defn InputField [props owner]
  (reify
    om/IDisplayName (display-name [_] "InputField")
    om/IRenderState
    (render-state [_ {:keys []}]
      (let [field (observe-path owner (:path props))]
        (om/build Input (-> field
                            (merge (dissoc props :path))
                            (assoc
                              :on-blur #(om/update! field :show-errors true)
                              :on-change #(handle-value-change owner field %))))))))

(defn DecimalField [path owner]
  (reify
    om/IDisplayName (display-name [_] "DecimalField")
    om/IRenderState
    (render-state [_ {:keys []}]
      (let [field (observe-path owner path)]
        (om/build Input (assoc field
                          :class "wauto"
                          :on-blur #(om/update! field :show-errors true)
                          :on-change #(handle-value-change owner field %)))))))

(defn DateField [path owner]
  (reify
    om/IDisplayName (display-name [_] "DateField")
    om/IRender
    (render [_]
      (let [field (observe-path owner path)]
        (om/build Date (assoc field :class "wauto"
                                    :display-format "DD-MM-YYYY"
                                    :on-blur #(om/update! field :show-errors true)
                                    :on-date-change #(om/update! field :value %)))))))

(defn SelectField [path owner]
  (reify
    om/IDisplayName (display-name [_] "SelectField")
    om/IRender
    (render [_]
      (let [{:keys [options default-option disabled] :as field} (observe-path owner path)]
        (om/build Select (assoc field
                           :class "wauto"
                           :disabled (or disabled (empty? options))
                           :default-option (if-not (empty? options) default-option "")
                           :on-blur #(om/update! field :show-errors true)
                           :on-change #(handle-value-change owner field %)))))))

(defn AutoCompleteField [path owner]
  (reify
    om/IDisplayName (display-name [_] "AutoCompleteField")
    om/IRenderState
    (render-state [_ {:keys []}]
      (let [{:keys [options default-option disabled] :as field} (observe-path owner path)]
        (om/build AutoComplete (assoc field
                                 :disabled (or disabled (empty? options))
                                 :default-option (if-not (empty? options) default-option "")
                                 :on-change #(handle-value-change owner field %)))))))

(defn TextareaFieldProps [props owner]
  (reify
    om/IDisplayName (display-name [_] "TextareaField")
    om/IRenderState
    (render-state [_ {:keys []}]
      (let [{:keys [path]} props
            field (observe-path owner path)]
        (om/build ExpandingTextarea (merge field (dissoc props :path)
                                           {:on-change #(handle-value-change owner field %)
                                            :on-blur #(om/update! field :show-errors true)}))))))

(defn TextareaField [path owner]
  (reify
    om/IDisplayName (display-name [_] "TextareaField")
    om/IRenderState
    (render-state [_ {:keys []}]
      (let [field (observe-path owner path)]
        (om/build ExpandingTextarea (assoc field
                                      :on-blur #(om/update! field :show-errors true)
                                      :on-change #(handle-value-change owner field %)))))))

(defn CheckboxField [path owner]
  (reify
    om/IDisplayName (display-name [_] "CheckboxField")
    om/IRenderState
    (render-state [_ {:keys []}]
      (let [field (observe-path owner path)]
        (om/build Checkbox (assoc field :checked (:value field)
                                        :on-blur #(om/update! field :show-errors true)
                                        :on-change #(handle-checkbox-change owner field %)))))))

