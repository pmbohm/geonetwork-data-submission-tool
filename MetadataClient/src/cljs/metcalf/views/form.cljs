(ns metcalf.views.form
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [cljs.core.async :as async :refer [<! timeout]]
            [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [sablono.core :refer-macros [html]]
            [metcalf.globals :refer [observe-path ref-path]]
            [om-tick.field :refer [field-zipper field-edit reset-field field?]]
            [om-tick.bootstrap :refer [Select validation-state]]
            [condense.utils :refer [fmap]]
            [condense.fields :refer [Input Checkbox ExpandingTextarea validate-required-field
                                     help-block-template label-template
                                     del-value! add-value! add-field!]]
            [metcalf.views.widget :refer [InputField DecimalField DateField SelectField AutoCompleteField
                                          TextareaField TextareaFieldProps CheckboxField
                                          handle-value-change field-update! handle-checkbox-change]]
            [metcalf.views.highlight :refer [handle-highlight-new]]
            [metcalf.views.modal :refer [Modal]]
            [metcalf.handlers :as handlers]
            [metcalf.utils :refer [reverse-or]]
            [clojure.string :as string]
            [clojure.zip :as zip]))

(defn TableInlineEdit [{:keys [ths tds-fn form field-path
                               placeholder default-field]
                        :or   {tds-fn #(list (:value %))}} owner]
  (reify
    om/IDisplayName (display-name [_] "TableInlineEdit")
    om/IInitState (init-state [_] {:cursor nil
                                   :highlight #{}})
    om/IRenderState
    (render-state [_ {:keys [cursor highlight]}]
      (let [{:keys [disabled] :as many-field} (observe-path owner field-path)
            col-span (if ths (count ths) 1)
            edit! (fn [field-path]
                    (om/set-state! owner :cursor field-path))
            delete! (fn [field]
                      (om/set-state! owner :cursor nil)
                      (del-value! many-field (last (om/path field))))
            new! (fn [default-field]
                   (let [values-ref (:value many-field)
                         values-len (count values-ref)]
                     (if default-field
                       (add-field! many-field default-field)
                       (add-field! many-field))
                     (let [new-cursor (conj (om/path values-ref) values-len)]
                       (om/set-state! owner :cursor new-cursor)
                       (handle-highlight-new owner new-cursor))))]
        (html [:div.TableInlineEdit
               (help-block-template many-field)
               (if (or (not placeholder) (-> many-field :value count pos?))
                 [:table.table {:class (when-not disabled "table-hover")}
                  (if ths [:thead [:tr (for [th ths]
                                         [:th th])
                                   [:th.xcell " "]]])
                  [:tbody
                   (for [field (:value many-field)]
                     (let [field-path (om/path field)
                           highlight-class (if (highlight field-path) "highlight")]
                       (if (= cursor field-path)

                         [:tr.active {:ref "edit"}
                          [:td {:class highlight-class
                                :col-span col-span}
                           (om/build form cursor)
                           [:button.btn.btn-primary {:on-click #(edit! nil)} "Done"] " "
                           [:a.text-danger.pull-right {:on-click #(delete! field)}
                            [:span.glyphicon.glyphicon-remove] " Delete"]]
                          [:td.xcell {:class highlight-class}
                           [:span.clickable-text
                            {:on-click #(edit! nil)}
                            [:span.glyphicon.glyphicon-remove]]]]


                         [:tr.noselect {:ref      field-path
                                        :on-click (when-not disabled
                                                    #(edit! field-path))
                                        :class    (if (= field-path cursor) "info")}
                          (for [td-value (tds-fn field)]
                            [:td td-value])
                          [:td.xcell
                           (when-not disabled
                             [:span.glyphicon.glyphicon-edit.hover-only])]])))]]
                 [:div {:style {:margin-bottom "1em"}} placeholder])
               (when-not disabled
                 [:button.btn.btn-primary
                  {:on-click #(new! default-field)}
                  [:span.glyphicon.glyphicon-plus] " Add new"])])))))

(defn DataParameterRowEdit [path owner]
  (reify
    om/IDisplayName (display-name [_] "DataParameterDetail")
    om/IRender
    (render [_]
      (let [props (observe-path owner path)
            {:keys [name longName parameterDescription unit]} (:value props)]
        (html [:div.DataParameterMaster
               [:h3 "Edit parameter"]
               (om/build InputField {:path (om/path longName)})
               [:div.row
                [:div.col-sm-6
                 (om/build InputField {:path (om/path name)})]
                [:div.col-sm-6
                 (om/build InputField {:path (om/path unit)})]]
               [:label "Additional parameter info"]
               (om/build TextareaFieldProps
                         {:path (om/path parameterDescription)})])))))

(defn DataParametersTable [path owner]
  (reify
    om/IDisplayName (display-name [_] "DataParameters")
    om/IRender
    (render [_]
      (html [:div.DataParametersTable
             (om/build TableInlineEdit {:ths        ["Name" "Long name" "Unit of measurement" "Description"]
                                        :tds-fn     (fn [field]
                                                      (let [{:keys [parameterDescription unit name longName]}
                                                            (fmap (comp #(or % "--") :value) (:value field))]
                                                        [name longName unit parameterDescription]))
                                        :form       DataParameterRowEdit
                                        :field-path [:form :fields :identificationInfo :dataParameters]})]))))

(defn Lodge [_ owner]
  (reify
    om/IDisplayName (display-name [_] "Lodge")
    om/IRenderState
    (render-state [_ {:keys [saving]}]
      (let [{:keys [document urls site]} (observe-path owner [:context])
            {:keys [portal_title portal_url]} site
            {:keys [errors]} (observe-path owner [:progress])
            {:keys [disabled]} (observe-path owner [:form])
            is-are (if (> errors 1) "are" "is")
            plural (if (> errors 1) "s")
            has-errors? (and errors (> errors 0))
            submitted? (= (:status document) "Submitted")]
        (html [:div.Lodge
               [:p "Are you finished? Use this page to lodge your completed metadata record."]
               [:p "The Data Manager will be notified of your submission and will be in contact
               if any further information is required. Once approved, your data will be archived
               for discovery in the "
                (if portal_url
                  [:a {:href portal_url :target "_blank"} [:span.portal_title portal_title]]
                  [:span.portal_title portal_title])
                "."]
               [:p "How complete is your data?"]

               [:p

                [:button.btn.btn-primary.btn-lg
                 {:disabled (or has-errors? saving disabled submitted?)
                  :on-click #(handlers/submit! owner % document)}
                 (when saving
                   (list
                     [:img
                      {:src (str (:STATIC_URL urls)
                                 "metcalf/resources/public/img/saving.gif")}]
                     " "))
                 "Lodge data"]
                " "

                (if has-errors?
                  [:span.text-danger [:b "Unable to lodge: "]
                   "There " is-are " " [:span errors " error" plural
                                        " which must be corrected first."]]
                  [:span.text-success
                   [:b
                    (cond
                      saving "Submitting..."
                      (= (:status document) "Draft") "Ready to lodge"
                      (= (:status document) "Submitted") "Your record has been submitted."
                      :else (:status document))]])]])))))

(defn AddressField [address owner]
  (reify
    om/IDisplayName (display-name [_] "AddressField")
    om/IRender
    (render [_]
      (let [{:keys [city postalCode administrativeArea country deliveryPoint deliveryPoint2]} address]
        (html [:div.AddressField
               (om/build Input (assoc deliveryPoint
                                 :on-blur #(om/update! deliveryPoint :show-errors true)
                                 :on-change #(handle-value-change owner deliveryPoint %)))
               (om/build Input (assoc deliveryPoint2
                                 :on-blur #(om/update! deliveryPoint2 :show-errors true)
                                 :on-change #(handle-value-change owner deliveryPoint2 %)))
               [:div.row
                [:div.col-xs-6
                 (om/build Input (assoc city
                                   :help "City"
                                   :on-change #(handle-value-change owner city %)))]
                [:div.col-xs-6
                 (om/build Input (assoc administrativeArea
                                   :help "State/territory"
                                   :on-change #(handle-value-change owner administrativeArea %)))]]
               [:div.row
                [:div.col-xs-6
                 (om/build Input (assoc postalCode
                                   :help "Postal / Zip code"
                                   :on-change #(handle-value-change owner postalCode %)))]
                [:div.col-xs-6
                 (om/build Input (assoc country
                                   :help "Country"
                                   :on-change #(handle-value-change owner country %)))]]])))))

(defn update-address! [contact {:keys [city organisationName deliveryPoint deliveryPoint2
                                       postalCode country administrativeArea]}]
  (om/transact! contact
                #(-> %
                     (assoc-in [:value :organisationName :value] organisationName)
                     (update-in [:value :address :deliveryPoint :value] reverse-or deliveryPoint)
                     (update-in [:value :address :deliveryPoint2 :value] reverse-or deliveryPoint2)
                     (update-in [:value :address :city :value] reverse-or city)
                     (update-in [:value :address :administrativeArea :value] reverse-or administrativeArea)
                     (update-in [:value :address :postalCode :value] reverse-or postalCode)
                     (update-in [:value :address :country :value] reverse-or country))))

(defn re-escape
  [s]
  (string/replace s #"[\-\[\]\/\{\}\(\)\*\+\?\.\\\^\$\|]" #(str "\\" %)))

(defn OrganisationInputField
  "Input field for organisation which offers autocompletion of known
  institutions.  On autocomplete address details are updated."
  [party-path owner]
  (reify
    om/IDisplayName (display-name [_] "OrganisationInputField")
    om/IRender
    (render [_]
      (let [party-field (observe-path owner party-path)
            organisation-name (-> party-field :value :organisationName :value)
            disabled (get-in party-field [:value :organisationName :disabled])
            institutions (observe-path owner [:institutions])]
        (html [:div.OrganisationInputField
               (om/build select-om-all.core/AutoComplete
                         {:placeholder  "Organisation"
                          :default      (first (filter #(= organisation-name (:organisationName %)) institutions))
                          :editable?    true
                          :disabled?    disabled
                          :datasource   institutions
                          :get-cols     (comp vector :organisationName)
                          :display-fn   :organisationName
                          :index-fn     :organisationName
                          :undisplay-fn (fn [s] {:organisationName s})
                          :on-change    (fn [institution]
                                          (update-address! party-field institution))})])))))

(defn ResponsiblePartyField [path owner]
  (reify
    om/IDisplayName (display-name [_] "ResponsiblePartyField")
    om/IRender
    (render [_]
      (let [party (observe-path owner path)
            {:keys [individualName phone facsimile orcid
                    electronicMailAddress address role organisationName]} (:value party)]
        (html [:div.ResponsiblePartyField

               [:h4 (:value individualName)]
               (om/build Input (assoc individualName
                                 :on-change #(handle-value-change owner individualName %)))

               (om/build Input (assoc orcid
                                 :on-change #(handle-value-change owner orcid %)))

               (om/build Select (assoc role
                                  :on-change #(handle-value-change owner role %)))

               [:label "Organisation" (when (:required organisationName) " *")]
               (om/build OrganisationInputField path)

               [:label "Postal address"]
               (om/build AddressField address)

               [:div.ContactDetails

                (om/build Input (assoc phone
                                  :on-change #(handle-value-change owner phone %)))

                (om/build Input (assoc facsimile
                                  :on-change #(handle-value-change owner facsimile %)))

                (om/build Input (assoc electronicMailAddress
                                  :on-change #(handle-value-change owner electronicMailAddress %)))]])))))

(defn FieldError [{:keys [errors label]} owner]
  (reify
    om/IDisplayName (display-name [_] "FieldError")
    om/IInitState (init-state [_] {})
    om/IRender
    (render [_]
      (html [:span.FieldError label ": " (first errors)]))))

(defn page-fields
  [state page]
  (let [zipper (field-zipper state)]
    (loop [loc zipper
           acc []]
      (if (zip/end? loc)
        acc
        (let [node (zip/node loc)]
          (recur (zip/next loc) (if (and (field? node) (= page (:page node)))
                                  (conj acc node)
                                  acc)))))))

(defn PageErrors [{:keys [page path]} owner]
  (reify
    om/IDisplayName (display-name [_] "PageErrors")
    om/IRender
    (render [_]
      (let [{:keys [show-errors] :as form} (observe-path owner path)
            fields (->> (page-fields form page)
                        (remove #(empty? (:errors %))))]
        (if (and show-errors (not (empty? fields)))
          (html [:div.alert.alert-info.alert-dismissable
                 [:button {:type "button" :class "close"
                           :on-click #(om/update! form :show-errors false)} "×"]
                 (if (> (count fields) 1)
                   [:div
                    [:b "There are multiple fields on this page that require your attention:"]
                    [:ul (for [field fields]
                           [:li (om/build FieldError field)])]]
                   (om/build FieldError (first fields)))]))))))

(defn CreditField [path owner]
  (reify
    om/IDisplayName (display-name [_] "CreditField")
    om/IRender
    (render [_]
      (html [:div.CreditField (om/build TextareaField path)]))))

(defn ResourceConstraints [props owner]
  (reify
    om/IDisplayName (display-name [_] "ResourceConstraints")
    om/IRender
    (render [_]
      (html [:div.ResourceConstraints
             (om/build SelectField [:form :fields :identificationInfo :creativeCommons])
             [:p.help-block "Learn more about which license is right for you at "
              [:a {:href "http://creativecommons.org.au/learn/licenses/"
                   :target "_blank"}
               "Creative Commons Australia"]]
             ;(om/build Checkbox {:label   "Other constraints" :checked true})
             (om/build TextareaField [:form :fields :identificationInfo :useLimitation])]))))

(defn SupplementalFieldEdit [path owner]
  (reify
    om/IDisplayName (display-name [_] "SupplementalFieldEdit")
    om/IRender
    (render [_]
      (om/build TextareaFieldProps {:path path
                                    :rows 3}))))

(defn SupplementalInformation [path owner]
  (reify
    om/IDisplayName (display-name [_] "SupplementalInformation")
    om/IRender
    (render [_]
      (let [list-field (observe-path owner path)]
        (html [:div.SupplementalInformation
               (label-template list-field)
               (om/build TableInlineEdit {:form       SupplementalFieldEdit
                                          :field-path path})])))))

(defn FormErrors [{:keys [path] :as props} owner]
  (reify
    om/IDisplayName (display-name [_] "FormErrors")
    om/IRender
    (render [_]
      (let [{:keys [fields show-errors] :as form} (observe-path owner path)
            fields-with-errors (filter (comp :errors second) fields)]
        (html (if (and show-errors (seq fields-with-errors))
                [:div.alert.alert-danger
                 [:p [:b "The following fields need your attention"]]
                 [:ul (for [[k {:keys [label errors]}] fields-with-errors]
                        [:li
                         (or label (name k)) ": "
                         (string/join ". " errors)])]]))))))

(defn NewDocumentForm [props owner]
  (reify
    om/IDisplayName (display-name [_] "NewDocumentForm")
    om/IRender
    (render [_]
      (html [:div/NewDocumentForm
             (om/build FormErrors {:path [:create_form]})
             (om/build InputField {:path [:create_form :fields :title]})
             (om/build SelectField [:create_form :fields :template])]))))

(defn DashboardCreateModal [props owner]
  (reify
    om/IDisplayName (display-name [_] "DashboardCreateModal")
    om/IRender
    (render [_]
      (let [page (observe-path owner [:page])
            hide-modal! #(om/update! page :show-create-modal false)]
        (html [:div.DashboardCreateModal
               (om/build Modal {:ok-copy      "OK"
                                :modal-header (html [:span [:span.glyphicon.glyphicon-list] " " "Create a new record"])
                                :modal-body   (om/build NewDocumentForm nil)
                                :on-dismiss   #(hide-modal!)
                                :on-save      #(handlers/dashboard-create-save owner %)
                                :on-cancel    #(hide-modal!)})])))))

(defn NewDocumentButton [props owner]
  (reify
    om/IDisplayName (display-name [_] "NewDocumentForm")
    om/IInitState (init-state [_] {:title ""})
    om/IRenderState
    (render-state [_ {:keys [title ch]}]
      (html [:button.btn.btn-primary {:on-click #(om/update! (ref-path [:page])
                                                             :show-create-modal true)}
             [:span.glyphicon.glyphicon-plus]
             " Create new record"]))))