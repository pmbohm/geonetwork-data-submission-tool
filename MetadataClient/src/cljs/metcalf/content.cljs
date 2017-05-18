(ns metcalf.content)

(def term-defaults
  {:term              {:type nil}
   :vocabularyTermURL {:type nil}
   :vocabularyVersion {:type nil}
   :termDefinition    {:type nil}})

(def responsible-party-defaults
  {:individualName        {:type        nil
                           :label       "Contact name"
                           :placeholder "Last name, First name"
                           :required    true
                           :page        :who}
   :orcid                 {:type        nil
                           :label       "ORCID ID"
                           :placeholder "ORCID researcher page URL"
                           :page        :who}
   :role                  {:type        nil
                           :label       "Role"
                           :placeholder "Role"
                           :required    true
                           :page        :who}
   :organisationName      {:type        nil
                           :label       "Organisation"
                           :placeholder "Organisation"
                           :required    true
                           :page        :who}
   :phone                 {:type  nil
                           :label "Phone number"}
   :facsimile             {:type  nil
                           :label "Fax number"}
   :electronicMailAddress {:type     nil
                           :label    "Email address"
                           :required true
                           :page     :who}
   :address               {:deliveryPoint      {:type nil}
                           :deliveryPoint2     {:type nil}
                           :city               {:type nil :help "City"}
                           :administrativeArea {:type nil :help "State/territory"}
                           :postalCode         {:type nil :help "Postal / Zip code"}
                           :country            {:type nil :help "Country"}}})

