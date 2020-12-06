(ns io.alda.releases-api.system
  (:require [com.stuartsierra.component  :as component]
            [io.alda.releases-api.server :as server]))

(defn system
  [config]
  (component/system-using
    (component/map->SystemMap
      {:http-server (server/map->Server (:http-server config))})
    {:http-server {}}))
