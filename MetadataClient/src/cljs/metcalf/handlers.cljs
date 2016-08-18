(ns metcalf.handlers
  (:require [clojure.string :as str]
            [cljs.core.async :as async :refer [put!]]
            [ajax.core :refer [GET POST DELETE]]
            [om.core :as om]
            [metcalf.utils :refer [vec-remove js-lookup error warn log info debug]]
            [metcalf.globals :refer [ref-path app-db]]
            [metcalf.logic :as logic :refer [extract-field-values field-edit reset-field field-zipper]]
            [goog.net.XhrIo :as xhrio]
            [goog.object :as gobj])
  (:import [goog.net Cookies]))

(defn load-api-options [api-path]
  (let [{:keys [uri options]} (get-in @app-db api-path)]
    (when (nil? options)
      (xhrio/send uri (fn [e]
                        (let [json (.. e -target getResponseJson)
                              results (gobj/get json "results")]
                          (swap! app-db update-in api-path assoc :options results)))))))

(defn transact!
  "Act like om/transact! but actually swap against app-db.  Work around for app-db -> app-state behavior."
  ([cursor f]
   (transact! cursor [] f nil))
  ([cursor korks f]
   (transact! cursor korks f nil))
  ([cursor korks f tag]
   (let [path (om/path cursor)
         korks (cond
                 (nil? korks) []
                 (sequential? korks) korks
                 :else [korks])]
     (swap! app-db
            update-in path
            update-in korks
            f))))

(defn update!
  "Act like om/update! but actually swap against app-db.  Work around for app-db -> app-state behavior."
  ([cursor v]
   (transact! cursor [] (fn [_] v) nil))
  ([cursor korks v]
   (transact! cursor korks (fn [_] v) nil))
  ([cursor korks v tag]
   (transact! cursor korks (fn [_] v) tag)))

(defn close-modal []
  (swap! app-db update :alert pop))

(defn open-modal [props]
  (swap! app-db update :alert
         (fn [alerts]
           (when-not (= (peek alerts) props)
             (conj alerts props)))))

