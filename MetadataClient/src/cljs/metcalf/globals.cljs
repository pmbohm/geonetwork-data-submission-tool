(ns metcalf.globals
  (:require [om.core :as om :include-macros true]
            [metcalf.logic :refer [derived-state]]))

(defonce app-db (atom {}))
(defonce derived-db (atom {}))

(add-watch
  app-db :derive-db
  (fn [k r o n]
    (when-not (= o n)
      (reset! derived-db (derived-state n)))))

(defn ref-path
  "Return a ref cursor at a specified path"
  [path]
  (let [rc (om/root-cursor derived-db)]
    (assert (get-in rc path) (str "No value found in app-state at: " path))
    (-> rc (get-in path) om/ref-cursor)))

(defn observe-path
  "Observes and returns a reference cursor at path and it's value including any derived state."
  ([owner]
   (om/observe owner (om/ref-cursor (om/root-cursor derived-db))))
  ([owner path]
   {:pre [(om/component? owner)]}
   (om/observe owner (ref-path path))))