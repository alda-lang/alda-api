(ns io.alda.api.telemetry
  (:require [cognician.dogstatsd :as dogstatsd]))

(defn record-telemetry!
  [{:strs [os arch version command]}]
  (dogstatsd/increment!
    "cli.telemetry"
    1
    {:tags {"os"      (or os "unknown")
            "arch"    (or arch "unknown")
            "version" (or version "unknown")
            "command" (or command "unknown")}}))
