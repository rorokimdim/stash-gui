(ns app.main.core
  (:require [clojure.string :as str]
            ["electron" :refer [app dialog BrowserWindow Menu ipcMain]]
            ["child_process" :as child-process]
            ["electron-prompt" :as electron-prompt]
            ["bencode" :as bencode]))

(def main-window (atom nil))
(def stash-process (atom nil))
(def stash-busy? (atom false))
(def stash-request-queue (atom []))
(def force-quit (atom false))
(def context-menu-root (atom nil))
(def context-menu-selected-item (atom nil))
(def opened-with-file-path (atom nil))

(def menu-template-context-menu-root
  [{:label "Add"
    :click (fn [^js item ^js window]
             (.webContents.send window "add-child-to-selected-node"))}])

(def menu-template-context-menu-selected-item
  [{:label "Add"
    :click (fn [^js item ^js window]
             (.webContents.send window "add-child-to-selected-node"))}
   {:label "Rename"
    :click (fn [^js item ^js window]
             (.webContents.send window "rename-selected-node"))}
   {:label "Delete"
    :click (fn [^js item ^js window]
             (.webContents.send window "delete-selected-node"))}])

(def menu-template-app
  [{:label (.-name app)
    :submenu [{:label "New"
               :accelerator "CommandOrControl+N"
               :click (fn [^js item ^js window]
                        (.webContents.send window "open-new-stash-file"))}
              {:label "Open"
               :accelerator "CommandOrControl+O"
               :click (fn [^js item ^js window]
                        (.webContents.send window "open-stash-file"))}
              {:role "quit"}]}
   {:label "Edit"
    :submenu [{:role "cut"}
              {:role "copy"}
              {:role "paste"}
              {:role "selectAll"}]}
   {:label "View"
    :submenu [{:role "reload"}
              {:role "toggleDevTools"}
              {:role "togglefullscreen"}]}
   {:label "Window"
    :submenu [{:role "minimize"}
              {:role "zoom"}]}])

(defn mac-platform? []
(= js/process.platform "darwin"))

(defn bytes->string [data]
  (.toString (js/Buffer.from data)))

(defn process-stash-request-queue []
  (when (and (not @stash-busy?)
             (not-empty @stash-request-queue))
    (reset! stash-busy? true)
    (let [[message cb] (first @stash-request-queue)
          ^js sp @stash-process
          ^js stdin (. sp -stdin)
          ^js stdout (. sp -stdout)]
      (swap! stash-request-queue rest)
      (.write stdin (bencode/encode (clj->js message)))
      (.once stdout "data" (fn handler [data]
                             (try
                               (let [decoded (bencode/decode data "utf-8")]
                                 (cb decoded)
                                 (reset! stash-busy? false))
                               (catch :default e
                                 (when (str/starts-with? (.-message e) "Invalid data")
                                   (.once stdout "data" (fn [more-data]
                                                          (handler (clj->js (concat data more-data)))))))))))))

