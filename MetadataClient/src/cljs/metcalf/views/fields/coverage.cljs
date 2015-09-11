(ns metcalf.views.fields.coverage
  (:require [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [sablono.core :refer-macros [html]]
            [metcalf.globals :refer [observe-path]]
            [openlayers-om-components.geographic-element :refer [BoxMap]]
            [metcalf.views.form :refer [TableInlineEdit]]
            [metcalf.views.widget :refer [InputField SelectField CheckboxField]]
            [condense.fields :refer [Input]]
            [clojure.string :as string]
            [metcalf.logic :as logic]
            [metcalf.handlers :as handlers]))

(defn ->float [s]
  (let [f (js/parseFloat s)]
    (if (js/isNaN f) nil f)))

(defn CoordInputField [{:keys [on-change abbr min max] :as props} owner]
  (reify
    om/IDisplayName (display-name [_] "CoordInputField")
    om/IInitState (init-state [_] {:value nil})
    om/IWillMount
    (will-mount [_]
      (om/set-state! owner :value (om/get-props owner :value)))
    om/IDidUpdate
    (did-update [_ prev-props _]
      (let [p0 (:value prev-props)
            p1 (:value props)]
        (if-not (= p0 p1)
          (om/set-state! owner :value p1))))
    om/IRenderState
    (render-state [_ {:keys [value errors] :as state}]
      (let [change! (fn [e]
                      (om/set-state! owner :errors nil)
                      (let [v (-> (.. e -target -value)
                                  (or "")
                                  (string/replace #"[^\d\.-]" "")
                                  (->float))]
                        (if (and (not (js/isNaN v))
                                 (or (not min) (<= min v))
                                 (or (not max) (<= v max)))
                          (on-change v)
                          (om/set-state! owner :errors [true]))))]
        (om/build Input {:value       (or value "")
                         :show-errors true
                         :errors      errors
                         :on-blur     #(change! %)
                         :on-change   #(om/set-state! owner :value (.. % -target -value))
                         :addon-after (html [:span.input-group-addon [:span.coord-after "°" [:span.coord-abbr abbr]]])})))))

(defn CoordField [path owner]
  (reify
    om/IInitState
    (init-state [_] {})
    om/IRenderState
    (render-state [_ {:keys []}]
      (let [props (observe-path owner path)
            {:keys [northBoundLatitude westBoundLongitude eastBoundLongitude southBoundLatitude]} (:value props)]
        (let [n-field (om/build CoordInputField {:abbr        "N"
                                                 :max         90 :min -90
                                                 :placeholder "Northbound" :value (:value northBoundLatitude)
                                                 :on-change   #(handlers/field-update! owner northBoundLatitude %)})
              e-field (om/build CoordInputField {:abbr "E"
                                                 :max 180 :min -180
                                                 :placeholder "Eastbound" :value (:value eastBoundLongitude)
                                                 :on-change   #(handlers/field-update! owner eastBoundLongitude %)})
              s-field (om/build CoordInputField {:abbr "S"
                                                 :max 90 :min -90
                                                 :placeholder "Southbound" :value (:value southBoundLatitude)
                                                 :on-change   #(handlers/field-update! owner southBoundLatitude %)})
              w-field (om/build CoordInputField {:abbr "W"
                                                 :max 180 :min -180
                                                 :placeholder "Westbound" :value (:value westBoundLongitude)
                                                 :on-change   #(handlers/field-update! owner westBoundLongitude %)})]
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
    om/IRenderState
    (render-state [_ {:keys [boundaries]}]
      (let [{:keys [disabled] :as geographicElement} (observe-path owner [:form :fields :identificationInfo :geographicElement])
            geographicElements (:value geographicElement)
            extents (map (comp logic/geographicElement->extent :value) geographicElements)]
        (html [:div.GeographicCoverage
               [:h4 "Geographic Coverage"]
               [:div.row
                [:div.col-sm-6
                 (om/build BoxMap
                           {:value                (mapv (fn [extent] [:box extent]) extents)
                            :disabled             disabled
                            :center               [147 -42]
                            :zoom                 6
                            :on-boxend            (partial handlers/add-extent! geographicElements)
                            :on-view-change       (fn [extent]
                                                    (om/set-state! owner :boundaries
                                                                   (mapv #(-> %2 (- %1) (* 0.25) (+ %1)) extent (->> extent cycle (drop 2)))))
                            :mark-change-debounce 400
                            :on-mark-change       (partial handlers/update-extent! geographicElements)})

                 [:em "Hold down shift to draw a box."]]
                [:div.col-sm-6
                 (om/build TableInlineEdit {:ths           ["North" "West" "South" "East"]
                                            :tds-fn        (fn [geographicElement]
                                                             (let [{:keys [northBoundLatitude westBoundLongitude
                                                                           eastBoundLongitude southBoundLatitude]}
                                                                   (:value geographicElement)]
                                                               [(print-nice (:value northBoundLatitude))
                                                                (print-nice (:value westBoundLongitude))
                                                                (print-nice (:value southBoundLatitude))
                                                                (print-nice (:value eastBoundLongitude))]))
                                            :default-field {:value (logic/extent->geographicElement boundaries)}
                                            :form          CoordField
                                            :field-path    [:form :fields :identificationInfo :geographicElement]
                                            :placeholder   [:em {:style {:color "#a94442"}} "Specify the location(s) of this study."]})]]])))))

(defn VerticalCoverage [props owner]
  (reify
    om/IDisplayName (display-name [_] "VerticalCoverage")
    om/IRender
    (render [_]
      (let [{hasVerticalExtent :value} (observe-path owner [:form :fields :identificationInfo :verticalElement :hasVerticalExtent])]
        (html [:div.VerticalCoverage
               [:h4 "Vertical Coverage"]
               (om/build CheckboxField [:form :fields :identificationInfo :verticalElement :hasVerticalExtent])
               (if hasVerticalExtent
                 [:div
                  (om/build SelectField [:form :fields :identificationInfo :verticalElement :verticalCRS])
                  (om/build InputField
                            {:path [:form :fields :identificationInfo :verticalElement :minimumValue]
                             :class "wauto"})
                  (om/build InputField
                            {:path [:form :fields :identificationInfo :verticalElement :maximumValue]
                             :class "wauto"})])])))))