(ns app.renderer.ui.core
  (:require
   [clojure.string :as str]
   [reagent.core :as reagent]
   [re-frame.core :as rf]
   [dommy.core :as dommy]
   ["electron" :refer [ipcRenderer]]))

(def ROOT-ID "app-container")

(defn open-ancestor-nodes
  "Opens all ancestor nodes to given element."
  [element]
  (doseq [a (->> (dommy/ancestors element)
                 (filter #(= (dommy/class %) "collapsible-node")))]
    (dommy/set-attr! a :open "open")))

(defn select-node
  "Selects a node, opens it and open any of its ancestor nodes."
  [element]
  (when element
    (open-ancestor-nodes element)
    (.scrollIntoView element)
    (.focus element)
    (.click element)
    (.focus (dommy/sel1 ".search-input"))))

(defn search-node
  "Searches for the next node containing a search-text."
  ([search-text selected-node-id] (search-node search-text selected-node-id false))
  ([search-text selected-node-id reverse?]
   (let [cmp (if reverse? > <)
         matching-nodes (->> (dommy/sel [:.node])
                             (filter #(re-find (re-pattern (str "\\b" search-text "\\b")) (dommy/text %)))
                             (sort (fn [a b] (compare (js/parseInt (dommy/attr a :data-node-id))
                                                      (js/parseInt (dommy/attr b :data-node-id)))))
                             (filter #(cmp selected-node-id (dommy/attr % :data-node-id))))]
     (if (seq matching-nodes)
       (first matching-nodes)
       (when-not reverse? (search-node search-text selected-node-id true))))))

(defn tree-component
  "Builds tree component."
  [node-id node-name children]
  (let [selected-node-id @(rf/subscribe [:selected-node-id])
        tree @(rf/subscribe [:node-tree])]
    [:details (if (= node-id 0) {:open "open"
                                 :class "collapsible-node"}
                  {:class "collapsible-node"})
     [:summary.node {:data-node-id node-id
                     :class (if (= node-id selected-node-id) "selected")
                     :on-click (fn [] (rf/dispatch [:set-selected-node-id node-id]))
                     :on-context-menu (fn [_]
                                        (rf/dispatch [:set-selected-node-id node-id])
                                        (if (= node-id 0)
                                          (.send ipcRenderer "show-context-menu/root")
                                          (.send ipcRenderer "show-context-menu/selected-item")))}
      node-name]
     [:ul {:class "list-group"}
      (for [[nid {k :key grand-children :children}] children]
        (if (empty? grand-children)
          ^{:key nid} [:li.node {:class (if (= nid selected-node-id)
                                          "list-group-item selected"
                                          "list-group-item")
                                 :data-node-id nid
                                 :on-context-menu (fn [_]
                                                    (rf/dispatch [:set-selected-node-id nid])
                                                    (.send ipcRenderer "show-context-menu/selected-item"))
                                 :on-click (fn [_] (rf/dispatch [:set-selected-node-id nid]))}
                       (name k)]
          ^{:key nid} [tree-component nid (name k) grand-children]))]]))

(defn no-open-file-component
  "Component to show when a stash file hasn't been opened."
  []
  [:div {:class "no-open-file"}
   [:img {:src "img/logo.png"}]
   [:button.btn.btn-default {:on-click (fn [] (rf/dispatch [:open-new-stash-file]))}
    "Create a new file"]
   [:button.btn.btn-default {:on-click (fn [] (rf/dispatch [:open-stash-file]))}
    "Open an existing file"]])

(defn root-component
  "Builds root component."
  []
  (let [tree @(rf/subscribe [:node-tree])
        stash-file-path @(rf/subscribe [:stash-file-path])
        stash-file-name @(rf/subscribe [:stash-file-name])
        selected-node-id @(rf/subscribe [:selected-node-id])
        selected-node-value @(rf/subscribe [:selected-node-value])]
    (if (nil? tree)
      [:div.window
       [:div.window-content
        [no-open-file-component]]]
      [:div.window
       [:div.window-content
        [:div.pane-group
         [:div.pane.pane-sm.sidebar [tree-component 0 stash-file-name (get-in tree [0 :children])]]
         [:div.pane
          [:textarea {:value selected-node-value
                      :class "editor"
                      :on-change (fn [event]
                                   (if-not (= selected-node-id 0)
                                     (rf/dispatch [:update-selected-node-value
                                                   (-> event .-target .-value)])))}]]
         ]]
       [:footer.toolbar.toolbar-footer
        [:div.toolbar-actions
         [:input.form-control.search-input.pull-left
          {:type "text"
           :placeholder "Search"
           :on-key-press (fn [e]
                           (if (= 13 (.-charCode e))
                             (let [search-text (-> e .-target .-value)
                                   found-node (search-node search-text selected-node-id)]
                               (select-node found-node))))}]]]])))

;;
;; These provide a way for the main process to call the renderer process to do some gui related stuff.
;;
(.on ipcRenderer "add-child-to-selected-node" (fn [_] (rf/dispatch [:add-child-to-selected-node])))
(.on ipcRenderer "delete-selected-node" (fn [_] (rf/dispatch [:delete-selected-node])))
(.on ipcRenderer "rename-selected-node" (fn [_] (rf/dispatch [:rename-selected-node])))
(.on ipcRenderer "open-new-stash-file" (fn [_] (rf/dispatch [:open-new-stash-file])))
(.on ipcRenderer "open-stash-file" (fn [_] (rf/dispatch [:open-stash-file])))
(.on ipcRenderer "open-stash-file-from-path"
     (fn [e path create-stash-if-missing?]
       (rf/dispatch [:open-stash-file-from-path path create-stash-if-missing?])))
