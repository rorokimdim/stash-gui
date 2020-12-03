(ns app.renderer.core
  (:require [reagent.dom :as rdom]
            [re-frame.core :as rf]
            [app.renderer.events]
            [app.renderer.subscriptions]
            [app.renderer.ui.core :as ui]
            ))

(enable-console-print!)

(defn start! []
  (rdom/render
   [ui/root-component]
   (js/document.getElementById ui/ROOT-ID)))
