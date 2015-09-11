(ns metcalf.views.page
  (:require metcalf.views.form))

(defmulti PageView (fn [page owner] (get page :name)) :default "404")

(defmulti PageTabView (fn [page owner] [(get page :name)
                                        (get page :tab :data-identification)]))