(ns app
  (:require [integrant.core :as ig]
            [reax.integrant]
            [reax.core :as reax]
            [rehook.dom :refer-macros [defui]]
            [rehook.dom.native :as rehook-dom]
            [rehook.core :as rehook]
            ["@react-native-community/slider" :as Slider]
            ["react" :as react]
            ["react-native" :refer [AppRegistry]]))

(defmethod ig/init-key :app/db [_ initial-state]
  (atom initial-state))

;; a very naive re-frame impl containing just subscriptions and events
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

(defn synth-result-handler
  [db event]
  (assoc db :synth/state event))

(defn synth-error-handler
  [db event]
  (js/console.warn "Synth error" (pr-str event))
  db)

(defn wrap-db [db handler]
  (fn [event]
    (swap! db handler event)))

(defmethod ig/init-key :app/db-handler [_ {:keys [db handler]}]
  (wrap-db db handler))

(defn synth-playing? [db _]
  (or (-> db :synth/state :started)
      false))

(def subscriptions
  {:synth/playing? synth-playing?})

(defn start-synth [db {:keys [synth]} _]
  (reax/dispatch synth "startSynth" {})
  db)

(defn stop-synth [db {:keys [synth]} _]
  (reax/dispatch synth "stopSynth" {})
  db)

(defn set-frequency [db {:keys [synth]} [frequency]]
  (reax/dispatch synth "setFrequency" {:frequency frequency})
  db)

(def events
  {:synth/start         start-synth
   :synth/stop          stop-synth
   :synth/set-frequency set-frequency})

(defn config []
  {;; The atom housing our application state.
   :app/db                                 {} ;; <--- initial app state

   ;; A naive re-frame impl using rehook
   :rehook/reframe                         {:db            (ig/ref :app/db) ;; <-- the database re-frame is operating on.
                                            :ctx           {:synth (ig/ref :reax/synth)} ;; <-- ctx passed into our events
                                            :subscriptions subscriptions ;; <-- all available subscriptions. call (dev/subscriptions) to see list
                                            :events        events} ;; <-- all available events. call (dev/events) to see list

   ;; The result handler for our synth
   [:app/db-handler :synth/result-handler] {:db      (ig/ref :app/db)
                                            :handler synth-result-handler}

   ;; The error handler for our synth
   [:app/db-handler :synth/error-handler]  {:db      (ig/ref :app/db)
                                            :handler synth-error-handler}

   ;; A reax module for our synth
   [:reax/module :reax/synth]              {:class-name     "Synth" ;; <-- the objc class of our reax module
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

;; Instead of passing the entire application context to our react components
;; we can pass our re-frame like interface instead!
;;
;; If we update our event or subscription maps, we can simply call (dev/reload)
(defn ig-system->app-ctx [system]
  {:dispatch   (partial dispatch system)
   :subscribe  (partial subscribe system)})

(defui app
  [{:keys [dispatch subscribe]} _]
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
         (js/setTimeout #(dispatch [:synth/stop]) duration))
       (constantly nil))
     [playing?])

    [:View {:style {:flex           1
                    :justifyContent "center"
                    :alignItems     "center"}}

     [:View {:style {}}
      [:Text {} (str "Frequency (" frequency ")")]
      [Slider {:rehook/id             :set-frequency
               :style                 {:width  200
                                       :height 40}
               :maximumValue          2000
               :minimumValue          400
               :value                 frequency
               :onValueChange         #(set-frequency (long %))
               :minimumTrackTintColor "red"
               :maximumTrackTintColor "green"}]]

     [:View {}
      [:Text {} (str "Duration (" duration " ms)")]
      [Slider {:rehook/id             :set-duration
               :style                 {:width  200
                                       :height 40}
               :maximumValue          4000
               :minimumValue          0
               :disabled              playing?
               :value                 duration
               :onValueChange         #(set-duration (long %))
               :minimumTrackTintColor "red"
               :maximumTrackTintColor "green"}]]

     [:Button {:title     (if playing?
                            "Stop"
                            "Start")
               :rehook/id :start-stop
               :onPress   #(if playing?
                             (dispatch [:synth/stop])
                             (dispatch [:synth/start]))}]]))

(defn dominant-component []
  (let [[n _] (rehook/use-atom reload-trigger)
        ctx   (ig-system->app-ctx @system)]
    (js/console.log (str "Re-rendering root component: " n))
    (rehook-dom/bootstrap ctx identity clj->js app)))

(defn ^:dev/after-load reload []
  (swap! reload-trigger inc))

(defn main []
  (reset! system (ig/init (config)))
  (.registerComponent AppRegistry "app" (constantly #(react/createElement dominant-component))))
