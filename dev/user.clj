(ns user
  (:require [com.stuartsierra.component :as component]
            [io.alda.api.system         :as system]
            [io.pedestal.http           :as http]))

(def system nil)

(defn init
  []
  (alter-var-root
    #'system
    (constantly (system/system
                  ;; Un-comment to enable sending metrics.
                  {#_#_:datadog     {:env "dev"}
                   :http-server {::http/port 8080}}))))

(defn start
  []
  (alter-var-root #'system component/start))

(defn stop
  []
  (alter-var-root #'system #(when % (component/stop %))))

(defn go
  []
  (init)
  (start))

(comment
  (go)
  (stop))

