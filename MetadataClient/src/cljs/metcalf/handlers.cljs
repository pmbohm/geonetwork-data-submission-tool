(ns metcalf.handlers
  (:require [om-tick.form :as form]
            [tailrecursion.priority-map :refer [priority-map]]
            [metcalf.utils :refer [deep-merge]]
            [metcalf.content :refer [default-payload]]
            [metcalf.logic :as logic]))

(defn init-theme-options [{:keys [table] :as theme}]
  (assoc theme :options (into (priority-map) (map logic/theme-option table))))

(defn initialise-form
  ([{:keys [data] :as form}]
   (initialise-form form data))
  ([form data]
   (-> (form/reset-form form)
       (assoc :data data)
       (update :fields logic/reduce-many-field-templates data)
       (update :fields logic/reduce-field-values data))))

(defn initial-state
  "Massage raw payload for use as app-state"
  [payload]
  (-> (deep-merge default-payload payload)
      (update :form initialise-form)
      (update :theme init-theme-options)))