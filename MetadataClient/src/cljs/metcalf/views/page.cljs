(ns metcalf.views.page
  (:require [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [sablono.core :refer-macros [html]]
            [condense.fields :refer [Input Checkbox ExpandingTextarea validate-required-field
                                     help-block-template label-template
                                     del-value! add-value! add-field!]]
            [condense.utils :refer [fmap title-case keys-in int-assoc-in map-keys vec-remove enum]]
            [om-tick.bootstrap :refer [Select validation-state]]
            [om-tick.form :refer [is-valid? load-errors extract-data]]
            [metcalf.content :refer [contact-groups]]
            [metcalf.globals :refer [observe-path ref-path app-state-js]]
            [metcalf.handlers :as handlers]
            [metcalf.views.widget :refer [InputField DecimalField DateField SelectField AutoCompleteField
                                          TextareaField TextareaFieldProps CheckboxField
                                          handle-value-change field-update! handle-checkbox-change]]
            [metcalf.views.form :refer [TableInlineEdit DataParametersTable Lodge AddressField
                                        OrganisationInputField ResponsiblePartyField FieldError
                                        PageErrors CreditField ResourceConstraints SupplementalInformation
                                        DashboardCreateModal NewDocumentButton]]
            [metcalf.views.fields.keyword :refer [ThemeKeywords ThemeKeywordsExtra TaxonKeywordsExtra
                                                  KeywordsThemeTable]]
            [metcalf.views.fields.coverage :refer [GeographicCoverage VerticalCoverage]]
            [metcalf.views.upload :refer [UploadData]]
            goog.dom
            cljsjs.moment
            [clojure.string :as string]
            [clojure.set :as set]
            [metcalf.logic :as logic]))

(defmulti PageView (fn [page owner] (get page :name)) :default "404")

(defmulti PageTabView (fn [page owner] [(get page :name)
                                        (get page :tab :data-identification)]))

(defn BackButton [props owner]
  (reify
    om/IDisplayName (display-name [_] "BackButton")
    om/IRender
    (render [_]
      (let [page (observe-path owner [:page])
            back (:back page)]
        (html (if back [:button.btn.btn-default.BackButton
                        {:on-click handlers/back!}
                        [:span.glyphicon.glyphicon-chevron-left] " Back"]))))))

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
                 [:li [:a {:on-click handlers/reset-form!
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

(defmethod PageView "404"
  [page owner]
  (om/component
    (.log js/console "App State is" (app-state-js))
    (dom/div nil
             (dom/h1 nil "Page not found: " (get page :name)))))

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

(defmethod PageTabView ["Edit" :how]
  [page owner]
  (om/component
    (html [:div
           (om/build PageErrors {:page :how :path [:form]})
           [:h2 "5: How"]
           (om/build TextareaField
                     [:form :fields :dataQualityInfo :statement])])))

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

(defmethod PageTabView ["Edit" :lodge]
  [page owner]
  (om/component
    (html [:div
           (om/build PageErrors {:page :lodge :path [:form]})
           [:h2 "9: Lodge Metadata Draft"]
           (om/build Lodge nil)])))

(defmethod PageTabView ["Edit" :upload]
  [page owner]
  (om/component
    (html [:div
           (om/build PageErrors {:page :upload :path [:form]})
           [:h2 "8: Upload Data"]
           (om/build UploadData nil)])))

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
                  [:button.btn.btn-default.text-warn {:on-click #(handlers/archive! owner)
                                                      :disabled disabled}
                   [:span.glyphicon.glyphicon-trash]
                   " Archive"] " "
                  [:button.btn.btn-primary {:disabled (or disabled (not dirty) saving)
                                            :on-click #(handlers/save! owner)}
                   (cond
                     saving [:img {:src (str (:STATIC_URL urls) "metcalf/resources/public/img/saving.gif")}]
                     dirty  [:span.glyphicon.glyphicon-floppy-disk]
                     :else  [:span.glyphicon.glyphicon-floppy-saved])
                   " Save"]]
                 [:p.lead [:b (:username user)] " / " (if (string/blank? title) "Untitled" title)
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
                           :on-click #(do
                                       (if has-errors? (om/update! form :show-errors true))
                                       (om/update! page [:tab] id))} text]]))
                 [:div.pull-right.hidden-xs.hidden-sm
                  (when-not disabled (om/build ProgressBar nil))]]
                [:div.PageViewBody
                 (om/build PageTabView page)]]])))))

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
           (if (string/blank? name) [:em "Untitled"] name))
         (when-not disabled
           [:button.btn.btn-warn.btn-xs.pull-right
            {:on-click (partial handlers/delete-contact! owner group item)}
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
                                    (partial remove (comp string/blank? #(get-in % [:value :individualName :value])))
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

(defn DocumentTeaser [{:keys [url title last_updated status transitions
                              transition_url clone_url] :as doc} owner]
  (reify
    om/IDisplayName (display-name [_] "DocumentTeaser")
    om/IInitState (init-state [_] {})
    om/IRenderState
    (render-state [_ {:keys []}]
      (let [transitions (set transitions)
            transite (partial handlers/transite-doc transition_url)]
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
                 {:on-click (partial handlers/clone-doc clone_url)}
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

(defmethod PageView "Dashboard"
  [{:keys [show-create-modal status-filter]
    :or {status-filter logic/active-status-filter}
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
                     (if (= status-filter logic/active-status-filter)
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
                                       :on-click #(handlers/toggle-status-filter page status-filter sid)}]
                              " " sname
                              (if freq [:span.freq " (" freq ")"])
                              ]]))])]]]]))))

(defmethod PageView "Theme"
  [page owner]
  (om/component
    (html [:div.PageViewTheme.container
           (om/build BackButton nil)
           [:h1 "Research theme keywords"]
           [:p.help-block "Select keyword(s) to add to record"]
           (om/build KeywordsThemeTable nil)])))