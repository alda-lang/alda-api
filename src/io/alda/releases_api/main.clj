(ns io.alda.releases-api.main)

(defn -main
  [& _args]
  (println "Fake serving...")
  @(promise)
  (println "should never get here"))
