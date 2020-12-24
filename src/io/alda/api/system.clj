(ns io.alda.api.system
  (:require [cognician.dogstatsd        :as dogstatsd]
            [com.stuartsierra.component :as component]
            [io.alda.api.releases       :as releases]
            [io.alda.api.server         :as server]
            [io.pedestal.log            :as log]))

(defn system
  [{:keys [datadog http-server cache] :as _config}]
  ;; HACK: Not making this a part of the system goes against the grain of the
  ;; component workflow, but I think it makes sense based on the global
  ;; state-oriented nature of the dogstatsd-clj library.
  ;;
  ;; I briefly tried making a Metrics component that takes configuration (just
  ;; :env, either "dev" or "prod") and uses dogstatsd-clj under the hood, but it
  ;; felt like too much ceremony to accommodate a library that relies heavily on
  ;; global state.
  ;;
  ;; It just so happens that I'm happy with global state in this case; the only
  ;; global tag I'm using is "env", which is either "dev" or "prod", and I don't
  ;; see the need to have multiple systems running in the same process where
  ;; they would have different values for the "env" tag. So, this is fine.
  (let [{:keys [env]} datadog]
    (if env
      (do
        (log/info :metrics/enabled? true :metrics/env env)
        (dogstatsd/configure!
          "localhost:8125"
          {:tags {:env env}}))
      (log/info :metrics/enabled? false)))
  (component/system-using
    (component/map->SystemMap
      {:http-server (server/map->Server  http-server)
       :cache       (releases/map->Cache cache)})
    {:http-server [:cache]}))
