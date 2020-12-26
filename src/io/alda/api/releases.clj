(ns io.alda.api.releases
  (:require [clj-http.client            :as http]
            [clojure.data.xml           :as xml]
            [clojure.string             :as str]
            [cognician.dogstatsd        :as dogstatsd]
            [com.stuartsierra.component :as component]
            [io.pedestal.log            :as log]))

(def storage-base-url
  "https://alda-releases.nyc3.digitaloceanspaces.com")

(xml/alias-uri 's3 "http://s3.amazonaws.com/doc/2006-03-01/")

(defn files-by-release
  []
  (let [xml-nodes     (-> (http/get storage-base-url)
                          :body
                          xml/parse-str
                          :content)
        content-nodes (for [{:keys [tag content]} xml-nodes
                            :when (= ::s3/Contents tag)]
                        content)
        file-keys     (mapcat #(for [{:keys [tag content]} %
                                     :when (= ::s3/Key tag)]
                                 ;; I'm not sure why this is a list of values
                                 ;; instead of a single value, but there is only
                                 ;; ever one value, so I'll just pull it out
                                 ;; here.
                                 (first content))
                              content-nodes)]
    (->> file-keys
         (map #(str/split % #"/" 2))
         (group-by first)
         (reduce-kv (fn [m k v]
                      (assoc m k (map second v)))
                    {}))))

(defn parse-version
  "Given a string like \"1.2.3\", returns a [major minor patch] tuple like
   [1 2 3].

   Returns nil if the provided string cannot be parsed as a version."
  [version-string]
  (let [components (->> (str/split version-string #"\.")
                        (mapv #(try
                                 (Integer/parseInt %)
                                 (catch Throwable _))))]
    (when (and (= 3 (count components))
               (every? number? components))
      components)))

(defn compile-releases-data
  []
  (->> (files-by-release)
       (map (fn [[version-string files]]
              (let [version (or (parse-version version-string)
                                (throw (ex-info
                                         "Invalid version string."
                                         {:version-string version-string})))]
                [version
                 (merge
                   {:version
                    version-string

                    :assets
                    (into
                      {}
                      (concat
                        (for [os-and-arch
                              ["darwin-amd64" "linux-386" "linux-amd64"]]
                          [os-and-arch
                           (for [file files
                                 :when (or (str/includes? file os-and-arch)
                                           (str/includes? file "non-windows"))]
                             {:type "executable"
                              :name (last (str/split file #"/"))
                              :url  (format "%s/%s/%s"
                                            storage-base-url
                                            version-string
                                            file)})])
                        (for [os-and-arch
                              ["windows-386" "windows-amd64"]]
                          [os-and-arch
                           (for [file files
                                 :when (or (str/includes? file os-and-arch)
                                           (str/includes? file "/windows/")
                                           (str/starts-with? file "windows/"))]
                             {:type "executable"
                              :name (last (str/split file #"/"))
                              :url  (format "%s/%s/%s"
                                            storage-base-url
                                            version-string
                                            file)})])))}
                   (when (some #{"date.txt"} files)
                     {:date
                      (-> (http/get (format "%s/%s/date.txt"
                                            storage-base-url
                                            version-string))
                          :body
                          str/trim)})
                   (when (some #{"CHANGELOG.md"} files)
                     {:changelog
                      (-> (http/get (format "%s/%s/CHANGELOG.md"
                                            storage-base-url
                                            version-string))
                          :body
                          str/trim)}))])))
       (into (sorted-map))))

(defrecord Cache []
  component/Lifecycle
  (start [{:keys [running?] :as component}]
    (if running?
      component
      (let [data     (atom nil)
            running? (atom true)]
        (future
          ;; Update the cache every 60 seconds.
          (while @running?
            (try
              (log/info :cache/status :updating)
              (reset! data (compile-releases-data))
              (log/info :cache/status :updated)
              (dogstatsd/increment!
                "api.releases.cache.update.success"
                1)
              (catch Throwable t
                (dogstatsd/increment!
                  "api.releases.cache.update.failed"
                  1)
                (log/error :exception t
                           :cache/status :failed)))
            (Thread/sleep 60000)))
        ;; Wait until the initial data is loaded before considering the
        ;; component to be operational.
        (while (nil? @data)
          (Thread/sleep 500))
        (assoc component
               ::data          data
               :cache/running? running?))))

  (stop [{:cache/keys [running?] :as component}]
    (when running?
      (reset! running? false))
    (dissoc component ::data :cache/running?)))

(def alda-v2-explanation
  "Alda v2 is a long-anticipated total rewrite of Alda, focused on:

  * Simplifying the architecture to make the end user experience better.
  * Performance improvements.
  * Support for live-coding.

  Alda v2 is _mostly_ compatible with your existing Alda v1 scores, but there
  are some minor differences. (TODO: link to migration guide here)")

(defn releases-from-version
  "Given:
    * `from-version`, the current version of Alda being updated from, and
    * `data`, the data about all available release versions,

   Returns a tuple [releases explanation], where `releases` is a list of options
   for newer releases, and explanation is either nil or a string explaining why
   there are multiple version choices and why the user might want to download
   one vs. another."
  [from-version data]
  (let [[from-major-version _minor _patch]
        from-version

        ;; A seq of all available versions newer than `from-version`, in order.
        newer-versions
        (->> data
             (drop-while (fn [[version _]]
                           (<= (compare version from-version) 0))))

        ;; A sorted map of major release version number (e.g. 2) to list of
        ;; versions in that version series.
        candidates
        (->> newer-versions
             (partition-by ffirst)
             (map (fn [[version & more :as version-series]]
                    [(ffirst version)
                     (val (last version-series))]))
             (into (sorted-map)))]
    [(or (vals candidates) [])
     (when (< from-major-version 2)
       alda-v2-explanation)]))

(defn latest-release
  "Returns the very latest release available.

   To be congruent with `releases-from-version`, the return value of
   `latest-release` is also a tuple [releases explanation], where `releases` is
   a list containing just the one release, and `explanation` is nil."
  [data]
  [[(val (last data))]
   nil])

(defn latest-releases
  [data from-version]
  (let [[releases explanation]
        (if from-version
          (releases-from-version from-version data)
          (latest-release data))]
    (merge
      {:releases releases}
      (when explanation
        {:explanation explanation}))))

(defn get-release
  [data release-version]
  (get data release-version))

(defn all-releases
  [data]
  {:releases (reverse (vals data))})

