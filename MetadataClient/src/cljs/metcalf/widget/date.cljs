(ns metcalf.widget.date
  (:require [om.core :as om]
            [sablono.core :refer-macros [html]]
            cljsjs.moment
            cljsjs.pikaday))

(defn init-date-state
  [{:keys [display-format value]
    :or   {display-format "YYYY-MM-DD"}}]
  {:display-format display-format
   :value          value})

(defn PikadayInputWidget
  [{:keys                          [date-state on-change on-blur disabled]
    {:keys [display-format value]} :date-state} owner]
  (reify

    om/IDisplayName
    (display-name [_] "Date")

    om/IInitState
    (init-state [_] {:picker nil})

    om/IDidMount
    (did-mount [_]
      (let [picker (js/Pikaday.
                     #js {:field    (om/get-node owner "datepicker")
                          :onSelect (fn [inst]
                                      (on-change (assoc date-state :value inst)))
                          :format   display-format})]
        (.setDate picker value)
        (om/set-state! owner :picker picker)))

    om/IWillUnmount
    (will-unmount [this]
      (.destroy (om/get-state owner :picker)))

    om/IWillReceiveProps
    (will-receive-props [_ next-props]
      (let [before date-state
            after (:date-state next-props)]
        (when (and (not= (:value after) (:value before)))
          (.setDate (om/get-state owner :picker) (:value after) true))))

    om/IRenderState
    (render-state [_ {:keys [picker]}]
      (let [handle-blur (fn [e]
                          (let [text-value (.. e -target -value)
                                valid? (.isValid (js/moment text-value display-format))]

                            (when (and value (empty? text-value))
                              (on-change (assoc date-state :value nil)))

                            (when (and (not valid?) (not-empty text-value))
                              (.setDate picker value))

                            (on-blur)))]
        (html
          [:div
           [:div.form-inline
            [:div.input-group {:key "ig"}
             [:input.form-control
              {:ref     "datepicker"
               :key "ifc"
               :disabled disabled
               ; This is a workaround for the case that another field losing focus changes
               ; the page layout (shows/hides error tex) causing the popup menu to appear out of position.
               :on-focus #(js/setTimeout (fn [] (.adjustPosition picker value)) 10)
               :on-blur handle-blur}]
             [:span.input-group-btn
              [:button.btn {:on-click #(.show picker)
                            :disabled disabled
                            :tab-index -1}
               [:span.glyphicon.glyphicon-calendar]]]]]])))))

(defn DateWidget
  [{:keys [display-format
           value-format
           is-hidden
           show-errors
           errors
           label
           required
           disabled
           value on-change on-blur
           help]
    :or   {value-format "YYYY-MM-DD"}} owner]
  (reify
    om/IInitState
    (init-state [_]
      {:date-state (init-date-state
                     {:display-format display-format
                      :value          (.toDate (js/moment value value-format))})})
    om/IRenderState
    (render-state [_ {:keys [date-state]}]
      (html
        [:div.form-group {:class [(if (and show-errors (not (empty? errors)))
                                    "has-error")
                                  (if is-hidden "hidden" "")]
                          :style {:width "auto"}}
         (if label [:label label (if required " *")])
         [:div.form-inline
          (om/build PikadayInputWidget
                    {:disabled disabled
                     :date-state (assoc date-state
                                   :value (when (not-empty value)
                                            (.toDate (js/moment value value-format))))
                     :on-blur    on-blur
                     :on-change  (fn [date-state']
                                   (om/set-state! owner :date-state date-state')
                                   (let [new-inst (:value date-state')]
                                     (when-not (= (:value date-state) new-inst)
                                       (on-change (when new-inst (.format (js/moment new-inst) value-format))))))})]
         (if help [:p.help-block help])]))))