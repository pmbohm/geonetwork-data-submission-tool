(ns metcalf.globals
  (:require [cljs.core.async :as async :refer [chan pub]]
            [condense.derived :refer [derived-atom!]]
            [metcalf.logic :refer [derived-state]]
            [condense.utils :refer [memoize-last]]))

(defonce app-state (derived-atom! (atom {}) (memoize-last derived-state)))
(def pub-chan (chan))
(def notif-chan (pub pub-chan :topic))
