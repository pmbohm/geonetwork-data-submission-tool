(ns metcalf.views.highlight
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [cljs.core.async :as async :refer [<! timeout]]
            [om.core :as om :include-macros true]
            [sablono.core :refer-macros [html]]))

(defn handle-highlight-new [owner item]
  (om/set-state! owner :highlight (conj (om/get-state owner :highlight) item))
  (go (<! (timeout 5000))
      (om/set-state! owner :highlight (disj (om/get-state owner :highlight) item))))
