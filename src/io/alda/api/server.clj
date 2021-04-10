(ns io.alda.api.server
  (:require [cognician.dogstatsd        :as dogstatsd]
            [com.stuartsierra.component :as component]
            [io.alda.api.releases       :as releases]
            [io.alda.api.telemetry      :as telemetry]
            [io.pedestal.http           :as http]
            [io.pedestal.http.route     :as route]
            [io.pedestal.interceptor    :as int]
            [jsonista.core              :as json]))

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

(defn not-found-response
  [& [message]]
  (response 404 (or message "Not Found")))

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

(defn get-release
  [{::releases/keys [data]
    :keys [path-params]
    :as _request}]
  (let [{:keys [release-version]} path-params
        release-version           (releases/parse-version release-version)
        release-data              (releases/get-release data release-version)]
    (if release-data
      (success-response release-data)
      (not-found-response))))

(defn all-releases
  [{::releases/keys [data]
    :as _request}]
  (success-response (releases/all-releases data)))

(defn record-telemetry
  [{:keys [body] :as _request}]
  (telemetry/record-telemetry! (json/read-value body))
  (response 201 "Telemetry recorded."))

(def routes
  (route/expand-routes
    #{["/health"                   :get  `report-health]
      ["/release/:release-version" :get  `get-release]
      ["/releases/latest"          :get  `latest-releases]
      ["/releases"                 :get  `all-releases]
      ["/telemetry"                :post `record-telemetry]}))

(def request-metrics-interceptor
  (int/interceptor
    {:name  ::request-metrics-interceptor
     :leave (fn [{:keys [route request response] :as context}]
              (let [{:keys [path]}           route
                    {:keys [request-method]} request
                    {:keys [status]}         response]
                (dogstatsd/increment!
                  "api.request.count"
                  1
                  {:tags {"endpoint" (or path "invalid")
                          "method"   (name request-method)
                          "status"   status
                          "statusxx" (when status
                                       (str (first (pr-str status)) "xx"))}}))
              context)}))

(defn release-data-interceptor
  [{::releases/keys [data] :as _cache}]
  (int/interceptor
    {:name  ::release-data-interceptor
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
        (-> {::http/routes          routes
             ;; This is a public API, so we will allow requests from any origin.
             ;;
             ;; NOTE: The proper thing to do here is to add the response header:
             ;;   Access-Control-Allow-Origin: *
             ;;
             ;; But Pedestal's ::http/allowed-origins service map option
             ;; doesn't support doing it that way. I think this dynamically
             ;; sets the Access-Control-Allow-Origin response header to whatever
             ;; the origin is, if this function returns true. I think that will
             ;; work fine for our purposes.
             ::http/allowed-origins (constantly true)
             ::http/type            :jetty
             ::http/host            "0.0.0.0"
             ::http/port            8080
             ::http/join?           false}
            (http/default-interceptors)
            (update
              ::http/interceptors
              conj
              (release-data-interceptor cache)
              request-metrics-interceptor)
            (merge component)
            http/create-server
            http/start))))

  (stop [{::keys [server] :as component}]
    (when server (http/stop server))
    (dissoc component ::server)))
