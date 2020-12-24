(ns io.alda.api.main
  (:require [com.stuartsierra.component :as component]
            [io.alda.api.system         :as system]
            [io.pedestal.http           :as http]))

(defn -main
  [& [port*]]
  (let [port (Integer/parseInt port*)]
    (-> (system/system
          {:http-server {::http/port port}})
        component/start)))
