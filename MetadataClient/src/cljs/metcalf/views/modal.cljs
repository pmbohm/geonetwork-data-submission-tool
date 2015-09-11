(ns metcalf.views.modal
  (:require [cljs.core.async :as async :refer [<! timeout]]
            [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [sablono.core :refer-macros [html]]
            [metcalf.globals :refer [observe-path app-state]]
            goog.dom.classes
            goog.labs.userAgent.platform))

(def ESCAPE-KEY-CODE 27)

(defn Modal [props owner]
  (reify

    om/IDidMount
    (did-mount [_]
      (goog.dom.classes.add js/document.body "modal-open")
      (let [key-down-callback (fn [e] (if (= ESCAPE-KEY-CODE (.-keyCode e))
                                        (if-let [on-dismiss (:on-dismiss (om/get-props owner))]
                                          (on-dismiss e))))]
        (.addEventListener js/window "keydown" key-down-callback)
        (om/set-state! owner :key-down-callback key-down-callback)))

    om/IWillUnmount
    (will-unmount [_]
      (goog.dom.classes.remove js/document.body "modal-open")
      (.removeEventListener js/window "keydown" (om/get-state owner :key-down-callback)))

    om/IRender
    (render [_]
      (let [{:keys [modal-header modal-body dialog-class hide-footer
                    on-save on-cancel on-dismiss ok-copy loading]
             :or   {on-save identity on-cancel identity on-dismiss identity}} props]
        (html [:div.modal-open
               [:div.modal.in {:style {:display "block"}    ;:tabIndex -1
                               }
                [:div.modal-dialog {:class dialog-class}
                 [:div.modal-content
                  [:div.modal-header
                   [:button.close {:disabled loading :on-click #(on-dismiss %)}
                    [:span {:dangerouslySetInnerHTML {:__html "&times;"}}]]
                   [:h4.modal-title modal-header]]
                  [:div.modal-body modal-body]
                  (if-not hide-footer
                    [:div.modal-footer
                     (if loading [:span [:span.fa.fa-spinner.fa-spin] " "])
                     [:button.btn.btn-default {:disabled loading
                                               :on-click #(on-cancel %)} "Cancel"]
                     [:button.btn.btn-primary {:disabled loading
                                               :on-click #(on-save %)} (or ok-copy "OK")]])]]]
               [:div.modal-backdrop.in {:style    (if (goog.labs.userAgent.platform/isIos)
                                                    {:position "sticky" :top 0} ; NOTE: attempt to avoid keyboard bug
                                                    {:position "fixed"} ;GOTCHA: Large modals / scrolling is messy
                                                    )
                                        :disabled loading
                                        :on-click #(on-dismiss %)}]])))))