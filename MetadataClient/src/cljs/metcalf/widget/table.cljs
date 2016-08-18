(ns metcalf.widget.table
  (:require [metcalf.utils :refer [clj->js*]]
            [cljsjs.fixed-data-table]))

(def Table* (js/React.createFactory js/FixedDataTable.Table))
(def Column* (js/React.createFactory js/FixedDataTable.Column))

(defn Table [props & children]
  (-> props (assoc :children children) (clj->js* 1) (Table*)))

(defn Column [props]
  (-> props (clj->js* 1) (Column*)))