(defn start-stash-process []
  (let [env (js->clj (js/Object.assign #js {} js/process.env))
        ^js sp (child-process/spawn
                (str js/__dirname "/bin/stash")
                (clj->js [])
                (clj->js {"env" (assoc env "BABASHKA_POD" "true")}))]
    (reset! stash-process sp)
    (js/setInterval process-stash-request-queue 1)))

(defn call-stash-process
  ([message] (call-stash-process message (constantly nil)))
  ([message cb]
   (swap! stash-request-queue conj [message cb])))

(defn shutdown-stash-process []
  (call-stash-process {:op "shutdown"} (fn [_] (js/console.log "Goodbye!"))))

(defn icon-path []
  (if (mac-platform?)
    (str js/__dirname "/public/img/icon.icns")
    (str js/__dirname "/public/img/icon.png")))

(defn init-browser []
  (reset! main-window (BrowserWindow.
                       (clj->js {:width 400
                                 :height 300
                                 :show false
                                 :icon (icon-path)
                                 :webPreferences {:nodeIntegration true
                                                  :contextIsolation false}})))
  (let [^js w @main-window]
    (.on w "close" (fn [e]
                     (if (and (mac-platform?) (not @force-quit))
                       (do
                         (.preventDefault e)
                         (.hide w))
                       (shutdown-stash-process))))
    (.loadURL w (str "file://" js/__dirname "/public/index.html"))
    (.on w "ready-to-show" (fn []
                             (.show w)
                             (if-let [path @opened-with-file-path]
                               (.webContents.send w "open-stash-file-from-path" path false))))
    (.on w "closed" #(reset! main-window nil))))

(defn quit-app []
  (shutdown-stash-process)
  (.quit app))

(.on ipcMain "call-stash"
     (fn [^js e message]
       (let [msg (js->clj message :keywordize-keys true)
             rid (:id msg)
             cb (fn [data]
                  (.reply e rid data))]
         (call-stash-process msg cb))))

(.on ipcMain "prompt"
     (fn [^js e options]
       (-> (electron-prompt options @main-window)
           (.then (fn [value]
                    (.reply e (aget options "prompt-id") value))))))

(.on ipcMain "select-stash-file"
     (fn [^js e]
       (let [w (.getFocusedWindow BrowserWindow)
             selected-paths (.showOpenDialogSync
                             dialog
                             w
                             (clj->js {:title "Open Stash file"
                                       :properties ["openFile"]
                                       :filters [{:name "Stash file" :extensions ["stash"]}]}))]
         (if-not (undefined? selected-paths)
           (let [path (first selected-paths)]
             (.reply e "selected-stash-file" path))
           (.reply e "selected-stash-file" (clj->js nil))))))

(.on ipcMain "configure-main-window"
     (fn [^js e options]
       (let [w ^js @main-window
             opts (js->clj options :keywordize-keys true)]
         (.setTitle w (get opts :title "Stash"))
         (.setContentSize w (get opts :width 400) (get opts :height 300))
         (if (get opts :center true) (.center w)))))

(.on ipcMain "select-new-stash-file"
     (fn [^js e]
       (let [w (.getFocusedWindow BrowserWindow)
             selected-path (.showSaveDialogSync
                            dialog
                            w
                            #js {:title "New Stash file"
                                 :properties ["createDirectory"]
                                 })]
         (if-not (undefined? selected-path)
           (let [path (if-not (str/ends-with? selected-path ".stash")
                        (str selected-path ".stash")
                        selected-path)]
             (.setTitle w path)
             (.reply e "selected-new-stash-file" path))
           (.reply e "selected-new-stash-file" (clj->js nil))))))

(.on ipcMain "show-main-window" (fn [_] (.show ^js @main-window)))
(.on ipcMain "hide-main-window" (fn [_] (.hide ^js @main-window)))
(.on ipcMain "quit-app" (fn [_] (quit-app)))

(.on ipcMain "show-context-menu/selected-item"
     (fn [_]
       (let [^js m @context-menu-selected-item]
         (.popup m))))

(.on ipcMain "show-context-menu/root"
     (fn [_]
       (let [^js m @context-menu-root]
         (.popup m))))

(defn main []
  (.setName app "Stash")

  ;; For opening stash files from command-line argument
  (when (= (alength js/process.argv) 3)
    (let [path (aget js/process.argv 2)]
      (reset! opened-with-file-path path)))

  ;; For opening stash file using open-with on mac
  (.on app "open-file" (fn [e path] (reset! opened-with-file-path path)))

  (start-stash-process)

  (reset! context-menu-root
          (.buildFromTemplate Menu (clj->js menu-template-context-menu-root)))
  (reset! context-menu-selected-item
          (.buildFromTemplate Menu (clj->js menu-template-context-menu-selected-item)))

  (.setApplicationMenu Menu (.buildFromTemplate Menu (clj->js menu-template-app)))
  (.on app "window-all-closed" (fn [] (when-not (mac-platform?) (quit-app))))
  (.on app "activate" (fn [] (.show ^js @main-window)))
  (.on app "before-quit" (fn [] (reset! force-quit true)))
  (.on app "ready" init-browser))
