(ns metcalf.widget.boxmap
  (:require [om.core :as om :include-macros true]
            [sablono.core :as html :refer-macros [html]]
            [clojure.data :refer [diff]]
            [metcalf.utils :as utils]))

(defn extent->bounds [[w s e n]]
  (js/google.maps.LatLngBounds.
    (js/google.maps.LatLng. s w)
    (js/google.maps.LatLng. n e)))

(defn bounds->extent [bounds]
  (let [ne (.getNorthEast bounds)
        sw (.getSouthWest bounds)]
    [(.lng sw) (.lat sw) (.lng ne) (.lat ne)]))

(defn shape->box-extent [shape]
  [:box (bounds->extent (.getBounds shape))])

(defn shape->extent [shape]
  (bounds->extent (.getBounds shape)))

(defn shapes->extents [shapes]
  (into #{} (for [shape shapes] (shape->extent shape))))

(defn value->box-extent
  [{:keys [northBoundLatitude westBoundLongitude
           eastBoundLongitude southBoundLatitude]}]
  [:box [(:value westBoundLongitude)
         (:value southBoundLatitude)
         (:value eastBoundLongitude)
         (:value northBoundLatitude)]])

(defn value->extent
  [{:keys [northBoundLatitude westBoundLongitude
           eastBoundLongitude southBoundLatitude]}]
  [(:value westBoundLongitude)
   (:value southBoundLatitude)
   (:value eastBoundLongitude)
   (:value northBoundLatitude)])

(defn extent->value
  [[westBoundLongitude southBoundLatitude eastBoundLongitude northBoundLatitude]]
  {:westBoundLongitude {:value westBoundLongitude}
   :southBoundLatitude {:value southBoundLatitude}
   :eastBoundLongitude {:value eastBoundLongitude}
   :northBoundLatitude {:value northBoundLatitude}})

(defn geographic-element->bounds
  [{:keys [northBoundLatitude westBoundLongitude
           eastBoundLongitude southBoundLatitude]}]
  (js/google.maps.LatLngBounds.
    (js/google.maps.LatLng. southBoundLatitude westBoundLongitude)
    (js/google.maps.LatLng. northBoundLatitude eastBoundLongitude)))

;; BoxMapWidget

(defn init-map-props []
  {:map-options {:zoom             6
                 :center           (js/google.maps.LatLng. -42 147)
                 :rectangleOptions #js {:clickable true,
                                        :draggable false,
                                        :editable  false,
                                        :zIndex    1}}
   :extents     #{}
   :focus       nil})

(defn init-map
  [map-div {:keys [zoom center]}]
  (js/google.maps.Map.
    map-div
    #js {:zoom              zoom
         :center            center
         :streetViewControl false}))

(defn handle-focus-click [owner shape]
  (let [{:keys [on-change map-props]} (om/get-props owner)]
    (on-change (assoc map-props :focus (when shape (shape->extent shape))))))

(defn focus-on [owner extent]
  (doseq [shape (om/get-state owner :shapes)]
    (let [has-focus? (if extent
                       (.equals (extent->bounds extent)
                                (.getBounds shape))
                       false)]
      (.setDraggable shape has-focus?)
      (.setEditable shape has-focus?))))

(defn show-all [map shapes]
  (let [all-bounds (js/google.maps.LatLngBounds.)]
    (doseq [shape shapes]
      (.union all-bounds (.getBounds shape)))
    (.fitBounds map all-bounds)))

