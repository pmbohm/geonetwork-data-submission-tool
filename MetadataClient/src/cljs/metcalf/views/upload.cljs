(ns metcalf.views.upload
  (:require-macros [cljs.core.async.macros :refer [go alt! go-loop]])
  (:require [cljs.core.async :as async :refer [chan put!]]
            [ajax.core :refer [DELETE]]
            [om.core :as om :include-macros true]
            [sablono.core :refer-macros [html]]
            [metcalf.globals :refer [observe-path app-state]]
            [condense.utils :refer [map-keys]]
            goog.net.IframeIo
            goog.events
            goog.events.EventType
            goog.events.FileDropHandler
            goog.events.FileDropHandler.EventType
            [clojure.string :as string]))

(defn upload! [owner]
  (let [form (om/get-node owner "upload-form")
        io (goog.net.IframeIo.)]
    (goog.events.listen
      io goog.net.EventType.COMPLETE #(js/console.log "COMPLETE"))
    (goog.events.listen
      io goog.net.EventType.SUCCESS (fn [_]
                                      (put!
                                        (om/get-state owner :reset-file-drop)
                                        true)
                                      (->> io
                                           .getResponseJson
                                           js->clj
                                           (map-keys keyword)
                                           (swap! app-state update-in
                                                  [:attachments] conj))))
    (goog.events.listen
      io goog.net.EventType.ERROR #(js/console.log "ERROR"))
    (goog.events.listen
      io goog.net.EventType.TIMEOUT #(js/console.log "TIMEOUT"))
    (.sendFromForm io form)))


(defn FileDrop [{:keys [on-change reset-ch max-filesize] :as props} owner]
  (reify
    om/IDisplayName (display-name [_] "FileDrop")
    om/IDidMount
    (did-mount [_]
      (goog.events.listen
        (goog.events.FileDropHandler. js/document)
        goog.events.FileDropHandler.EventType.DROP
        #(set!
          (.-files (om/get-state owner :holder))
          (.. % getBrowserEvent -dataTransfer -files)))
      (go-loop []
               (when (<! reset-ch)
                 (let [holder (goog.dom.createDom "input" #js {:type "file"})]
                   (goog.events.listen
                     holder goog.events.EventType.CHANGE
                     (fn [e]
                       (let [file (.. e -target -files (item 0))]
                         (if (or (not max-filesize)
                                 (<= (.-size file) (* 1024 1024 max-filesize)))
                           (om/set-state! owner :file (.-name file))
                           (when max-filesize
                             (js/alert (str "Please, choose file less than "
                                            max-filesize "mb"))
                             (put! reset-ch true))))))
                   (om/set-state! owner {:holder holder :file nil}))
                 (recur)))
      (put! reset-ch true))
    om/IDidUpdate
    (did-update [_ _ prev-state]
      (set! (.-files (om/get-node owner "input"))
            (.-files (om/get-state owner :holder)))
      (let [file (om/get-state owner :file)]
        (when (and on-change (not= file (:file prev-state)))
          (on-change file))))
    om/IRenderState
    (render-state [_ {:keys [file holder]}]
      (html [:div
             [:div.text-center.dropzone {:on-click #(.click holder)}
              [:h3
               (or file (:placeholder props)
                   "Drop file here or click to upload")]
              [:span.help-block "Maximum file size 20 MB"]]
             [:br]
             [:input.hidden
              {:ref "input" :type "file" :name (:name props)}]]))))


(defn delete-attachment!
  "Quick and dirty delete function"
  [attachments-ref {:keys [delete_url] :as attachment}]
  (if (js/confirm "Are you sure you want to delete this file?")
    (let []
      (DELETE delete_url {:handler         (fn [{:keys [message document] :as data}]
                                             (let [pred #(= % attachment)]
                                               (om/transact! attachments-ref #(vec (remove pred %)))))
                          :error-handler   (fn [{:keys [status failure response status-text] :as data}]
                                             (js/alert "Unable to delete file"))
                          :headers         {"X-CSRFToken" (.get (goog.net.Cookies. js/document) "csrftoken")}
                          :format          :json
                          :response-format :json
                          :keywords?       true}))))

(defn UploadData [_ owner]
  (reify
    om/IDisplayName (display-name [_] "UploadData")
    om/IInitState
    (init-state [_]
      {:reset-file-drop (chan)})
    om/IRenderState
    (render-state [_ {:keys [file filename reset-file-drop]}]
      (let [attachments (observe-path owner [:attachments])
            upload-form (observe-path owner [:upload_form])
            uff (:fields upload-form)]
        (html [:div.UploadData
               (if-not (empty? attachments)
                 [:div
                  [:table.table.table-hover
                   [:thead
                    [:tr [:th "Name"]]]
                   [:tbody
                    (for [a attachments]
                      [:tr
                       [:td
                        [:a {:href (:file a) :target "blank"} (:name a)]

                        [:button.btn.btn-warn.btn-xs.pull-right
                         {:on-click #(delete-attachment! attachments a)}
                         [:span.glyphicon.glyphicon-minus]]]])]]]
                 [:p "There are no data files attached to this record"])
               [:form#upload-form
                {:ref      "upload-form"
                 :method   "POST"
                 :action   (:url upload-form)
                 :enc-type "multipart/form-data"}
                [:input {:type  "hidden"
                         :name  "csrfmiddlewaretoken"
                         :value (get-in uff [:csrfmiddlewaretoken :initial])
                         #_(.get (goog.net.Cookies. js/document) "csrftoken")}]
                [:input {:type "hidden"
                         :name "document"
                         :value (get-in uff [:document :initial])}]
                [:div.form-group
                 [:input.form-control
                  {:type      "hidden"
                   :name      "name"
                   :value     filename
                   :on-change #(om/set-state! owner :filename
                                              (.. % -target -value))}]]
                (om/build FileDrop
                          {:name        "file"
                           :max-filesize 20
                           :reset-ch    reset-file-drop
                           :on-change   (fn [name]
                                          (om/set-state-nr! owner :file name)
                                          (when (or (= filename file)
                                                    (string/blank? filename))
                                            (om/set-state!
                                              owner :filename name)))})]
               [:button.btn.btn-primary
                {:on-click #(upload! owner)
                 :disabled (when-not (not-any? string/blank? [file filename]) "disabled")}
                "Upload"]])))))