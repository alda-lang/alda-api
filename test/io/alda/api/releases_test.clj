(ns io.alda.api.releases-test
  (:require [clojure.test         :refer [deftest testing is are]]
            [io.alda.api.releases :as    releases]))

(deftest parse-version-tests
  (testing "parsing version strings"
    (are
      [result version-string] (= result (releases/parse-version version-string))
      nil     "enth;sntohen;hcrohlec"
      nil     "2"
      nil     "2.3"
      nil     "2.3.4.5"
      nil     "2.3.4.5.6"
      nil     "2,3,4"
      nil     "2.3,4"
      nil     "123"
      nil     "applesauce"
      [2 3 4] "2.3.4")))

(defn mock-release-data
  [[major minor patch]]
  (let [version (format "%s.%s.%s" major minor patch)]
    {:version   version
     :date      "2099-09-09"
     :changelog "* did some stuff\n* did some things too"
     :assets    (reduce
                  (fn [m os-and-arch]
                    (assoc
                      m
                      os-and-arch
                      [{:type "executable"
                        :name "alda"
                        :url  (format
                                "https://example.com/%s/alda"
                                version)}
                       {:type "executable"
                        :name "alda-player"
                        :url  (format
                                "https://example.com/%s/alda-player"
                                version)}]))
                  {}
                  ["darwin-amd64" "darwin-arm64" "linux-386" "linux-amd64"
                   "windows-386" "windows-amd64"])}))

(def mock-releases-data
  (into
    (sorted-map)
    (for [version
          [[1 4 3]
           [1 4 2]
           [1 4 1]
           [1 99 1]
           [1 99 2]
           [2 0 0]
           [2 1 2]
           [2 1 0]
           [2 0 1]
           [2 0 5]]]
      [version (mock-release-data version)])))

(deftest latest-releases-tests
  (testing "providing data about the latest releases"
    ;; NOTE: I tried to use clojure.test/are here, but I couldn't get it to
    ;; print the actual values when actual != expected. It seems like maybe
    ;; `are` is limited compared to `is` in that respect.
    (doseq [[from-version expected-release-versions expected-explanation]
            [[nil      ["2.1.2"]          nil]
             [[0 0 1]  ["1.99.2" "2.1.2"] releases/alda-2-explanation]
             [[1 0 0]  ["1.99.2" "2.1.2"] releases/alda-2-explanation]
             [[1 0 1]  ["1.99.2" "2.1.2"] releases/alda-2-explanation]
             [[1 5 0]  ["1.99.2" "2.1.2"] releases/alda-2-explanation]
             [[1 99 9] ["2.1.2"]          releases/alda-2-explanation]
             [[2 0 0]  ["2.1.2"]          nil]
             [[2 0 2]  ["2.1.2"]          nil]
             [[2 1 0]  ["2.1.2"]          nil]
             [[2 1 2]  []                 nil]
             [[2 3 0]  []                 nil]]
            :let [{:keys [releases explanation]}
                  (releases/latest-releases mock-releases-data from-version)]]
      (is
        (= [expected-release-versions expected-explanation]
           [(map :version releases) explanation])))))
