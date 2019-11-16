(ns reax.core
  (:require ["react-native" :refer [NativeModules NativeEventEmitter]]))

(defn deserialize-event [event-str]
  (js->clj (js/JSON.parse event-str) :keywordize-keys true))

(defn reax-module
  [class-name result-f error-f]
  (if-let [module (aget NativeModules class-name)]
    (let [event-emitter  (NativeEventEmitter. module)
          dispatch-f     (aget module "dispatch")
          result-handler (comp result-f deserialize-event)
          error-handler  (comp error-f deserialize-event)]
      {:event-emitter   event-emitter
       :module          module
       :dispatch        (fn [id args]
                          (dispatch-f (js/JSON.stringify id)
                                      (js/JSON.stringify (clj->js args))))
       :result-listener (.addListener event-emitter (aget module "resultType") result-handler)
       :error-listener  (.addListener event-emitter (aget module "errorType") error-handler)})

    (throw (ex-info (str class-name " could not be imported") {:class-name class-name}))))

(defn start
  [{:keys [module] :as reax-module}]
  (.start module)
  reax-module)

(defn stop
  [{:keys [module result-listener error-listener] :as reax-module}]
  (when module
    (.stop module))
  (when result-listener
    (.remove result-listener))
  (when error-listener
    (.remove error-listener))
  reax-module)