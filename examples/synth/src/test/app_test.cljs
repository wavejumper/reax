(ns app-test
  (:require [app :as app]
            [rehook.test :as rehook.test :refer-macros [defuitest is io initial-render next-render]]
            [integrant.core :as ig]))

(defmulti mock-reax-module (fn [id result-handler error-handler] id))

(defmethod mock-reax-module "Synth"
  [_ result-handler _]
  (fn [id _]
    (case id
      "setFrequency" nil
      "startSynth"   (result-handler {:started true})
      "stopSynth"    (result-handler {:started false}))))

(defmethod ig/init-key :reax.test/module
  [_ {:keys [class-name result-handler error-handler]}]
  {:dispatch (mock-reax-module class-name result-handler error-handler)})

(defn test-config []
  (let [config (app/config)]
    (-> config
        (dissoc [:reax/module :reax/synth])
        (assoc [:reax.test/module :reax/synth] (get config [:reax/module :reax/synth])))))

(defn test-ctx []
  (app/ig-system->app-ctx
   (ig/init (test-config))))

(defuitest app-component--start-stop
  [scenes {:system      test-ctx
           :system/args []
           :shutdown-f  identity
           :ctx-f       identity
           :props-f     identity
           :component   app/app}]

  (-> (initial-render scenes
        (is "Button should initially render 'Start'"
          (rehook.test/get-prop :start-stop :title))

        (io "Pressing start button"
          (rehook.test/invoke-prop :start-stop :onPress {})))

      (next-render
       (is "After pressing 'Start', button should render 'Stop'"
         (= "Stop" (rehook.test/get-prop :start-stop :title)))

       (io "Pressing stop button"
         (rehook.test/invoke-prop :start-stop :onPress {})))

      (next-render
       (is "After pressing button again, button should render 'Start'"
         (= "Start" (rehook.test/get-prop :start-stop :title))))))