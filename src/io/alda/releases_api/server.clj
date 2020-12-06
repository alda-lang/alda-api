(ns io.alda.releases-api.server
  (:require [com.stuartsierra.component    :as component]
            [io.alda.releases-api.releases :as releases]
            [io.pedestal.http              :as http]
            [io.pedestal.http.route        :as route]
            [jsonista.core                 :as json]))

(defn report-health
  [_request]
  {:status 200, :body "I'm healthy!"})

(defn latest-releases
  [request]
  {:status 200
   :body   (json/write-value-as-string
             (releases/latest-releases))})

(def routes
  (route/expand-routes
    #{["/health" :get `report-health]
      ["/latest" :get `latest-releases]}))

(defrecord Server []
  component/Lifecycle
  (start [{::keys [server] :as component}]
    ;; To make `start` idempotent, we check to see if there is already a server,
    ;; and if there is, it's a no-op.
    (if server
      component
      (assoc component ::server (-> (http/create-server
                                      (merge
                                        {::http/routes routes
                                         ::http/type   :jetty
                                         ::http/host   "0.0.0.0"
                                         ::http/port   8080
                                         ::http/join?  false}
                                        component))
                                    http/start))))

  (stop [{::keys [server] :as component}]
    ;; To make `stop` idempotent, we only attempt to stop the server if there
    ;; _is_ a server to stop. Otherwise, it's a no-op.
    (when server (http/stop server))
    (dissoc component ::server)))