(def default-payload
  "This is merged in.  Allows for some defaults to be applied.  Mostly used for bits of content and field props."
  {:form
   {:fields
    {:dataSources         {:type   nil
                           :page   :upload
                           :label  "Data services"
                           :help   "Please note: This is intended for advanced users only"
                           :many   true
                           :value  []
                           :fields {:description {:type        nil
                                                  :page        :upload
                                                  :label       "Title"
                                                  :placeholder "e.g. Map of Argo profiles"}
                                    :url         {:type        nil
                                                  :page        :upload
                                                  :label       "URL"
                                                  :placeholder "e.g. http://geoserver-123.aodn.org.au/geoserver/wms"}
                                    :name        {:type        nil
                                                  :page        :upload
                                                  :label       "Layer"
                                                  :placeholder "e.g. imos.argo_profile_map"}
                                    :protocol    {:type  nil
                                                  :page  :upload
                                                  :label "Protocol"
                                                  :options
                                                         [["OGC:WMS-1.3.0-http-get-map" "OGC Web Map Service (WMS)"]
                                                          ["OGC:WFS-1.0.0-http-get-capabilities" "OGC Web Feature Service (WFS)"]
                                                          ["WWW:LINK-1.0-http--downloaddata" "Other/unknown"]]}}}
     :attachments         {:type   nil
                           :page   :upload
                           :label  "Attachments"
                           :many   true
                           :value  []
                           :fields {:file       {:type  nil
                                                 :page  :upload
                                                 :label "File"}
                                    :name       {:type  nil
                                                 :page  :upload
                                                 :label "Name"}
                                    :delete_url {:type nil}}}
     :supportingResources {:type   nil
                           :page   :about
                           :label  "Supporting resources"
                           :many   true
                           :value  []
                           :fields {:description {:type  nil
                                                  :page  :about
                                                  :label "Title"}
                                    :url         {:type  nil
                                                  :page  :about
                                                  :label "URL"}}}
     :dataQualityInfo
                          {:statement {:type     nil
                                       :rows     20
                                       :label    "Methodological information"
                                       :help     "Provide a brief statement of the methods used for collection of the
                                   data, can include information regarding sampling equipment (collection hardware),
                                   procedures, and precision/resolution of data collected."
                                       :value    nil
                                       :required true
                                       :page     :how}}
     :identificationInfo
                          {:title                         {:type     nil
                                                           :label    "Title"
                                                           :help     "Clear and concise description of the content of the resource"
                                                           :rows     3
                                                           :required true
                                                           :page     :data-identification}
                           :dateCreation                  {:type     nil
                                                           :label    "Date of record creation"
                                                           :required true
                                                           :page     :data-identification}
                           :topicCategory                 {:type     nil
                                                           :label    "Topic category"
                                                           :required true
                                                           :options  [["biota" "biota"]
                                                                      ["climatology/meteorology/atmosphere" "climatology/meteorology/atmosphere"]
                                                                      ["oceans" "oceans"]
                                                                      ["geoscientificInformation" "geoscientificInformation"]
                                                                      ["inlandWater" "inlandWater"]]
                                                           :page     :data-identification}
                           :status                        {:type     nil
                                                           :label    "Status of data"
                                                           :required true
                                                           :options  [["onGoing" "ongoing"]
                                                                      ["completed" "completed"]]
                                                           :page     :data-identification}
                           :maintenanceAndUpdateFrequency {:type     nil
                                                           :label    "Maintenance and update frequency"
                                                           :required true
                                                           :options  [["daily" "Daily"]
                                                                      ["weekly" "Weekly"]
                                                                      ["monthly" "Monthly"]
                                                                      ["quarterly" "Quarterly"]
                                                                      ["annually" "Annually"]
                                                                      ["ongoing" "Ongoing"]
                                                                      ["asNeeded" "As required"]
                                                                      ["none-planned" "None planned"]]
                                                           :page     :data-identification}
                           :abstract                      {:type     nil
                                                           :label    "Abstract"
                                                           :help     "Describe the content of the resource; e.g. what information was collected,
                                          how was it collected, brief statement about the purpose of the study"
                                                           :rows     10
                                                           :required true
                                                           :page     :what}
                           :keywordsTheme                 {:keywords {:type        nil
                                                                      :label       "Research theme keywords"
                                                                      :help        "Select up to 12 research theme keywords describing your data"
                                                                      :placeholder "Search for keywords"
                                                                      :required    true
                                                                      :page        :what}}
                           :keywordsThemeExtra            {:keywords {:type        nil
                                                                      :label       "Additional theme keywords"
                                                                      :help        "Enter your own additional theme keywords as required and click + to add"
                                                                      :placeholder "Enter a theme keyword"
                                                                      :page        :what}}
                           :keywordsTaxonExtra            {:keywords {:type        nil
                                                                      :placeholder "Enter a taxon keyword"
                                                                      :help        "Add any taxon names describing your data and click + to add"
                                                                      :label       "Taxon keywords"
                                                                      :page        :what}}

                           :beginPosition                 {:type     nil
                                                           :label    "Start date"
                                                           :required true
                                                           :page     :when}
                           :endPosition                   {:type     nil
                                                           :label    "End date"
                                                           :required true
                                                           :page     :when}
                           :samplingFrequency             {:type    nil
                                                           :label   "Sampling frequency"
                                                           :options [["daily" "Daily"]
                                                                     ["weekly" "Weekly"]
                                                                     ["monthly" "Monthly"]
                                                                     ["quarterly" "Quarterly"]
                                                                     ["annually" "Annually"]
                                                                     ["ongoing" "Ongoing"]
                                                                     ["asNeeded" "As required"]
                                                                     ["irregular" "Irregular"]
                                                                     ["none-planned" "None planned"]]
                                                           :page    :when}
                           :geographicElement             {:hasGeographicCoverage {:type  nil
                                                                                   :label "Does data have a geographic coverage?"
                                                                                   :page  :where
                                                                                   :value true}
                                                           :boxes                 {:type   nil
                                                                                   :page   :where
                                                                                   :label  "Geographic coverage"
                                                                                   :many   true
                                                                                   :value  []
                                                                                   :fields {:northBoundLatitude {:max      90
                                                                                                                 :min      -90
                                                                                                                 :required true
                                                                                                                 :label    "North limit"
                                                                                                                 :page     :where}
                                                                                            :southBoundLatitude {:max      90
                                                                                                                 :min      -90
                                                                                                                 :required true
                                                                                                                 :label    "South limit"
                                                                                                                 :page     :where}
                                                                                            :eastBoundLongitude {:max      180
                                                                                                                 :min      -180
                                                                                                                 :required true
                                                                                                                 :label    "East limit"
                                                                                                                 :page     :where}
                                                                                            :westBoundLongitude {:max      180
                                                                                                                 :min      -180
                                                                                                                 :required true
                                                                                                                 :label    "West limit"
                                                                                                                 :page     :where}}}}


                           :verticalElement               {:hasVerticalExtent {:type  nil
                                                                               :label "Does data have a vertical (depth or altitude) component?"
                                                                               :page  :where}
                                                           :minimumValue      {:type     nil
                                                                               :label    "Minimum (m)"
                                                                               :required true
                                                                               :page     :where}
                                                           :maximumValue      {:type     nil
                                                                               :label    "Maximum (m)"
                                                                               :required true
                                                                               :page     :where}
                                                           :verticalCRS       {:type     nil
                                                                               :label    "Vertical type"
                                                                               :required true
                                                                               :options  [["EPSG::5715" "Depth (distance below mean sea level)"]
                                                                                          ["EPSG::5714" "Altitude (height above mean sea level)"]]
                                                                               :page     :where}}
                           :credit                        {:type  nil
                                                           :help  "Acknowledge the contribution of any funding schemes or organisations."
                                                           :label "Other credits"}

                           :dataParameters                {:label  "Data parameters"
                                                           :type   nil
                                                           :many   true
                                                           :page   :about
                                                           :fields {:longName   {:term              {:type     nil
                                                                                                     :page     :about
                                                                                                     :label    "Name"
                                                                                                     :required true}
                                                                                 :vocabularyTermURL {:type nil
                                                                                                     :page :about}
                                                                                 :vocabularyVersion {:type nil
                                                                                                     :page :about}
                                                                                 :termDefinition    {:type nil
                                                                                                     :page :about}}
                                                                    :name       {:type        nil
                                                                                 :label       ""
                                                                                 :placeholder "Name in dataset (optional)"
                                                                                 :page        :about}
                                                                    :unit       {:term              {:type nil :label "Unit" :required true
                                                                                                     :page :about}
                                                                                 :vocabularyTermURL {:type nil
                                                                                                     :page :about}
                                                                                 :vocabularyVersion {:type nil
                                                                                                     :page :about}
                                                                                 :termDefinition    {:type nil
                                                                                                     :page :about}}
                                                                    :instrument {:term              {:type  nil
                                                                                                     :label "Instrument"}
                                                                                 :vocabularyTermURL {:type nil
                                                                                                     :page :about}
                                                                                 :vocabularyVersion {:type nil
                                                                                                     :page :about}
                                                                                 :termDefinition    {:type nil
                                                                                                     :page :about}}
                                                                    :platform   {:term              {:type nil
                                                                                                     :label "Platform"
                                                                                                     :page :about}
                                                                                 :vocabularyTermURL {:type nil
                                                                                                     :page :about}
                                                                                 :vocabularyVersion {:type nil
                                                                                                     :page :about}
                                                                                 :termDefinition    {:type nil
                                                                                                     :page :about}}}}
                           :creativeCommons               {:type     nil
                                                           :label    "License"
                                                           :required true
                                                           :page     :about
                                                           :options  {"http://creativecommons.org/licenses/by/4.0/"    "Creative Commons by Attribution (recommended​)"
                                                                      "http://creativecommons.org/licenses/by-nc/4.0/" "Creative Commons, Non-commercial Use only"
                                                                      "http://creativecommons.org/licenses/other"      "Other constraints"}}
                           :otherConstraints              {:type        nil
                                                           :placeholder "Enter additional license requirements"
                                                           :is-hidden   true
                                                           :page        :about}
                           :useLimitations                {:type   nil
                                                           :label  "Use limitations"
                                                           :many   true
                                                           :page   :about}
                           :supplementalInformation       {:type  nil
                                                           :label "Publications associated with dataset"
                                                           :page  :about}
                           :citedResponsibleParty         {:type   nil
                                                           :many   true
                                                           :fields responsible-party-defaults
                                                           :page   :who
                                                           :label  "Responsible parties"}
                           :pointOfContact                {:type     nil
                                                           :many     true
                                                           :fields   responsible-party-defaults
                                                           :page     :who
                                                           :required true
                                                           :label    "Point of contact"}}
     :distributionInfo
                          {:distributionFormat
                           {:version {:type        nil
                                      :label       "Data file format date/version"
                                      :placeholder "Date format date or version if applicable"}
                            :name    {:type        nil
                                      :label       "Data file format"
                                      :placeholder "e.g. Microsoft Excel, CSV, NetCDF"}}}
     :noteForDataManager  {:type  nil
                           :label "Include a note for the data manager"
                           :style {:min-height "80px"}
                           :page  :lodge}}}})

(def contact-groups
  [{:path  [:form :fields :identificationInfo :pointOfContact]
    :title "Point of contact for dataset"}
   {:path  [:form :fields :identificationInfo :citedResponsibleParty]
    :title "Responsible parties for creating dataset"}])
