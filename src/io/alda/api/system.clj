(ns io.alda.api.system
  (:require [com.stuartsierra.component :as component]
            [io.alda.api.releases       :as releases]
            [io.alda.api.server         :as server]))

(defn system
  [config]
  (component/system-using
    (component/map->SystemMap
      {:http-server (server/map->Server  (:http-server config))
       :cache       (releases/map->Cache (:cache config))})
    {:http-server [:cache]}))
