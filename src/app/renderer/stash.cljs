(ns app.renderer.stash
  (:require [re-frame.core :as rf]
            ["electron" :refer [ipcRenderer]]
            ))

(defn invoke
  "Sends an invoke request to stash process."
  [name on-success on-failure & args]
  (let [rid (str (random-uuid))]
    (.send ipcRenderer "call-stash"
           (clj->js {:id rid
                     :op "invoke"
                     :var (str "pod.rorokimdim.stash/" name)
                     :args (.stringify js/JSON (clj->js args))}))
    (.once ipcRenderer rid (fn [e response]
                             (let [r (js->clj response :keywordize-keys true)]
                               (if (contains? r :ex-message)
                                 (on-failure (:ex-message r))
                                 (on-success (js->clj (.parse js/JSON (:value r))))))))))

(defn add-node!
  "Asks stash-process to add a node."
  [on-success on-failure parent-id node-key node-value]
  (invoke "add" on-success on-failure parent-id node-key node-value))

(defn update-node-value!
  "Asks stash-process to update content of a node."
  [on-success on-failure node-id node-value]
  (invoke "update" on-success on-failure node-id node-value))

(defn rename-node!
  "Asks stash-process to rename a node."
  [on-success on-failure node-id new-name]
  (invoke "rename" on-success on-failure node-id new-name))

(defn delete-node!
  "Asks stash-process to delete a node."
  [on-success on-failure node-id]
  (invoke "delete" on-success on-failure node-id))

(defn stash-tree->stash-tree-on-id
  "Converts a stash-tree to an equivalent tree indexed on node-ids."
  [stree]
  (apply merge
         (for [[k [node-id node-value child-stree]] (into [] stree)]
           {node-id
            {:key (name k)
             :value node-value
             :children (if (empty? child-stree)
                         {}
                         (stash-tree->stash-tree-on-id child-stree))}})))

(defn stash-tree-on-id->paths
  "Gets paths to nodes in a tree-on-id data-structure.

  See stash-tree-on-id function."
  [tree-on-id]
  (let [paths (atom {})
        inner (fn f [t pid]
                (doseq [[nid {children :children}] (into [] t)]
                  (swap! paths assoc nid (conj (get @paths pid []) nid))
                  (if (seq children)
                    (f children nid))))]
    (inner tree-on-id 0)
    @paths))

(defn load-tree!
  "Loads data from stash process."
  []
  (invoke
   "tree"
   (fn [stree]
     (let [node-tree (stash-tree->stash-tree-on-id stree)
           paths (stash-tree-on-id->paths node-tree)]
       (rf/dispatch [:set-node-tree node-tree])
       (rf/dispatch [:set-node-tree-paths paths])))
   (fn [message]
     (js/alert message))))

(defn init
  "Initializes stash-process to use a stash-file."
  [encryption-key stash-file-path create-stash-if-missing? on-success on-failure]
  (invoke
   "init"
   (fn [ok?]
     (if ok?
       (do
         (load-tree!)
         (rf/dispatch [:set-stash-file-path stash-file-path])
         (on-success))
       (on-failure)))
   (fn [response]
     (js/alert (:ex-message response)))
   {:encryption-key encryption-key
    :stash-path stash-file-path
    :create-stash-if-missing create-stash-if-missing?}))
