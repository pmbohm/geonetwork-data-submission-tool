(ns metcalf.core
  (:require [om.core :as om]
            [clojure.string :refer [blank?]]
            [metcalf.globals :refer [app-db derived-db]]
            [metcalf.routing :as router]
            [metcalf.logic :refer [initial-state]]
            [metcalf.views :refer [AppRoot]]
            [metcalf.watch-state :refer [enable-state-change-reporting]]
            [devtools.core :as devtools]
            [metcalf.handlers :as handlers]))

(if ^boolean js/goog.DEBUG (devtools/install!))

(if-not ^boolean js/goog.DEBUG
  (set! (.-onbeforeunload js/window)
        (fn []
          (if (get-in @derived-db [:form :dirty])
            "This will navigate away from the Data Submission Tool and all unsaved work will be lost. Are you sure you want to do this?"))))

(defn init-db []
  (reset! app-db (initial-state (js->clj (aget js/window "payload") :keywordize-keys true)))
  (doseq [api-key (keys (get @app-db :api))]
    (handlers/load-api-options [:api api-key])))

(defn main []
  (when-let [ele (.getElementById js/document "Content")]
    (when ^boolean js/goog.DEBUG
      (enable-state-change-reporting derived-db))
    (when (-> @app-db :page :name nil?)
      (init-db)
      (router/start! {:iref   app-db
                      :path   [:page :tab]
                      :->hash (fnil name "")
                      :<-hash #(if (blank? %) :data-identification (keyword %))}))
    (om/root AppRoot derived-db {:target ele})))
