(ns metcalf.handlers
  (:require-macros [cljs.core.async.macros :refer [go alt! go-loop]])
  (:require [om-tick.form :as form]
            [ajax.core :refer [GET POST DELETE]]
            [cljs.core.async :as async :refer [put! <! alts! chan sub timeout dropping-buffer]]
            [om.core :as om :include-macros true]
            [metcalf.logic :as logic :refer [extract-field-values]]
            [metcalf.globals :refer [observe-path app-state]]
            [tailrecursion.priority-map :refer [priority-map]]
            [metcalf.utils :refer [deep-merge]]
            [metcalf.content :refer [default-payload]]
            [metcalf.logic :as logic]
            goog.net.Cookies))

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

(defn save!
  "Quick and dirty save function"
  [owner & [callback]]
  (om/set-state! owner :saving true)
  (let [state @app-state
        done (chan)
        wait (async/map vector [(timeout 500) done])
        data (-> state :form :fields extract-field-values)]
    (go (<! wait) (om/set-state! owner :saving false))
    (POST (get-in state [:form :url])
          {:params          (clj->js data)
           :format          :json
           :response-format :json
           :keywords?       true
           :handler         (fn [resp]
                              (swap! app-state
                                     #(-> %
                                          (assoc-in [:form :data] data)
                                          (update-in
                                            [:context :document] merge
                                            (get-in resp [:form :document]))))
                              (put! done true)
                              (when callback (callback)))
           :error-handler   (fn [{:keys [status failure response status-text]}]
                              (put! done true))

           :headers         {"X-CSRFToken" (.get (goog.net.Cookies. js/document) "csrftoken")}})))

(defn submit!
  "Submit a doc"
  [owner event {:keys [transition_url] :as doc}]
  (.preventDefault event)
  (save! owner
         (fn []
           (om/set-state! owner :saving true)
           (POST transition_url
                 {:params          #js {:transition "submit"}
                  :handler         (fn [{:keys [document] :as data}]
                                     (swap! app-state assoc-in [:context :document] document)
                                     (om/set-state! owner :saving false))
                  :error-handler   (fn [{:keys [status failure response status-text] :as data}]
                                     (om/set-state! owner :saving false)
                                     (js/alert (str "Unable to submit: " status " " failure)))
                  :headers         {"X-CSRFToken" (.get (goog.net.Cookies. js/document) "csrftoken")}
                  :format          :json
                  :response-format :json
                  :keywords?       true}))))