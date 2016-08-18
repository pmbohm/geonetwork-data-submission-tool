 (ns metcalf.views-spec
    (:require [metcalf.views :as views]
              [cljs.spec :as s]))

(s/fdef views/format-columns
   :args (s/cat :flex (s/nilable coll?)
                :fixed (s/nilable coll?)
                :columns (s/nilable coll?)))