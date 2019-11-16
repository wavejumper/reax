(ns app
  (:require [integrant.core :as ig]
            [reax.integrant]
            [rehook.dom :refer-macros [defui]]
            [rehook.dom.native :as rehook-dom]
            [rehook.core :as rehook]
            ["react-native" :refer [AppRegistry]]))

(defmethod ig/init-key :app/db [_ initial-state]
  (atom initial-state))

(defmethod ig/init-key :rehook/reframe
  [_ {:keys [db ctx subscriptions events]}]
  {:subscriptions subscriptions
   :events events
   :subscribe (fn [[id & args]]
                (if-let [subscription (get subscriptions id)]
                  (first (rehook/use-atom-fn db #(subscription % args) (constantly nil)))
                  (js/console.warn (str "No subscription found for id " id))))

   :dispatch (fn [[id & args]]
               (if-let [handler (get events id)]
                 (swap! db #(handler % ctx args))
                 (js/console.warn (str "No event handler found for id " id))))})

(defn wrap-db [db handler]
  (fn [event]
    (swap! db handler event)))

(defn synth-result-handler
  [db event]

  )

(defmethod ig/init-key :app/db-handler [_ {:keys [db handler]}]
  (wrap-db db handler))

(defn synth-error-handler
  [db event]

  )

(def subscriptions
  {})

(def events
  {})

(defn config []
  {:app/db {}
   [:app/db-handler :synth/result-handler] {:db (ig/ref :app/db)
                                              :handler synth-result-handler}
   [:app/db-handler :synth/error-handler] {:db (ig/ref :app/db)
                                             :handler synth-error-handler}
   :rehook/reframe {:db  (ig/ref :app/db)
                    :ctx {:synth (ig/ref :reax/synth)}
                    :subscriptions subscriptions
                    :events events}
   [:reax/module :reax/synth] {:class-name     "Synth"
                                 :result-handler (ig/ref :synth/result-handler)
                                 :error-handler  (ig/ref :synth/error-handler)}})

(defonce system
  (atom {}))

(defn subscribe [sys sub]
  (let [f (get-in sys [:rehook/reframe :subscribe])]
    (f sub)))

(defn dispatch [sys event]
  (let [f (get-in sys [:rehook/reframe :dispatch])]
    (f event)))

(defn ig-system->app-ctx [system]
  {:dispatch  (partial dispatch system)
   :subscribe (partial subscribe system)})

(defui app [_ _ $]
  ($ :Text {} "Hello world!"))

(defn main []
  (let [next-system        (ig/init (config))
        component-provider (rehook-dom/component-provider (ig-system->app-ctx next-system) app)]
    (reset! system next-system)
    (.registerComponent AppRegistry "app" component-provider)))