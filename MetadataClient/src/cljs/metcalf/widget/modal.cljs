(ns metcalf.widget.modal
  (:require [om.core :as om :include-macros true]
            [sablono.core :refer-macros [html]]
            [goog.dom.classes :as gclasses]
            [goog.labs.userAgent.platform :as gplatform]))

(def ESCAPE-KEY-CODE 27)

(defn show-modal [owner]
  (gclasses/add js/document.body "modal-open")
  (.addEventListener js/window "keydown" (om/get-state owner :key-down-callback)))

(defn hide-modal [owner]
  (gclasses/remove js/document.body "modal-open")
  (.removeEventListener js/window "keydown" (om/get-state owner :key-down-callback)))

(defn ModalContent
  [{:keys [loading modal-header modal-body modal-footer hide-footer ok-copy
           on-dismiss on-cancel on-save]}]
  (om/component
    (html [:div.modal-content
           [:div.modal-header
            [:button.close {:disabled loading :on-click #(on-dismiss %)}
             [:span {:dangerouslySetInnerHTML {:__html "&times;"}}]]
            [:h4.modal-title modal-header]]
           [:div.modal-body (if (ifn? modal-body) (modal-body) modal-body)]
           (when-not hide-footer
             (if modal-footer
               [:div.modal-footer modal-footer]
               [:div.modal-footer
                (if loading [:span [:span.fa.fa-spinner.fa-spin] " "])
                (when on-cancel
                  [:button.btn.btn-default {:disabled loading
                                            :on-click #(on-cancel %)} "Cancel"])
                (when on-save
                  [:button.btn.btn-primary {:disabled loading
                                            :on-click #(on-save %)} (or ok-copy "OK")])]))])))

(defn Modal
  [{:keys [modal-header modal-body modal-footer dialog-class hide-footer
           on-save on-cancel on-dismiss ok-copy loading]
    :as   props}
   owner]
  (reify

    om/IInitState
    (init-state [_]
      {:key-down-callback
       (fn [e] (if (= ESCAPE-KEY-CODE (.-keyCode e))
                 (if-let [on-dismiss (:on-dismiss (om/get-props owner))]
                   (on-dismiss e))))})

    om/IDidMount
    (did-mount [_]
      (show-modal owner))

    om/IWillUnmount
    (will-unmount [_]
      (hide-modal owner))

    om/IRender
    (render [_]
      (html [:div.modal-open
             [:div.modal.in {:style {:display "block"}      ;:tabIndex -1
                             }
              [:div.modal-dialog {:class dialog-class}
               (om/build ModalContent
                         {:loading      loading
                          :modal-header modal-header
                          :modal-body   modal-body
                          :modal-footer modal-footer
                          :hide-footer  hide-footer
                          :ok-copy      ok-copy
                          :on-dismiss   on-dismiss
                          :on-cancel    on-cancel
                          :on-save      on-save})]]
             [:div.modal-backdrop.in {:style    (if (gplatform/isIos)
                                                  {:position "sticky" :top 0} ; NOTE: attempt to avoid keyboard bug
                                                  {:position "fixed"} ;GOTCHA: Large modals / scrolling is messy
                                                  )
                                      :disabled loading
                                      :on-click #(on-dismiss %)}]]))))

