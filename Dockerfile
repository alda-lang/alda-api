FROM clojure:openjdk-11-tools-deps-1.10.1.739
WORKDIR /root/releases-api
COPY . .

# Fetch dependencies and exit.
RUN clojure -P

# Serve the app.
CMD clojure -M:serve 8080
