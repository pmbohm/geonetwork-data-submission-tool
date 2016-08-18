(ns metcalf.widget.tree
  (:require [om.core :as om :include-macros true]
            [sablono.core :as html :refer-macros [html]]

            [ajax.core :as ajax]
            [metcalf.utils :refer [log error warn info js-lookup]]
            [metcalf.globals :refer [ref-path observe-path]]
            [metcalf.widget.select :refer [AutoSizer VirtualScroll]]))

(defn parent-paths [path]
  (take-while seq (rest (iterate drop-last path))))

(defn default-render-option
  [{:keys [option is-expandable is-expanded toggle-option select-option]} owner]
  (let [{:keys [path label]} option]
    (html [:div.tree-option
           {:style {:margin-left (str (count path) "em")}}
           (if is-expandable
             [(if is-expanded
                :span.glyphicon.glyphicon-triangle-bottom
                :span.glyphicon.glyphicon-triangle-right)
              {:style    {:cursor "pointer"}
               :on-click #(toggle-option option)}]
             [:span.glyphicon.glyphicon-file {:style {:visibility "hidden"}}])
           [:span.tree-label
            {:style    {:margin-left "0.2em"}
             :on-click #(select-option option)}] label])))

(defn Tree
  [{:keys [options on-select render-option]
    :or   {render-option default-render-option}} owner]
  (reify
    om/IInitState
    (init-state [_]
      {:expanded #{}})
    om/IRenderState
    (render-state [_ {:keys [expanded] :as state}]
      (let [toggle-option (fn [{:keys [path]}]
                            (if (contains? expanded path)
                              (om/set-state! owner :expanded (disj expanded path))
                              (om/set-state! owner :expanded (conj expanded path))))
            expandable (into #{} (map (comp drop-last :path) options))
            visible? #(every? expanded (parent-paths (:path %)))
            visible-options (filter visible? options)]
        (html [:div
               [:h2 "Tree"]
               (for [{:keys [path] :as option} visible-options]
                 (render-option
                   {:option        option
                    :toggle-option toggle-option
                    :select-option on-select
                    :is-expandable (contains? expandable path)
                    :is-expanded   (contains? expanded path)}))])))))

(defn add-nodes
  [api nodes]
  (let [api-path (om/path api)]
    (swap! metcalf.globals/app-db
           update-in api-path
           update :options
           into nodes)
    (-> @metcalf.globals/app-db (get-in api-path) :options)))

(defn matches
  ([m1]
   (partial matches m1))
  ([m1 m2]
   (reduce-kv
     (fn [t k v] (and t (= (get m2 k ::not-found) v)))
     true
     m1)))

(defn decendant-count
  [{:keys [lft rgt]}]
  (- rgt lft 1))

(defn node-parent?
  ([{:keys [lft rgt tree_id]} parent]
   (and (= (:tree_id parent) tree_id)
        (< (:lft parent) lft)
        (> (:rgt parent) rgt))))

(defn node-parents
  [nodes node]
  (->> nodes
       (filter (partial node-parent? node))
       (sort-by :lft)))

(defn node-decendant?
  "docstring"
  ([{:keys [lft rgt tree_id]} node]
   (and (= (:tree_id node) tree_id)
        (> (:lft node) lft)
        (< (:rgt node) rgt))))

(defn node-decendants
  [nodes parent]
  (filter (partial node-decendant? parent) nodes))

(defn node-child?
  "docstring"
  ([{:keys [lft rgt depth tree_id]} node]
   (and (= (:tree_id node) tree_id)
        (= (:depth node) (inc depth))
        (> (:lft node) lft)
        (< (:rgt node) rgt))))

(defn node-children
  [nodes parent]
  (filter (partial node-child? parent) nodes))

(defn root-nodes
  [{:keys [options]}]
  (filter (matches {"depth" 1}) options))

(defn fetch-nodes-from-api
  [{:keys [uri] :as api} params]
  (ajax/GET uri
            {:params          params
             :handler         (fn [{:keys [results]}] (add-nodes api results))
             :error-handler   error
             :response-format :json
             :keywords?       true}))

(defn get-children
  [{:keys [uri options] :as api} {:keys [lft rgt depth tree_id children_count] :as node}]
  (let [dn (decendant-count node)
        m (count (node-decendants options node))]
    (cond

      (= children_count
         (count (node-children options node)))
      (log :get-children "we have all children, nothing to do")

      (and (not= dn m) (< dn 1000))
      (do (log :get-children "We need to fetch children but it's easy to just fetch all decendants from here")
          (fetch-nodes-from-api api {:tree_id tree_id :min_lft lft :max_rgt rgt}))

      :else
      (do (log :get-children "Just fetching direct children")
          (fetch-nodes-from-api api {:tree_id tree_id :min_lft lft :max_rgt rgt :depth (inc depth)})))))

(defn is-selected?
  "This helper allows for value to appear in tree multiple times"
  [value-key value option]
  (= (get value value-key)
     (get option value-key)))

(defn get-all-parents
  "This helper allows for value to appear in tree multiple times"
  [value-key value options]
  (->> options
       (filter #(is-selected? value-key value %))
       (mapcat #(node-parents options %))))

(defn BaseTermTree
  [{:keys [value options value-key on-select render-menu render-option]} owner]
  (reify
    om/IInitState
    (init-state [_]
      {:expanded (set (get-all-parents value-key value options))})
    om/IRenderState
    (render-state [_ {:keys [expanded]}]
      (let [toggle-option (fn [option]
                            (if (contains? expanded option)
                              (om/set-state! owner :expanded (disj expanded option))
                              (om/set-state! owner :expanded (conj expanded option))))
            visible? #(every? expanded (node-parents options %))
            visible-options (->> options
                                 (sort-by (juxt :tree_id :lft))
                                 (filter visible?))]
        (render-menu
          {:value value
           :value-key value-key
           :options options
           :expanded expanded
           :select-option on-select
           :toggle-option toggle-option
           :visible-options visible-options})))))

(defn BaseTermList
  [{:keys [value options value-key on-select render-menu render-option]} owner]
  (reify
    om/IInitState
    (init-state [_]
      {:expanded (set (get-all-parents value-key value options))})
    om/IRenderState
    (render-state [_ {:keys [expanded]}]
      (let [toggle-option (fn [option]
                            (if (contains? expanded option)
                              (om/set-state! owner :expanded (disj expanded option))
                              (om/set-state! owner :expanded (conj expanded option))))
            visible? #(every? expanded (node-parents options %))
            visible-options (filter visible? options)]
        (render-menu
          {:value value
           :value-key value-key
           :options options
           :expanded expanded
           :select-option on-select
           :toggle-option toggle-option
           :visible-options visible-options})))))

(defn render-tree-term-option
  [{:keys [option is-selected is-expandable is-expanded toggle-option select-option]} owner]
  (let [{:keys [depth Name children_count is_selectable lft rgt]} option]
    (html [:div.tree-term-item
           {:class [(when is-selected "active")
                    (when is_selectable "is-selectable")
                    (when is-expanded "is-expanded")]}

           (cond

             (and is-expandable is-expanded)
             [:div.expander
              {:on-click #(toggle-option option)
               :style {:margin-left (str (- depth 1) "em")}}
              [:span.glyphicon.glyphicon-triangle-bottom]]

             (and is-expandable (not is-expanded))
             [:div.expander
              {:on-click #(toggle-option option)
               :style {:margin-left (str (- depth 1) "em")}}
              [:div.glyphicon.glyphicon-triangle-right]]

             :else
             [:div.expander
              {:style {:margin-left (str (- depth 1) "em")}}
              [:div.glyphicon.glyphicon-file
               {:style {:visibility "hidden"}}]])

           [:div.term
            {:on-click (when (:is_selectable option) #(select-option option))}
            [:span Name]]

           [:div.children_count
            (when (and is-expandable)
              [:span.badge (/ (- rgt lft 1) 2)])]])))

(defn render-tree-term-menu
  [{:keys [value value-key options expanded select-option toggle-option visible-options]} owner]
  (html
    [:div.pre-scrollable
     [:div
      [:div.tree-term
       (for [option visible-options]
         (render-tree-term-option
           {:option        option
            :is-selected  (is-selected? value-key value option)
            :toggle-option toggle-option
            :select-option select-option
            :is-expandable (> (decendant-count option) 0)
            :is-expanded   (contains? expanded option)}
           owner))]]]))

(defn TermTree
  [props owner]
  (om/component
    (om/build BaseTermTree (assoc props
                             :render-menu render-tree-term-menu
                             :render-option render-tree-term-option))))

(defn TermList
  [props owner]
  (om/component
    (om/build BaseTermList (assoc props
                             :render-menu render-tree-term-menu
                             :render-option render-tree-term-option))))

