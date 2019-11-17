(ns app-test
  (:require [app :as app]
            [cljs.test :refer-macros [deftest testing is]]
            [rehook.test :as rehook.test]
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

(deftest app-component--start-stop
  (let [scenes (rehook.test/init-scenes (test-ctx) identity clj->js app/app)
        scene1 (rehook.test/play-scenes! scenes)]

    (testing "Button should initially render 'Start'"
      (is (= "Start" (rehook.test/get-prop scene1 :start-stop :title))))

    (rehook.test/invoke-prop scene1 :start-stop :onPress {})

    (testing "After pressing button, button should render 'Stop'"
      (let [scene2 (rehook.test/play-scenes! scenes scene1)]
        (is (= "Stop" (rehook.test/get-prop scene2 :start-stop :title)))

        (rehook.test/invoke-prop scene2 :start-stop :onPress {})

        (testing "After pressing button again, button should render 'Start'"
          (let [scene3 (rehook.test/play-scenes! scenes scene1)]
            (is (= "Start" (rehook.test/get-prop scene3 :start-stop :title)))))))))