(defn remove-rect-by-extent [owner extent]
  (let [bounds (extent->bounds extent)
        pred #(.equals bounds (.getBounds %))]
    (om/update-state!
      owner :shapes
      (fn [shapes]
        (doseq [shape shapes]
          (when (pred shape)
            (.setMap shape nil)))
        (remove pred shapes)))))

(defn debounce
  ([f] (debounce f 250))
  ([f timeout]
   (let [id (atom nil)]
     (fn [& args]
       (when-not (nil? @id)
         (js/clearTimeout @id))
       (reset! id (js/setTimeout
                    (partial apply f args)
                    timeout))))))

(defn add-rect-by-extent [owner extent]
  (let [{:keys [map-props on-change disabled]} (om/get-props owner)
        {:keys [map-options]} map-props
        {:keys [map shapes]} (om/get-state owner)
        bounds (extent->bounds extent)
        pred #(.equals bounds (.getBounds %))]

    (when (empty? (filter pred shapes))
      (let [rect (js/google.maps.Rectangle. #js {:bounds bounds})]
        (.setOptions rect (:rectangleOptions map-options))
        (.setMap rect map)

        (when-not disabled
          (js/google.maps.event.addListener
            rect "click"
            #(if (.getEditable rect)
              (handle-focus-click owner nil)
              (handle-focus-click owner rect)))

          (js/google.maps.event.addListener
            rect "bounds_changed"
            (debounce
              #(on-change (assoc (om/get-props owner :map-props)
                            :focus (shape->extent rect)
                            :extents (shapes->extents (om/get-state owner :shapes)))))))

        (om/update-state! owner :shapes #(conj % rect))))))

(def box-buffer-pct 0.2)
(def box-bump-pct 0.1)

(defn new-from-bounds
  [[w s e n]]
  (let [lng-diff (* (- e w) box-buffer-pct)
        lat-diff (* (- s n) box-buffer-pct)]
    [(+ w lng-diff)
     (- s lat-diff)
     (- e lng-diff)
     (+ n lat-diff)]))

(defn bump
  [[w s e n]]
  (let [lng-diff (* (- e w) box-bump-pct)
        lat-diff (* (- s n) box-bump-pct)
        diff (min (js/Math.abs lng-diff) (js/Math.abs lat-diff))]
    [(+ w diff)
     (- s diff)
     (+ e diff)
     (- n diff)]))

(defn new-from-bounds-given-extents [bounds extents]
  (loop [extent (-> bounds new-from-bounds)]
    (if (contains? (set extents) extent)
      (recur (bump extent))
      extent)))

(defn handle-delete-click [owner]
  (let [{:keys [on-change map-props]} (om/get-props owner)
        {:keys [focus extents]} map-props
        bounds (extent->bounds focus)
        pred (fn [other-extent]
               (.equals bounds (extent->bounds other-extent)))]
    (on-change (assoc map-props
                 :focus nil
                 :extents (into #{} (remove pred extents))))))

(defn handle-add-click [owner]
  (let [{:keys [on-change map-props]} (om/get-props owner )
        new-extent (new-from-bounds-given-extents (:bounds map-props) (:extents map-props))]
    (on-change (-> map-props
                   (assoc :focus new-extent)
                   (update :extents conj new-extent)))))

(defn BoxMapWidget
  [{:keys [map-props on-change disabled ref] :as props} owner]
  (reify

    om/IDidMount
    (did-mount [_]
      (let [map-div (om/get-node owner "map")
            {:keys [map-options extents]} map-props
            map (init-map map-div map-options)]
        (om/set-state! owner :map map)
        (when-not disabled
          (js/google.maps.event.addDomListener
            map "idle" #(on-change (assoc (om/get-props owner :map-props)
                                     :zoom (.getZoom map)
                                     :center (.getCenter map)
                                     :bounds (bounds->extent (.getBounds map)))))
          (js/google.maps.event.addDomListener
            map "click" #(handle-focus-click owner nil))
          (let [controls (aget map "controls")
                control-pos (aget controls js/google.maps.ControlPosition.TOP_RIGHT)]
            (.push control-pos (om/get-node owner "map-area-controls"))))
        (doseq [extent extents]
          (add-rect-by-extent owner extent))
        (when (seq extents)
          (show-all map (om/get-state owner :shapes)))

        ; NOTE: This is new standard behaviour for react
        (if (ifn? ref)
          (ref owner))))

    om/IDidUpdate
    (did-update [_ prev-props prev-state]
      (let [[before after _] (diff (:map-props prev-props)
                                   (:map-props props))]
        (when-let [zoom (:zoom after)]
          (.setZoom (om/get-state owner :map) zoom))
        (when-let [center (:center after)]
          (.setCenter (om/get-state owner :map) center))
        (doseq [extent (:extents before)]
          (remove-rect-by-extent owner extent)
          (when (= extent (:focus map-props))
            (handle-focus-click owner nil)))
        (doseq [extent (:extents after)]
          (add-rect-by-extent owner extent))
        (when (contains? after :focus)
          (if-let [extent (:focus (:map-props props))]
            (focus-on owner extent)
            (focus-on owner nil)))
        (utils/on-change prev-state (om/get-state owner) [:shapes] #(focus-on owner (:focus (:map-props props))))))

    om/IRender
    (render [_]
      (html
        [:div
         (when-not disabled
           [:div {:ref   "map-area-controls"
                  :style {:margin-top   "9px"
                          :margin-right "9px"}}
            [:button.btn.btn-sm.btn-default
             {:on-click #(handle-add-click owner)
              :title "Add new coordinates"}
             [:span.glyphicon.glyphicon-pencil]]

            [:button.btn.btn-sm.btn-default
             {:disabled (not (:focus map-props))
              :title    (if (not (:focus map-props))
                          "No selection to delete"
                          "Delete selected coordinates")
              :on-click #(handle-delete-click owner)}
             [:span.glyphicon.glyphicon-trash]]])
         [:div.map {:ref "map"}]]))))



