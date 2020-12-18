FROM daveyarwood:alda-releases-api:latest

WORKDIR /root/releases-api
COPY . .

# Fetch dependencies and exit.
RUN clojure -P

# Deliberately fail to start unless the tests are passing.
RUN clojure -M:test

# Serve the app.
CMD clojure -M:serve 8080
