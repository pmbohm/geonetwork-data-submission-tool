(ns metcalf.core
  (:require-macros [cljs.core.async.macros :refer [go alt! go-loop]])
  (:require [cljs.core.async :as async :refer [put! <! alts! chan sub timeout dropping-buffer]]
            [clojure.string :refer [blank?]]
            [clojure.set :as set]
            [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [cljs.core.match :refer-macros [match]]
            [sablono.core :as html :refer-macros [html]]
            [clojure.string :as string]
            [clojure.walk :refer [postwalk]]
            [ajax.core :refer [GET POST DELETE]]
            [goog.net.Cookies :as Cookies]
            [condense.fields :refer [Input Checkbox ExpandingTextarea validate-required-field
                                     help-block-template label-template
                                     del-value! add-value! add-field!]]
            [om-tick.form :refer [is-valid? load-errors reset-form extract-data]]
            [om-tick.field :refer [field-zipper field-edit reset-field field?]]
            [clojure.zip :as zip :refer [zipper]]
            [om-tick.bootstrap :refer [Select validation-state]]
            [metcalf.logic :as logic :refer [extract-field-values]]
            [metcalf.content :refer [contact-groups]]

            [condense.utils :refer [fmap title-case keys-in
                                    int-assoc-in map-keys vec-remove enum]]
            cljsjs.moment

            condense.watch-state
            condense.performance
            [condense.history :as history]
            goog.dom
            goog.dom.classes
            goog.dom.ViewportSizeMonitor
            goog.events
            goog.events.EventType
            goog.events.FileDropHandler
            goog.events.FileDropHandler.EventType
            goog.net.EventType
            goog.net.IframeIo
            select-om-all.core
            select-om-all.utils
            [metcalf.routing :as router]
            [metcalf.views.page :refer [PageView PageTabView BackButton]]
            [metcalf.globals :refer [app-state pub-chan notif-chan ref-path observe-path]]
            [metcalf.handlers :as handlers]
            [metcalf.views.widget :refer [InputField DecimalField DateField SelectField AutoCompleteField
                                          TextareaField TextareaFieldProps CheckboxField
                                          handle-value-change field-update! handle-checkbox-change]]
            [metcalf.views.form :refer [TableInlineEdit DataParametersTable]]
            [metcalf.views.highlight :refer [handle-highlight-new]]
            [metcalf.views.modal :refer [Modal]]
            [metcalf.views.fields.keyword :refer [ThemeKeywords ThemeKeywordsExtra TaxonKeywordsExtra
                                                  KeywordsThemeTable]]
            [metcalf.views.fields.coverage :refer [GeographicCoverage VerticalCoverage]]
            [metcalf.views.upload :refer [UploadData]]))



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
                  :on-click #(submit! owner % document)}
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

; TODO: Move hardcoded content to metcalf.content namespace
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

(defn ror
  "Reverse OR:
   use it to update source value only if destination value is not falsey."
  [a b]
  (or b a))

(defn update-address! [contact {:keys [city organisationName deliveryPoint deliveryPoint2
                                       postalCode country administrativeArea]}]
  (om/transact! contact
                #(-> %
                     (assoc-in [:value :organisationName :value] organisationName)
                     (update-in [:value :address :deliveryPoint :value] ror deliveryPoint)
                     (update-in [:value :address :deliveryPoint2 :value] ror deliveryPoint2)
                     (update-in [:value :address :city :value] ror city)
                     (update-in [:value :address :administrativeArea :value] ror administrativeArea)
                     (update-in [:value :address :postalCode :value] ror postalCode)
                     (update-in [:value :address :country :value] ror country))))

