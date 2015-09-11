(ns metcalf.handlers
  (:require-macros [cljs.core.async.macros :refer [go alt! go-loop]])
  (:require [om-tick.form :as form]
            [ajax.core :refer [GET POST DELETE]]
            [cljs.core.async :as async :refer [put! <! alts! chan sub timeout dropping-buffer]]
            [om.core :as om :include-macros true]
            [om-tick.form :refer [is-valid? load-errors reset-form extract-data]]
            [condense.utils :refer [vec-remove]]
            [metcalf.logic :as logic :refer [extract-field-values]]
            [metcalf.globals :refer [observe-path app-state ref-path]]
            [tailrecursion.priority-map :refer [priority-map]]
            [metcalf.utils :refer [deep-merge]]
            [metcalf.content :refer [default-payload]]
            [metcalf.logic :as logic]
            [metcalf.content :refer [contact-groups]]
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

(defn archive!
  "Quick and dirty delete function"
  [owner]
  (if (js/confirm "Are you sure you want to archive this record?")
    (let [state @app-state
          transition_url (-> state :context :document :transition_url)
          success_url (-> state :context :urls :Dashboard)]
      (POST transition_url {:params #js {:transition "archive"}
                            :handler         (fn [{:keys [message document] :as data}]
                                               (aset js/location "href" success_url))
                            :error-handler   (fn [{:keys [status failure response status-text] :as data}]
                                               (js/alert "Unable to delete"))
                            :headers         {"X-CSRFToken" (.get (goog.net.Cookies. js/document) "csrftoken")}
                            :format          :json
                            :response-format :json
                            :keywords?       true}))))

(defn delete-contact! [owner group item e]
  (.stopPropagation e)
  (let [parties (-> group contact-groups :path ref-path)
        {:keys [selected-group selected-item]} (om/get-state owner)]
    (when (js/confirm "Are you sure you want to delete this person?")
      (when (and (= group selected-group) (<= item selected-item))
        (om/set-state!
          owner :selected-item (when (> (count (:value parties)) 1)
                                 (-> selected-item dec (max 0)))))
      (om/transact! parties #(update % :value vec-remove item)))))

(defn create-document-ch
  [{:keys [url] :as form}]
  (let [result-ch (chan)]
    (POST url
          {:params          (extract-data form)
           :format          :json
           :response-format :json
           :keywords?       true
           :handler         (fn [data]
                              (put! result-ch {:success true :data data}))
           :error-handler   (fn [data]
                              (put! result-ch {:success false :data data}))
           :headers         {"X-CSRFToken" (.get (goog.net.Cookies. js/document) "csrftoken")}})
    result-ch))

(defn dashboard-create-save [owner e]
  (let [form-ref (ref-path [:create_form])
        page-ref (ref-path [:page])]
    (if (is-valid? form-ref)
      (go (let [{:keys [success data]} (<! (create-document-ch (om/value form-ref)))]
            (if success
              (do
                (om/transact! form-ref reset-form)
                (om/update! form-ref :show-errors false)
                (om/update! page-ref :show-create-modal false)
                (aset js/location "href" (-> data :document :url)))
              (if (= (:status data) 400)
                (om/update! page-ref {:name   "Error"
                                      :text   (-> data :response :message)
                                      :code   (-> data :status)
                                      :detail (-> data :response)})
                (do
                  (om/update! form-ref load-errors (:response data))
                  (om/update! form-ref :show-errors true))))))
      (om/update! form-ref :show-errors true))
    nil))

(defn transite-doc [url transition event]
  (let [trans-name (first (clojure.string/split transition "_"))]
    (if (js/confirm (str "Are you sure you want to " trans-name " this record?"))
      (POST url {:handler         (fn [{{:keys [uuid] :as doc} :document}]
                                    (swap! app-state update-in [:context :documents]
                                           (fn [docs]
                                             (reduce #(if (= uuid (:uuid %2))
                                                       (if (= transition "delete_archived")
                                                         %1
                                                         (conj %1 doc))
                                                       (conj %1 %2))
                                                     [] docs))))
                 :error-handler   (fn [{:keys [status failure response status-text] :as data}]
                                    (js/alert (str "Unable to " trans-name)))
                 :headers         {"X-CSRFToken" (.get (goog.net.Cookies. js/document) "csrftoken")}
                 :params          #js {:transition transition}
                 :format          :json
                 :response-format :json
                 :keywords?       true})))
  (.preventDefault event))

(defn clone-doc [url event]
  (if (js/confirm (str "Are you sure you want to clone this record?"))
    (POST url {:handler         #(aset js/location "href" (get-in % [:document :url]))
               :error-handler   (fn [{:keys [status failure response status-text] :as data}]
                                  (js/alert (str "Unable to clone")))
               :headers         {"X-CSRFToken" (.get (goog.net.Cookies. js/document) "csrftoken")}
               :format          :json
               :response-format :json
               :keywords?       true}))
  (.preventDefault event))

(defn toggle-status-filter
  [page-ref status-filter status]
  (if (contains? status-filter status)
    (om/update! page-ref :status-filter (disj status-filter status))
    (om/update! page-ref :status-filter (conj status-filter status))))

(defn back! []
  (swap! app-state update :page #(into {} (:back %))))

(defn reset-form! []
  (swap! app-state update :form reset-form))

(defn attach-success! [new-attachment]
  (swap! app-state update-in [:attachments] conj new-attachment))

(defn field-blur! [cursor]
  (om/update! cursor :show-errors true))

(defn field-update! [owner field v]
  (om/update! field :value v)
  (put! (:pub-chan (om/get-shared owner)) {:topic (om/path field) :value v}))

(defn value-change! [owner field event]
  (field-update! owner field (-> event .-target .-value)))