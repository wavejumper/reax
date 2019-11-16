(ns dev
  (:require [app :as app]
            [integrant.core :as ig]))

(defn reload []
  (ig/halt! @app/system)
  (reset! app/system (ig/init (app/config))))

(defn subscriptions []
  (some-> app/system deref :rehook/reframe :subscriptions keys))

(defn events []
  (some-> app/system deref :rehook/reframe :events keys))

(defn app-state []
  (some-> app/system deref :app/db deref))