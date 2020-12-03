(ns app.renderer.subscriptions
  (:require [re-frame.core :as rf]))

(rf/reg-sub
 :node-tree
 (fn [db ev]
   (:node-tree db)))

(rf/reg-sub
 :stash-file-path
 (fn [db ev]
   (:stash-file-path db)))

(rf/reg-sub
 :stash-file-name
 (fn [db ev]
   (:stash-file-name db)))

(rf/reg-sub
 :selected-node-id
 (fn [db ev]
   (:selected-node-id db)))

(rf/reg-sub
 :selected-node-value
 (fn [db ev]
   (:selected-node-value db)))
