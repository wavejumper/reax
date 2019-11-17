(ns app-test
  (require [app :as app]
           [test-utils :refer-macros [with-ig]]
           [clojure.test :refer-macros [deftest testing is]]
           [integrant.core :as ig]
           [clojure.set :as set]))

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
  (set/rename-keys
   (app/config)
   {[:reax/module :reax/synth] [:reax.test/module :reax/synth]}))

(deftest app-component


  )