(ns metcalf.views.page
  (:require [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [sablono.core :refer-macros [html]]
            [metcalf.globals :refer [observe-path app-state]]
            metcalf.views.form
            goog.dom))

(defmulti PageView (fn [page owner] (get page :name)) :default "404")

(defmulti PageTabView (fn [page owner] [(get page :name)
                                        (get page :tab :data-identification)]))

(defn BackButton [props owner]
  (reify
    om/IDisplayName (display-name [_] "BackButton")
    om/IRender
    (render [_]
      (let [page (observe-path owner [:page])
            back (:back page)]
        (html (if back [:button.btn.btn-default.BackButton
                        {:on-click #(swap! app-state assoc :page (into {} back))}
                        [:span.glyphicon.glyphicon-chevron-left] " Back"]))))))

