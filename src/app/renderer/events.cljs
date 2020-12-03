(ns app.renderer.events
  (:require [re-frame.core :as rf]
            [app.renderer.stash :as stash]
            ["electron" :refer [ipcRenderer]]
            ["path" :refer [basename]]))

(rf/reg-event-db
 :initialise-db
 (fn [db [_ encryption-key stash-file-path create-stash-if-missing? on-success on-failure]]
   (stash/init
    encryption-key
    stash-file-path
    create-stash-if-missing?
    on-success
    on-failure)
   (assoc db :node-tree {})))

(rf/reg-event-db
 :set-node-tree
 (fn [db [_ tree]]
   (assoc db :node-tree tree)))

(rf/reg-event-db
 :set-node-tree-paths
 (fn [db [_ paths]]
   (assoc db :node-tree-paths paths)))

(rf/reg-event-db
 :set-stash-file-path
 (fn [db [_ stash-file-path]]
   (assoc db
          :stash-file-path stash-file-path
          :stash-file-name (basename stash-file-path))))

(rf/reg-event-db
 :set-selected-node
 (fn [db [_ node-id node-value]]
   (assoc db
          :selected-node-id node-id
          :selected-node-value node-value)))

(defn lookup-node-search-path [db node-id]
  (let [path (get (:node-tree-paths db) node-id)]
    (cons :node-tree (butlast (interleave path (repeat :children))))))

(defn lookup-node-value [db node-id]
  (let [search-path (lookup-node-search-path db node-id)]
    (get-in db (concat search-path [:value]))))

(rf/reg-event-db
 :set-selected-node-id
 (fn [db [_ node-id]]
   (assoc db
          :selected-node-id node-id
          :selected-node-value (lookup-node-value db node-id))))

(rf/reg-event-db
 :add-child-to-selected-node
 (fn [db]
   (let [node-id (:selected-node-id db)
         search-path (lookup-node-search-path db node-id)
         prompt-id "add-child"]
     (.send ipcRenderer "prompt"
            (clj->js {:title "Add new child"
                      :label "Enter a name to add:"
                      :type "input"
                      :inputAttrs {:type "text"
                                   :required true}
                      :alwaysOnTop true
                      :prompt-id prompt-id}))
     (.once ipcRenderer prompt-id
            (fn [e child-key]
              (if-not (nil? child-key)
                (stash/add-node!
                 (fn [child-id]
                   (rf/dispatch [:update-node-tree-add-node node-id child-id child-key ""]))
                 (fn [estr] (js/alert estr))
                 node-id
                 child-key
                 "")))))
   db))

(rf/reg-event-db
 :update-selected-node-value
 (fn [db [_ node-value]]
   (let [node-id (:selected-node-id db)
         search-path (lookup-node-search-path db node-id)]
     (stash/update-node-value!
      (constantly nil)
      (fn [estr] (js/alert estr))
      node-id node-value)
     (-> db
         (update-in search-path assoc :value node-value)
         (assoc :selected-node-value node-value)))))

(rf/reg-event-db
 :delete-selected-node
 (fn [db]
   (let [node-id (:selected-node-id db)
         search-path (lookup-node-search-path db node-id)
         node-key (get-in db (concat search-path [:key]))]
     (if (.confirm js/window (str "Are you sure you want to delete '" node-key "'?"))
       (do
         (stash/delete-node!
          (constantly nil)
          (fn [estr] (js/alert estr))
          node-id)
         (-> db
             (assoc :selected-node-id 0 :selected-node-value "")
             (update-in (butlast search-path) dissoc node-id)
             (update-in [:node-tree-paths] dissoc node-id)))
       db))))

(rf/reg-event-db
 :rename-selected-node
 (fn [db]
   (let [node-id (:selected-node-id db)
         search-path (lookup-node-search-path db node-id)
         node-key (get-in db (concat search-path [:key]))
         prompt-id "new-name-for-selected-node"]
     (.send ipcRenderer "prompt"
            (clj->js {:title "Rename"
                      :label (str "Enter a new name for '" node-key "':")
                      :type "input"
                      :inputAttrs {:type "text"
                                   :required true}
                      :alwaysOnTop true
                      :prompt-id prompt-id}))
     (.once ipcRenderer prompt-id
            (fn [e new-name]
              (when-not (nil? new-name)
                (stash/rename-node!
                 (fn [_]
                   (rf/dispatch [:update-node-tree-node-name node-id new-name]))
                 (fn [estr] (js/alert estr))
                 node-id
                 new-name))))
     db)))

(rf/reg-event-db
 :update-node-tree-node-name
 (fn [db [_ node-id new-name]]
   (let [search-path (lookup-node-search-path db node-id)]
     (update-in db search-path assoc :key new-name))))

(rf/reg-event-db
 :update-node-tree-add-node
 (fn [db [_ parent-id child-id child-key child-value]]
   (let [search-path (lookup-node-search-path db parent-id)
         parent-path (get (:node-tree-paths db) parent-id)]
     (-> db
         (update-in (concat search-path [:children]) assoc child-id
                    {:key child-key :value child-value :children {}})
         (assoc-in [:node-tree-paths child-id] (conj parent-path child-id))))))

(rf/reg-event-db
 :load-tree
 (fn [db _]
   (stash/load-tree!)
   db))

(defn open-stash-file [path create-stash-if-missing?]
  (let [prompt-id "encryption-key"]
    (.send ipcRenderer "prompt"
           (clj->js {:title "Encryption key"
                     :label (str "Enter encryption key for " (basename path) ":")
                     :type "input"
                     :inputAttrs {:type "password"
                                  :required true}
                     :alwaysOnTop true
                     :prompt-id prompt-id}))
    (.once ipcRenderer prompt-id
           (fn [e encryption-key]
             (if-not (nil? encryption-key)
               (rf/dispatch [:initialise-db
                             encryption-key
                             path
                             create-stash-if-missing?
                             (fn [] (.send ipcRenderer "configure-main-window"
                                           #js {:title path :width 800 :height 600}))
                             (fn [message]
                               (js/alert message))]))))))

(rf/reg-event-db
 :open-stash-file-from-path
 (fn [db [_ path create-stash-if-missing?]]
   (open-stash-file path create-stash-if-missing?)
   db))

(rf/reg-event-db
 :open-new-stash-file
 (fn [db _]
   (.send ipcRenderer "select-new-stash-file")
   (.once ipcRenderer
          "selected-new-stash-file"
          (fn prompt-ekey [e stash-file-path]
            (if-not (nil? stash-file-path)
              (open-stash-file stash-file-path true))))
   db))

(rf/reg-event-db
 :open-stash-file
 (fn [db _]
   (.send ipcRenderer "select-stash-file")
   (.once ipcRenderer
          "selected-stash-file"
          (fn prompt-ekey [e stash-file-path]
            (if-not (nil? stash-file-path)
              (open-stash-file stash-file-path false))))
   db))
