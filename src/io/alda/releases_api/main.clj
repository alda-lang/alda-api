(ns io.alda.releases-api.main
  (:require [io.alda.releases-api.service :as service]))

(defn -main
  [& [port*]]
  (let [port (Integer/parseInt port*)]
    (service/serve port)))
