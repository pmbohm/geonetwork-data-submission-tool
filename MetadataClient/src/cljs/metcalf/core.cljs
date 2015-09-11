(ns metcalf.core
  (:require [clojure.string :refer [blank?]]
            [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [cljs.core.match :refer-macros [match]]
            [sablono.core :as html :refer-macros [html]]
            [metcalf.routing :as router]
            [metcalf.views.page :refer [PageView]]
            [metcalf.globals :refer [app-state pub-chan notif-chan ref-path observe-path]]
            [metcalf.handlers :as handlers]
            condense.performance
            goog.userAgent))

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
                [:p.lead "We do support early versions of Internet Explorer."]
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
                       "Market share of IE8 and IE9 is <2% each world wide."]]]
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