(defn re-escape
  [s]
  (string/replace s #"[\-\[\]\/\{\}\(\)\*\+\?\.\\\^\$\|]" #(str "\\" %)))

(defn OrganisationInputField0 [props owner]
  (reify
    om/IDisplayName (display-name [_] "OrganisationInputField")
    om/IInitState
    (init-state [_] {:open?    false
                     :event-ch (chan)})

    om/IWillMount
    (will-mount [_]

      (let [contact (om/get-props owner)
            {:keys [organisationName]} (:value contact)
            {:keys [event-ch]} (om/get-state owner)
            open! #(om/set-state! owner :open? true)
            close! #(om/set-state! owner :open? false)
            change! #(do (field-update! owner organisationName %))
            select! #(update-address! contact %)]

        (go (loop [state :idle]
              (om/set-state! owner :state state)
              (let [[event data] (<! event-ch)]
                (match [state event data]
                  [:idle :focus _] (do (open!)
                                       (recur :active))
                  [:active :blur _] (do (close!)
                                        (recur :idle))
                  [:active :change value] (do (change! value)
                                              (open!)
                                              (recur :active))
                  [_ :select institution] (do (select! institution)
                                              (close!)
                                              (recur :active))
                  :else (recur state)))))))

    om/IRenderState
    (render-state [_ {:keys [open? event-ch]}]
      (let [{:keys [organisationName]} (:value props)
            search-fn #(re-seq (re-pattern (str "(?i)" (re-escape (:value organisationName)))) %)
            institutions (observe-path owner [:institutions])
            event! (fn [& args] (do (put! event-ch args) nil))]
        (html [:div.OrganisationInputField
               (om/build Input (assoc organisationName
                                 :class "InputWithDropdown"
                                 :on-focus #(event! :focus)
                                 :on-blur #(event! :blur)
                                 :on-change #(event! :change (.. % -target -value))))
               [:div {:class (if open? "open")
                      :style {:position "absolute"}}
                [:ul.dropdown-menu {:style {:max-height "10em"}}
                 (for [institution (->> institutions
                                        (filter #(search-fn (:organisationName %)))
                                        (take 10))]
                   [:li
                    [:a.menuitem
                     {:on-mouse-down #(do (event! :select institution)
                                          (.preventDefault %))}
                     (:organisationName institution)]])]]])))))

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

(defn NavbarHeader [props owner]
  (reify
    om/IDisplayName (display-name [_] "NavbarHeader")
    om/IRender
    (render [_]
      (let [{:keys [Dashboard]} (observe-path owner [:context :urls])
            {:keys [title tag_line]} (observe-path owner [:context :site])]
        (html [:div.navbar-header
               [:a.navbar-brand {:href Dashboard}
                title " " tag_line]])))))

(defn NavbarForm [props owner]
  (reify
    om/IDisplayName (display-name [_] "NavbarForm")
    om/IRender
    (render [_]
      (html [:form.navbar-form.navbar-left {:role "search"}
             [:div.form-group [:input.form-control {:placeholder "Search"}]]
             [:button.btn.btn-default "Submit"]]))))

(defn Navbar [props owner]
  (reify
    om/IDisplayName (display-name [_] "PageNavigation")
    om/IRender
    (render [_]
      (let [{:keys [username]} (observe-path owner [:context :user])
            {:keys [account_profile account_logout]} (ref-path [:context :urls])
            {:keys [guide_pdf]} (observe-path owner [:context :site])]
        (html [:nav.navbar.navbar-inverse
               [:div.container
                (om/build NavbarHeader nil)
                [:ul.nav.navbar-nav.navbar-right
                 [:li [:a {:on-click #(swap! app-state update :form reset-form)
                           :title    "Reset form (for testing)"}
                       [:span.glyphicon.glyphicon-fire]
                       " "]]
                 [:li [:a {:href guide_pdf :target "_blank" :title "Help"}
                       [:span.glyphicon.glyphicon-book]
                       " Help"]]
                 [:li [:a {:href  account_profile
                           :title "Profile"}
                       [:span.glyphicon.glyphicon-user]
                       " " username]]
                 [:li [:a {:href  account_logout
                           :title "Logout"} [:span.glyphicon.glyphicon-log-out]
                       " Sign out"]]]]])))))


(defmethod PageView "404"
  [page owner]
  (om/component
    (.log js/console "App State is" (clj->js @app-state))
    (dom/div nil
             (dom/h1 nil "Page not found: " (get page :name))
             (dom/pre nil (.stringify js/JSON (clj->js @app-state) nil "  ")))))


(defmethod PageView "Error"
  [{:keys [text code detail]} owner]
  (om/component
    (html [:div
           (om/build Navbar nil)
           [:div.container
            [:div.PageViewBody
             [:p.lead "Oops! " (pr-str text)]
             [:p "The server responded with a " [:code code " " (pr-str text)] " error."]
             [:pre (pr-str detail)]]]])))


(defmethod PageTabView ["Edit" :data-identification]
  [page owner]
  (om/component
    (html [:div
           (om/build PageErrors {:page :data-identification :path [:form]})
           [:h2 "1. Data Identification"]
           (om/build TextareaField [:form :fields :identificationInfo :title])
           (om/build DateField [:form :fields :identificationInfo :dateCreation])
           (om/build SelectField [:form :fields :identificationInfo :topicCategory])
           (om/build SelectField [:form :fields :identificationInfo :status])
           (om/build SelectField [:form :fields :identificationInfo :maintenanceAndUpdateFrequency])])))


(defmethod PageTabView ["Edit" :what]
  [page owner]
  (om/component
    (html [:div
           (om/build PageErrors {:page :what :path [:form]})
           [:h2 "2. What"]
           [:span.abstract-textarea
            (om/build TextareaField [:form :fields :identificationInfo :abstract])]
           (om/build ThemeKeywords nil)
           (om/build ThemeKeywordsExtra nil)
           (om/build TaxonKeywordsExtra nil)])))

(defmethod PageTabView ["Edit" :when]
  [page owner]
  (om/component
    (html [:div
           (om/build PageErrors {:page :when :path [:form]})
           [:h2 "3. When"]
           (om/build DateField [:form :fields :identificationInfo :beginPosition])
           (om/build DateField [:form :fields :identificationInfo :endPosition])
           (om/build SelectField [:form :fields :identificationInfo :samplingFrequency])])))

(defmethod PageTabView ["Edit" :where]
  [page owner]
  (om/component
    (html [:div
           (om/build PageErrors {:page :where :path [:form]})
           [:h2 "4. Where"]
           (om/build GeographicCoverage nil)
           (om/build VerticalCoverage nil)])))

(defn CreditField [path owner]
  (reify
    om/IDisplayName (display-name [_] "CreditField")
    om/IRender
    (render [_]
      (html [:div.CreditField (om/build TextareaField path)]))))

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

(defn parties-list [owner group]
  (let [{:keys [disabled] :as parties} (-> group contact-groups :path ref-path)
        {:keys [selected-group selected-item]} (om/get-state owner)
        selected-item (when (= group selected-group) selected-item)]
    [:div.list-group
     (for [[item party] (-> parties :value enum)]
       [:a.list-group-item
        {:class    (if (= item selected-item) "active")
         :on-click #(om/set-state! owner {:selected-group group
                                          :selected-item  item})}
        [:span
         (let [name (get-in party [:value :individualName :value])]
           (if (blank? name) [:em "Untitled"] name))
         (when-not disabled
           [:button.btn.btn-warn.btn-xs.pull-right
            {:on-click (partial delete-contact! owner group item)}
            [:i.glyphicon.glyphicon-minus]])]])]))

(defmethod PageTabView ["Edit" :who]
  [page owner]
  (reify
    om/IDisplayName (display-name [_] "")
    om/IInitState
    (init-state [_]
      {:selected-group (ffirst
                         (filter
                           #(-> % second :path (conj :value) ref-path first)
                           (enum contact-groups)))
       :selected-item  0})
    om/IRenderState
    (render-state [_ {:keys [selected-group selected-item open hold]}]
      (let [cursors (mapv (comp (partial observe-path owner) :path) contact-groups)
            new! (fn [group & [field]]
                   (let [many-field (cursors group)]
                     (if field
                       (add-value! many-field (:value field))
                       (add-field! many-field))
                     (om/set-state!
                       owner {:selected-group group
                              :selected-item  (-> many-field :value count)})))
            all-parties (mapv (comp set
                                    (partial remove (comp blank? #(get-in % [:value :individualName :value])))
                                    :value)
                              cursors)
            all-parties-set (apply clojure.set/union all-parties)]
        (html [:div
               (om/build PageErrors {:page :who :path [:form]})
               [:h2 "6: Who"]
               [:div.row
                [:div.col-sm-4
                 (for [[group {:keys [title]}] (enum contact-groups)]
                   (let [parties (clojure.set/difference
                                   all-parties-set (all-parties group))]
                     (list
                       [:h4 title (when (get-in cursors [group :required]) " *")]
                       (parties-list owner group)
                       (when-not (get-in cursors [group :disabled])
                         [:div.dropdown
                          {:class   (if (= open group) "open")
                           :on-blur #(om/update-state!
                                      owner :open
                                      (fn [x] (when (or hold (not= x group)) x)))}
                          [:button.btn.btn-default.dropdown-toggle
                           {:on-click #(if (zero? (count parties))
                                        (new! group)
                                        (om/update-state!
                                          owner :open
                                          (fn [x] (when (not= x group) group))))}
                           [:span.glyphicon.glyphicon-plus]
                           " Add person"]
                          [:ul.dropdown-menu
                           {:on-mouse-enter #(om/set-state! owner :hold true)
                            :on-mouse-leave #(om/set-state! owner :hold false)}
                           [:li.dropdown-header "Copy person"]
                           (for [x parties]
                             [:li [:a {:tab-index -1
                                       :href      "#"
                                       :on-click  (fn [e]
                                                    (.preventDefault e)
                                                    (new! group x)
                                                    (om/set-state! owner :open false))}
                                   (get-in x [:value :individualName :value])]])
                           [:li.divider]
                           [:li [:a {:href     "#"
                                     :on-click (fn [e]
                                                 (.preventDefault e)
                                                 (new! group)
                                                 (om/set-state! owner :open false))}
                                 "New person"]]]]))))]
                [:div.col-sm-8
                 (when (and selected-group selected-item)
                   (om/build ResponsiblePartyField
                             (-> contact-groups
                                 (get-in [selected-group :path])
                                 (conj :value selected-item))))]]

               [:h2 "Other credits"]
               (om/build
                 TableInlineEdit
                 {:form       CreditField
                  :field-path [:form :fields :identificationInfo :credit]})])))))

(defmethod PageTabView ["Edit" :how]
  [page owner]
  (om/component
    (html [:div
           (om/build PageErrors {:page :how :path [:form]})
           [:h2 "5: How"]
           (om/build TextareaField
                     [:form :fields :dataQualityInfo :statement])])))

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

(defmethod PageTabView ["Edit" :about]
  [page owner]
  (om/component
    (html [:div
           (om/build PageErrors {:page :about :path [:form]})
           [:h2 "7: About Dataset"]
           (om/build DataParametersTable [:form :fields :identificationInfo :dataParameters])
           (om/build ResourceConstraints nil)
           (om/build TextareaField [:form :fields :identificationInfo :otherConstraints])
           (om/build SupplementalInformation [:form :fields :identificationInfo :supplementalInformation])
           (om/build InputField {:path [:form :fields :distributionInfo :distributionFormat :name]})
           (om/build InputField {:path [:form :fields :distributionInfo :distributionFormat :version]})])))

(defmethod PageTabView ["Edit" :upload]
  [page owner]
  (om/component
    (html [:div
           (om/build PageErrors {:page :upload :path [:form]})
           [:h2 "8: Upload Data"]
           (om/build UploadData nil)])))

(defmethod PageTabView ["Edit" :lodge]
  [page owner]
  (om/component
    (html [:div
           (om/build PageErrors {:page :lodge :path [:form]})
           [:h2 "9: Lodge Metadata Draft"]
           (om/build Lodge nil)])))

(defn ProgressBar [props owner]
  (reify
    om/IDisplayName (display-name [_] "ProgressBar")
    om/IRender
    (render [_]
      (let [{:keys [fields errors empty required required-errors]
             :as progress} (observe-path owner [:progress])
            can-submit? (= errors 0)
            pct (-> (- fields empty) (/ fields) (* 100) int (str "%"))]
        (html
          [:div {:style {:height 20}}
           [:div.ProgressBar {:style {:width   120
                                      :display "inline-block"}
                              :title "Required"}
            [:div.progress
             [:div.progress-bar {:class (if can-submit?
                                          "progress-bar-success"
                                          "progress-bar-danger")
                                 :style {:width pct}}
              pct]]]])))))


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


(defmethod PageView "Edit"
  [page owner]
  (reify
    om/IRenderState
    (render-state [_ {:keys [saving]}]
      (let [progress (observe-path owner [:progress])
            {:keys [user urls]} (observe-path owner [:context])
            {:keys [dirty disabled] :as form} (observe-path owner [:form])
            {:keys [status title last_updated]} (observe-path owner [:context :document])]
        (html [:div
               (om/build Navbar nil)
               [:div.pagehead
                [:div.container
                 [:div.pull-right
                  [:button.btn.btn-default.text-warn {:on-click #(archive! owner)
                                                      :disabled disabled}
                   [:span.glyphicon.glyphicon-trash]
                   " Archive"] " "
                  [:button.btn.btn-primary {:disabled (or disabled (not dirty) saving)
                                            :on-click #(save! owner)}
                   (cond
                     saving [:img {:src (str (:STATIC_URL urls) "metcalf/resources/public/img/saving.gif")}]
                     dirty  [:span.glyphicon.glyphicon-floppy-disk]
                     :else  [:span.glyphicon.glyphicon-floppy-saved])
                   " Save"]]
                 [:p.lead [:b (:username user)] " / " (if (blank? title) "Untitled" title)
                  " "
                  [:span.label.label-info {:style {:font-weight "normal"}} status]
                  [:br]
                  [:small [:i {:style {:color     "#aaa"
                                       :font-size "0.7em"}}
                           "Last edited " (-> last_updated js/moment .fromNow)]]]]]
               [:div.Home.container
                [:ul.nav.nav-tabs
                 (for [[id text] [[:data-identification "Data identification"]
                                  [:what "What"]
                                  [:when "When"]
                                  [:where "Where"]
                                  [:how "How"]
                                  [:who "Who"]
                                  [:about "About"]
                                  [:upload "Upload"]
                                  [:lodge "Lodge"]]]
                   (let [error-count (get-in progress [:page-errors id])
                         has-errors? (and error-count (> error-count 0))
                         text [:span text " " (if has-errors?
                                                [:b.text-warning "*"])]]
                     [:li {:class (if (= id (get page :tab :data-identification)) "active")}
                      [:a {:style    {:cursor "pointer"}
                           :on-click #(do #_(save!)
                                       (if has-errors? (om/update! form :show-errors true))
                                       (om/update! page [:tab] id))} text]]))
                 [:div.pull-right.hidden-xs.hidden-sm
                  (when-not disabled (om/build ProgressBar nil))]]
                [:div.PageViewBody
                 (om/build PageTabView page)]]])))))


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


(defn handle-dashboard-create-save [owner e]
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
                                :on-save      #(handle-dashboard-create-save owner %)
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



(defn test-transition [transition]
  (let [{:keys [transition_url] :as doc} (get-in @app-state [:context :document])]
    (POST transition_url
          {:params          #js {:transition transition}
           :handler         #(println [:transition-success %])
           :error-handler   #(println [:transition-failure %])
           :headers         {"X-CSRFToken" (.get (goog.net.Cookies. js/document) "csrftoken")}
           :format          :json
           :response-format :json
           :keywords?       true})))



(defn DocumentTeaser [{:keys [url title last_updated status transitions
                              transition_url clone_url] :as doc} owner]
  (reify
    om/IDisplayName (display-name [_] "DocumentTeaser")
    om/IInitState (init-state [_] {})
    om/IRenderState
    (render-state [_ {:keys []}]
      (let [transitions (set transitions)
            transite (partial transite-doc transition_url)]
        (html [:div.list-group-item.DocumentTeaser
               [:div.pull-right
                (if (contains? transitions "archive")
                  [:span.btn.btn-default.noborder.btn-xs
                   {:on-click (partial transite "archive")}
                   [:span.glyphicon.glyphicon-trash] " archive"])
                (if (contains? transitions "delete_archived")
                  [:span.btn.btn-default.noborder.btn-xs
                   {:on-click (partial transite "delete_archived")}
                   [:span.glyphicon.glyphicon-remove] " delete"])
                (if (contains? transitions "restore")
                  [:span.btn.btn-default.noborder.btn-xs
                   {:on-click (partial transite "restore")}
                   [:span.glyphicon.glyphicon-open] " restore"])
                [:span.btn.btn-default.noborder.btn-xs
                 {:on-click (partial clone-doc clone_url)}
                 [:span.glyphicon.glyphicon-duplicate] " clone"]
                [:span.btn.btn-default.noborder.btn-xs {:on-click #(aset js/location "href" url)}
                 [:span.glyphicon.glyphicon-pencil] " edit"]]
               [:p.lead.list-group-item-heading
                [:span.link {:on-click #(aset js/location "href" url)}
                 [:b (:username (:owner doc))] " / " title]
                " "
                [:span.label.label-info {:style {:font-weight "normal"}}  status]]
               [:p.list-group-item-text
                [:i {:style {:color     "#aaa"
                             :font-size "0.9em"}}
                 (if-not (empty? last_updated)
                   [:span
                    "Last edited " (.fromNow (js/moment last_updated))
                    " by " (:username (:owner doc))]
                   "Has not been edited yet")]]])))))


(defn toggle-status-filter
  [page-ref status-filter status]
  (if (contains? status-filter status)
    (om/update! page-ref :status-filter (disj status-filter status))
    (om/update! page-ref :status-filter (conj status-filter status))))


(def active-status-filter #{"Draft" "Submitted"})


(defmethod PageView "Dashboard"
  [{:keys [show-create-modal status-filter]
    :or {status-filter active-status-filter}
    :as page} owner]
  (om/component
    (let [{:keys [documents status urls user]} (observe-path owner [:context])
          status-freq (frequencies (map :status documents))
          all-statuses (set (keys status-freq))
          relevant-status-filter (set/intersection status-filter all-statuses)
          filtered-docs (->> documents
                             (filter (fn [{:keys [status]}]
                                       (contains? relevant-status-filter status)))
                             (sort-by :last_updated)
                             (reverse))]
      (html [:div
             (om/build Navbar nil)
             (if show-create-modal (om/build DashboardCreateModal nil))
             [:div.container
              [:span.pull-right (om/build NewDocumentButton nil)]
              [:h1 "My Records"]
              [:div.row
               [:div.col-sm-9
                [:div.list-group
                 (om/build-all DocumentTeaser filtered-docs)
                 (if (empty? documents)
                   [:a.list-group-item {:on-click #(do (om/update! page :show-create-modal true)
                                                       (.preventDefault %))
                                        :href (:Create urls)}
                    [:span.glyphicon.glyphicon-star.pull-right]
                    [:p.lead.list-group-item-heading [:b (:username user)] " / My first record "
                     ]
                    [:p.list-group-item-text "Welcome!  Since you're new here, we've created your first record. "
                     [:span {:style {:text-decoration "underline"}} "Click here"] " to get started."]]
                   (if (empty? filtered-docs)
                     (if (= status-filter active-status-filter)
                       [:div
                        [:p "You don't have any active records: "
                         [:a {:on-click #(om/update! page :status-filter (set (keys status-freq)))}
                          "show all documents"] "."]
                        (om/build NewDocumentButton nil)]
                       [:div
                        [:p "No documents match your filter: "
                         [:a {:on-click #(om/update! page :status-filter (set (keys status-freq)))}
                          "show all documents"] "."]
                        (om/build NewDocumentButton nil)])))]]
               [:div.col-sm-3
                (if-not (empty? status-freq)
                  [:div
                   (for [[sid sname] status]
                     (let [freq (get status-freq sid)]

                       [:div [:label
                              [:input {:type     "checkbox"
                                       :disabled (not freq)
                                       :checked  (contains? relevant-status-filter sid)
                                       :on-click #(toggle-status-filter page status-filter sid)}]
                              " " sname
                              (if freq [:span.freq " (" freq ")"])
                              ]]))])]]]]))))


(defn LegacyIECompatibility [props owner]
  (reify
    om/IDisplayName
    (display-name [_] "LegacyIECompatibility")
    om/IRender
    (render [_]
      (html [:div.LegacyIECompatibility

             [:div.row
              [:div.col-md-6.col-md-offset-3
               [:div.container.box
                [:h1 "Browser not supported"]
                [:p.lead "The work request system doesn't support early versions of Internet Explorer."]
                [:p.lead "Please use Google Chrome to access this system or upgrade your browser."]
                [:hr]
                [:p "Related links:"]
                [:ul
                 [:li [:a {:href "http://www.techtimes.com/articles/12659/20140811/dingdong-internet-explorer-8-is-dead-microsoft-will-end-its-life-in-january-2016.htm"}
                       "Dingdong, Internet Explorer 8 is dead: Microsoft will end its life in January 2016 (TechTimes)"]
                  " and IE 9 will follow a year later."]
                 [:li [:a {:href "http://www.computerworld.com/article/2492571/web-apps/google-to-drop-support-for-ie8-on-nov--15.html"}
                       "Google to drop support for IE8 on Nov 15 [2012]"]]
                 [:li [:a {:href "http://www.w3counter.com/globalstats.php"}
                       "Market share of IE8 and IE9 is around 2% each world wide."]]]
                [:br]]]]]))))


(defn AppRoot [app owner]
  (reify
    om/IDisplayName (display-name [_] "AppRoot")
    om/IRender
    (render [_]
      (if (and goog.userAgent.IE (not (goog.userAgent.isVersionOrHigher 10)))
        (om/build LegacyIECompatibility nil)
        (om/build PageView (:page app))))))


(set! (.-onbeforeunload js/window)
      (fn []
        (if (get-in @app-state [:form :dirty])
          "This will navigate away from the Data Submission Tool and all unsaved work will be lost. Are you sure you want to do this?")))

(defmethod PageView "Theme"
  [page owner]
  (om/component
    (html [:div.PageViewTheme.container
           (om/build BackButton nil)
           [:h1 "Research theme keywords"]
           [:p.help-block "Select keyword(s) to add to record"]
           (om/build KeywordsThemeTable nil)])))

(defn main []
  (when-let [ele (.getElementById js/document "Content")]
    (condense.watch-state/enable-state-change-reporting app-state)
    ;(condense.performance/enable-performance-reporting)
    (when (-> @app-state :page :name nil?)
      (reset! app-state (handlers/initial-state (js->clj (aget js/window "payload") :keywordize-keys true)))
      (router/start! {:iref app-state
                      :path [:page :tab]
                      :->hash (fnil name "")
                      :<-hash #(if (blank? %) :data-identification (keyword %))}))
    (om/root
      AppRoot
      app-state
      {:target     ele
       :shared     {:notif-chan notif-chan
                    :pub-chan   pub-chan}
       :instrument condense.performance/performance-instrument})))
