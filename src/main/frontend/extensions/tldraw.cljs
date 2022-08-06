(ns frontend.extensions.tldraw
  (:require ["/tldraw-logseq" :as TldrawLogseq]
            [frontend.components.block :as block]
            [frontend.components.page :as page]
            [frontend.handler.search :as search]
            [frontend.handler.whiteboard :as whiteboard-handler]
            [frontend.rum :as r]
            [frontend.state :as state]
            [frontend.util :as util]
            [goog.object :as gobj]
            [promesa.core :as p]
            [rum.core :as rum]))

(def tldraw (r/adapt-class (gobj/get TldrawLogseq "App")))

(def generate-preview (gobj/get TldrawLogseq "generateJSXFromApp"))

(rum/defc page-cp
  [props]
  (page/page {:page-name (gobj/get props "pageName") :whiteboard? true}))

(rum/defc block-cp
  [props]
  ((state/get-component :block/single-block) (uuid (gobj/get props "blockId"))))

(rum/defc breadcrumb
  [props]
  (block/breadcrumb {:preview? true} (state/get-current-repo) (uuid (gobj/get props "blockId")) nil))

(rum/defc page-name-link
  [props]
  (block/page-cp {:preview? true} {:block/name (gobj/get props "pageName")}))

(defn create-block-shape-by-id [e]
  (when-let [block (block/get-dragging-block)]
    (let [uuid (:block/uuid block)
          client-x (gobj/get e "clientX")
          client-y (gobj/get e "clientY")]
      (whiteboard-handler/add-new-block-shape! uuid client-x client-y))))

(defn search-handler [q]
  (p/let [results (search/search q)]
    (clj->js results)))

(rum/defc tldraw-app
  [name block-id]
  (let [data (whiteboard-handler/page-name->tldr! name block-id)
        [tln set-tln] (rum/use-state nil)]
    (rum/use-effect!
     (fn []
       (when (and tln name)
         (when-let [^js api (gobj/get tln "api")]
           (if (empty? block-id)
             (. api zoomToFit)
             (do (. api selectShapes block-id)
                 (. api zoomToSelection)))))
       nil) [name block-id tln])
    (when (and name (not-empty (gobj/get data "currentPageId")))
      [:div.draw.tldraw.whiteboard.relative.w-full.h-full
       {:style {:overscroll-behavior "none"}
        :on-blur #(state/set-block-component-editing-mode! false)
        :on-drop create-block-shape-by-id
        ;; wheel -> overscroll may cause browser navigation
        :on-wheel util/stop-propagation}

       (tldraw {:renderers {:Page page-cp
                            :Block block-cp
                            :Breadcrumb breadcrumb
                            :PageNameLink page-name-link}
                :handlers (clj->js {:search search-handler
                                    :addNewBlock (fn [content]
                                                   (str (whiteboard-handler/add-new-block! name content)))})
                :onMount (fn [app] (set-tln ^js app))
                :onPersist (fn [app]
                             (let [document (gobj/get app "serialized")]
                               (whiteboard-handler/transact-tldr! name document)))
                :model data})])))