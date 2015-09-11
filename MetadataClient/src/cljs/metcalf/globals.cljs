(ns metcalf.globals
  (:require [cljs.core.async :as async :refer [chan pub]]
            [om.core :as om :include-macros true]
            [condense.derived :refer [derived-atom!]]
            [metcalf.logic :refer [derived-state]]
            [condense.utils :refer [memoize-last]]))

(defonce app-state (derived-atom! (atom {}) (memoize-last derived-state)))
(def pub-chan (chan))
(def notif-chan (pub pub-chan :topic))

(defn ref-path
  "Return a ref cursor at a specified path"
  [path]
  (let [rc (om/root-cursor app-state)]
    (assert (get-in rc path) (str "No value found in app-state at: " path))
    (-> rc (get-in path) om/ref-cursor)))

(defn observe-path
  "Observes and returns a reference cursor at path and it's value including any derived state."
  [owner path]
  {:pre [(om/component? owner)]}
  (om/observe owner (ref-path path)))

(defn ^:export app-state-js []
  (clj->js @app-state))