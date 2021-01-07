(ns io.alda.api.main
  (:require [com.stuartsierra.component :as component]
            [io.alda.api.system         :as system]
            [io.pedestal.http           :as http]))

(defn -main
  [& [port* metrics-env]]
  (let [port (Integer/parseInt port*)]
    (println "Serving app on port" port)
    (-> (system/system
          (merge
            {:http-server {::http/port port}}
            (when (seq metrics-env)
              {:datadog {:env metrics-env}})))
        component/start)))
