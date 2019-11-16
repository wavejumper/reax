(ns app
  (:require [integrant.core :as ig]
            [reax.integrant]
            [rehook.dom :refer-macros [defui]]
            [rehook.dom.native :as rehook-dom]
            [rehook.core :as rehook]
            ["@react-native-community/slider" :as Slider]
            ["react" :as react]
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
  (assoc db :synth/state event))

(defn synth-error-handler
  [db event]
  (js/console.warn "Synth error" (pr-str event))
  db)

(defmethod ig/init-key :app/db-handler [_ {:keys [db handler]}]
  (wrap-db db handler))

(defn synth-playing? [db _]
  (or (-> db :synth/state :started)
      false))

(def subscriptions
  {:synth/playing? synth-playing?})

(def events
  {:synth/start (fn [db _ _]
                  db)

   :synth/stop (fn [db _ _]
                 db)

   :synth/set-frequency (fn [db {:keys [synth]} [frequency]]
                          (dispatch! synth "" {:frequench frequench})
                          db)})

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

(defonce reload-trigger
  (atom 0))

(defn subscribe [sys sub]
  (let [f (get-in sys [:rehook/reframe :subscribe])]
    (f sub)))

(defn dispatch [sys event]
  (let [f (get-in sys [:rehook/reframe :dispatch])]
    (f event)))

(defn ig-system->app-ctx [system]
  {:dispatch  (partial dispatch system)
   :subscribe (partial subscribe system)})

(defui app
  [{:keys [dispatch subscribe]} _ $]
  (let [playing?                  (subscribe [:synth/playing?])
        [frequency set-frequency] (rehook/use-state 400)
        [duration set-duration]   (rehook/use-state 250)]

    (rehook/use-effect
     (fn []
       (dispatch [:synth/set-frequency frequency])
       (constantly nil))
     [frequency])

    (rehook/use-effect
     (fn []
       (when playing?
         (js/setTimeout
          #(dispatch [:synth/stop])
          duration))
       #(dispatch [:synth/stop]))
     [playing?])

    ($ :View {:style {:flex           1
                      :justifyContent "center"
                      :alignItems     "center"}}

       ($ :View {:style {}}
          ($ :Text {} (str "Frequency (" frequency ")"))
          ($ Slider {:style                 {:width  200
                                             :height 40}
                     :maximumValue          4000
                     :minimumValue          0
                     :value                 frequency
                     :onValueChange         #(set-frequency (long %))
                     :minimumTrackTintColor "red"
                     :maximumTrackTintColor "green"}))

       ($ :View {}
          ($ :Text {} (str "Duration (" duration " ms)"))
          ($ Slider {:style                 {:width  200
                                             :height 40}
                     :maximumValue          4000
                     :minimumValue          0
                     :disabled              playing?
                     :value                 duration
                     :onValueChange         #(set-duration (long %))
                     :minimumTrackTintColor "red"
                     :maximumTrackTintColor "green"}))

       ($ :Button {:title (if playing?
                            "Stop"
                            "Start")
                   :onPress #(if playing?
                               (dispatch [:synth/stop])
                               (dispatch [:synth/start]))}))))

(defn dominant-component []
  (let [[n _] (rehook/use-atom reload-trigger)
        ctx   (ig-system->app-ctx @system)]
    (js/console.log (str "Re-rendering root component: " n))
    (rehook-dom/bootstrap ctx identity clj->js app)))

(defn ^:dev/after-load relaod []
  (swap! reload-trigger inc))

(defn main []
  (reset! system (ig/init (config)))
  (.registerComponent AppRegistry "app" (constantly #(react/createElement dominant-component))))