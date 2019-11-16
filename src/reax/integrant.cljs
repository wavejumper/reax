(ns reax.integrant
  (:require [reax.core :as reax]
            [integrant.core :as ig]))

(defmethod ig/init-key :reax/module
  [_ {:keys [class-name result-handler error-handler]}]
  (-> (reax/reax-module class-name result-handler error-handler)
      (reax/start)))

(defmethod ig/halt-key! :reax/module
  [_ reax-module]
  (reax/stop reax-module))