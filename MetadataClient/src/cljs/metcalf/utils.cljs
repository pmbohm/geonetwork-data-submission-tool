(ns metcalf.utils)

(defn deep-merge
  "Recursively merges maps. If keys are not maps, the last value wins."
  [& vals]
  (if (every? map? vals)
    (apply merge-with deep-merge vals)
    (last vals)))

(defn reverse-or
  "Reverse OR:
   use it to update source value only if destination value is not falsey."
  [a b]
  (or b a))