(defn del-value!
  "Helper to delete a value from a list field by index
   which assumes the standard :many field conventions"
  [many-field i]
  (transact! many-field :value #(vec-remove % i)))

(defn new-value-field
  [many-field]
  ; TODO: use field-postwalk to avoid inefficiencies with long options lists
  (let [fields (:fields (om/value many-field))]
    {:value (field-edit (field-zipper fields) reset-field)}))

(defn add-field!
  ([many-field]
   (let [new-field (new-value-field many-field)]
     (add-field! many-field new-field)))
  ([many-field field]
   (transact! many-field :value #(conj % (om/value field)))))

(defn add-value!
  "Helper to append a new value to a many-field.

  * Initialise new field map based on :fields
  * Load any data provided
  "
  [many-field value]
  (add-field! many-field (-> (new-value-field many-field)
                             (assoc :value (om/value value)))))

(defn delete-attachment
  [attachments-ref attachment]
  {:pre [(om/cursor? attachments-ref)
         (om/cursor? attachment)]}
  (del-value! attachments-ref (last (om/path attachment)))
  #_
  (let [delete_url (-> attachment :value :delete_url :value)]
    (DELETE delete_url
            {:handler         (fn [data]
                                (del-value! attachments-ref (last (om/path attachment))))
             :error-handler   (fn [data]
                                (open-modal {:type :alert :message "Unable to delete file"}))
             :headers         {"X-CSRFToken" (.get (Cookies. js/document) "csrftoken")}
             :format          :json
             :response-format :json
             :keywords?       true})))

(defn create-document
  [url form result-ch]
  (POST url
        {:params          (logic/extract-data form)
         :format          :json
         :response-format :json
         :keywords?       true
         :handler         (fn [data]
                            (put! result-ch {:success true :data data}))
         :error-handler   (fn [data]
                            (put! result-ch {:success false :data data}))
         :headers         {"X-CSRFToken" (.get (Cookies. js/document) "csrftoken")}}))

(defn clone-document
  "docstring"
  [url]
  (POST url {:handler         #(aset js/location "href" (get-in % [:document :url]))
             :error-handler   (fn [{:keys [status failure response status-text] :as data}]
                                (open-modal {:type :alert :message "Unable to clone"}))
             :headers         {"X-CSRFToken" (.get (Cookies. js/document) "csrftoken")}
             :format          :json
             :response-format :json
             :keywords?       true}))

(defn transition-current-document
  [url transition on-error]
  (POST url {:handler         (fn [{{:keys [uuid] :as doc} :document}]
                                (swap! app-db update-in [:context :documents]
                                       (fn [docs]
                                         (reduce #(if (= uuid (:uuid %2))
                                                   (if (= transition "delete_archived")
                                                     %1
                                                     (conj %1 doc))
                                                   (conj %1 %2))
                                                 [] docs))))
             :error-handler   on-error
             :headers         {"X-CSRFToken" (.get (Cookies. js/document) "csrftoken")}
             :params          #js {:transition transition}
             :format          :json
             :response-format :json
             :keywords?       true}))

(defn submit-current-document
  [transition_url on-success on-error]
  (POST transition_url
        {:params          #js {:transition "submit"}
         :handler         (fn [{:keys [document] :as data}]
                            (swap! app-db assoc-in [:context :document] document)
                            (on-success))
         :error-handler   (fn [data]
                            (on-error data))
         :headers         {"X-CSRFToken" (.get (Cookies. js/document) "csrftoken")}
         :format          :json
         :response-format :json
         :keywords?       true}))

(defn save-current-document
  [done-ch callback]
  (let [state @app-db
        data (-> state :form :fields extract-field-values)]
    (POST (get-in state [:form :url])
          {:params          (clj->js data)
           :format          :json
           :response-format :json
           :keywords?       true
           :handler         (fn [resp]
                              (swap! app-db
                                     #(-> %
                                          (assoc-in [:form :data] data)
                                          (update-in
                                            [:context :document] merge
                                            (get-in resp [:form :document]))))
                              (put! done-ch true)
                              (when callback (callback)))
           :error-handler   (fn [{:keys [status failure response status-text]}]
                              (put! done-ch true))

           :headers         {"X-CSRFToken" (.get (Cookies. js/document) "csrftoken")}})))

(defn add-attachment
  [attachment-data]
  (let [data (select-keys attachment-data [:file :name :delete_url])
        attachments-ref (ref-path [:form :fields :attachments])
        template (om/value (:fields attachments-ref))
        new-value (reduce (fn [form-acc [k v]]
                            (assoc-in form-acc [k :value] v))
                          template data)]
    (swap! app-db update-in [:form :fields :attachments :value]
           conj {:value new-value})))

(defn archive-current-document
  "Quick and dirty delete function"
  []
  (let [state @app-db
        transition_url (-> state :context :document :transition_url)
        success_url (-> state :context :urls :Dashboard)]
    (POST transition_url {:params          #js {:transition "archive"}
                          :handler         (fn [{:keys [message document] :as data}]
                                             (aset js/location "href" success_url))
                          :error-handler   (fn [{:keys [status failure response status-text] :as data}]
                                             (open-modal {:type :alert :message "Unable to delete"}))
                          :headers         {"X-CSRFToken" (.get (Cookies. js/document) "csrftoken")}
                          :format          :json
                          :response-format :json
                          :keywords?       true})))

(defn back [x]
  (swap! app-db assoc :page (into {} x)))

(defn ror
  "Reverse OR: use it to update source value only if destination value is not falsey."
  [a b]
  (or b a))

(defn update-address [contact {:strs [city organisationName deliveryPoint deliveryPoint2
                                      postalCode country administrativeArea]
                               :or   {city               ""
                                      organisationName   ""
                                      deliveryPoint      ""
                                      deliveryPoint2     ""
                                      postalCode         ""
                                      country            ""
                                      administrativeArea ""}
                               :as   values}]
  (swap! app-db
         update-in (om/path contact)
         #(-> %
              (assoc-in [:value :organisationName :value] organisationName)
              (update-in [:value :address :deliveryPoint :value] ror deliveryPoint)
              (update-in [:value :address :deliveryPoint2 :value] ror deliveryPoint2)
              (update-in [:value :address :city :value] ror city)
              (update-in [:value :address :administrativeArea :value] ror administrativeArea)
              (update-in [:value :address :postalCode :value] ror postalCode)
              (update-in [:value :address :country :value] ror country))))

(defn update-dp-term
  [dp-term-path option]
  ; TODO: need to make option data consistent
  (let [option (if (map? option) option (js-lookup option))
        {:keys [term vocabularyTermURL vocabularyVersion termDefinition]} option]
    (swap! app-db (fn [db] (-> db
                               (update-in dp-term-path assoc-in [:term :value] term)
                               (update-in dp-term-path assoc-in [:vocabularyTermURL :value] vocabularyTermURL)
                               (update-in dp-term-path assoc-in [:vocabularyVersion :value] vocabularyVersion)
                               (update-in dp-term-path assoc-in [:termDefinition :value] termDefinition))))))

(defn setter [cursor k v]
  (swap! app-db update-in (om/path cursor) assoc k v))

(defn unsaved-input-check-helper [{:keys [new-value errors] :as keywords-data}]
  (assoc keywords-data
    :errors
    (if (str/blank? new-value)
      (disj (set errors) "Unsaved value in the keyword input field")
      (conj (set errors) "Unsaved value in the keyword input field"))))

(defn check-unsaved-keyword-input [keywords-path]
  (swap! app-db update-in keywords-path unsaved-input-check-helper))

(defn remove-party [parties item]
  (let [parties-path (om/path parties)]
    (swap! app-db update-in parties-path update :value vec-remove item)))

(defn reset-form
  ([] (swap! app-db update :form reset-form))
  ([form-ref]
   (transact! form-ref logic/reset-form)))

(defn show-errors
  [field-cursor]
  (update! field-cursor :show-errors true))

(defn hide-errors
  [form]
  (update! form :show-errors false))

(defn show-create-modal []
  (swap! app-db assoc-in [:page :show-create-modal] true))

(defn hide-create-modal []
  (swap! app-db assoc-in [:page :show-create-modal] false))

(defn toggle-status-filter
  [page-ref status-filter status]
  (if (contains? status-filter status)
    (update! page-ref :status-filter (disj status-filter status))
    (update! page-ref :status-filter (conj status-filter status))))

(defn show-all-documents
  [page status-freq]
  (update! page :status-filter (set (keys status-freq))))

(defn load-error-page [data]
  (update! (ref-path [:page])
           {:name   "Error"
            :text   (-> data :response :message)
            :code   (-> data :status)
            :detail (-> data :response)}))

(defn set-value
  [field-cursor value]
  (swap! app-db update-in (om/path field-cursor) assoc :value value))

(defn set-geographic-element
  [many-field values]
  (let [new-fields (for [value values]
                     (-> (new-value-field many-field)
                         (update-in [:value :northBoundLatitude] merge (:northBoundLatitude value))
                         (update-in [:value :southBoundLatitude] merge (:southBoundLatitude value))
                         (update-in [:value :eastBoundLongitude] merge (:eastBoundLongitude value))
                         (update-in [:value :westBoundLongitude] merge (:westBoundLongitude value))))]
    (update! many-field :value (vec new-fields))))

(defn field-update [field v]
  (set-value field v))

(defn value-changed [field value]
  (swap! app-db update-in (om/path field)
         assoc :value value :show-errors true))

(defn checkbox-change [field event]
  (field-update field (-> event .-target .-checked)))

(defn set-tab
  [page id]
  (update! page [:tab] id))

(defn load-errors
  [form-ref data]
  (update! form-ref logic/load-errors data))

(defn add-keyword-extra [keywords value]
  (when-not (empty? value)
    (swap! app-db assoc-in (om/path keywords) (vec (conj keywords {:value value})))))

(defn del-keyword-extra [keywords value]
  (swap! app-db assoc-in (om/path keywords) (vec (remove #(= value (:value %)) keywords))))
