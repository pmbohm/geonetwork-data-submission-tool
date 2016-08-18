(ns metcalf.widget.select
  (:require [om.core :as om :include-macros true]
            [sablono.core :as html :refer-macros [html]]
            cljs.pprint
            cljsjs.react-select
            cljsjs.react-virtualized
            [clojure.string :as string]
            [metcalf.utils :refer [js-lookup js-lookup! clj->js*]]))

(def ReactSelect* (js/React.createFactory js/Select))
(def ReactSelectAsync* (js/React.createFactory js/Select.Async))
(def AutoSizer* (js/React.createFactory js/ReactVirtualized.AutoSizer))
(def VirtualScroll* (js/React.createFactory js/ReactVirtualized.VirtualScroll))

(defn normalize-props [props]
  (clj->js* (update props :options clj->js* 1) 1))

(defn ReactSelect [props]
  (ReactSelect* (normalize-props props)))

(defn ReactSelectAsync [props]
  (ReactSelectAsync* (normalize-props props)))

(defn AutoSizer [props]
  (AutoSizer* (normalize-props props)))

(defn VirtualScroll [props]
  (VirtualScroll* (normalize-props props)))

(defn render-menu [owner raw-args]
  (let [{:keys [focusedOption focusOption labelKey options selectValue valueArray]} (js-lookup raw-args)
        {:keys [maxHeight optionHeight optionRenderer]} (om/get-props owner)
        focusedOptionIndex (.indexOf options focusedOption)
        n-options (count options)
        height (js/Math.min maxHeight (* optionHeight n-options))

        default-option-renderer
        (fn [{:keys [focusedOption focusOption labelKey option selectValue]}]
          (let [optionHeight (om/get-props owner :optionHeight)
                className (if (identical? option focusedOption)
                            "VirtualizedSelectOption VirtualizedSelectFocusedOption"
                            "VirtualizedSelectOption")]
            (html [:div {:class         className
                         :on-click      #(selectValue option)
                         :on-mouse-over #(focusOption option)
                         :style         {:height optionHeight}}
                   (aget option labelKey)])))

        innerRowRenderer (or optionRenderer default-option-renderer)

        wrapped-row-renderer
        (fn [args]
          (let [idx (aget args "index")
                option (aget options idx)]
            (innerRowRenderer
              (js-lookup! #js {:focusedOption      focusedOption
                               :focusedOptionIndex focusedOptionIndex
                               :focusOption        focusOption
                               :labelKey           labelKey
                               :option             option
                               :options            options
                               :optionHeight       optionHeight
                               :selectValue        selectValue
                               :valueArray         valueArray}))))]

    (AutoSizer
      {:disableHeight true
       :children      (fn [args]
                        (let [{:keys [width]} (js-lookup args)]
                          (VirtualScroll {:ref              "VirtualScroll"
                                          :className        "VirtualSelectGrid"
                                          :height           height
                                          :overscanRowCount 0
                                          :rowCount         n-options
                                          :rowHeight        optionHeight
                                          :rowRenderer      wrapped-row-renderer
                                          :scrollToIndex    focusedOptionIndex
                                          :width            width})))})))

(defn VirtualizedSelect [props owner]
  (om/component
    (ReactSelect (merge {:clearable  true
                         :searchable true
                         :menuRenderer #(render-menu owner %)
                         :menuStyle  #js {:overflow "hidden"}}
                        props))))