(ns io.alda.releases-api.server
  (:require [com.stuartsierra.component    :as component]
            [io.alda.releases-api.releases :as releases]
            [io.pedestal.http              :as http]
            [io.pedestal.http.route        :as route]
            [io.pedestal.interceptor       :as int]
            [jsonista.core                 :as json]))

(defn response
  [status payload]
  (let [body (cond
               (map? payload)
               (json/write-value-as-string payload)

               (string? payload)
               (json/write-value-as-string {:message payload})

               :else
               payload)]
    {:status status, :body body}))

(def success-response
  (partial response 200))

(def client-error-response
  (partial response 400))

(defn report-health
  [_request]
  (success-response "I'm healthy!"))

(defn latest-releases
  [{::releases/keys [data]
    :keys [query-params]
    :as _request}]
  (let [{:keys [from-version]} query-params
        parsed-version         (when from-version
                                (releases/parse-version from-version))]
    (if (and from-version (not parsed-version))
      (client-error-response
        (format "Invalid version string: %s" from-version))
      (success-response
        (releases/latest-releases data parsed-version)))))

(def routes
  (route/expand-routes
    #{["/health" :get `report-health]
      ["/latest" :get `latest-releases]}))

(defn data-interceptor
  [{::releases/keys [data] :as _cache}]
  (int/interceptor
    {:name  ::data-interceptor
     :enter (fn [context]
              (assoc-in context [:request ::releases/data] @data))
     :leave (fn [context]
              (update context :request dissoc ::releases/data))}))

(defrecord Server []
  component/Lifecycle
  (start [{:keys [cache]
           ::keys [server]
           :as component}]
    (if server
      component
      (assoc
        component
        ::server
        (-> {::http/routes routes
             ::http/type   :jetty
             ::http/host   "0.0.0.0"
             ::http/port   8080
             ::http/join?  false}
            (http/default-interceptors)
            (update ::http/interceptors conj (data-interceptor cache))
            (merge component)
            http/create-server
            http/start))))

  (stop [{::keys [server] :as component}]
    (when server (http/stop server))
    (dissoc component ::server)))
