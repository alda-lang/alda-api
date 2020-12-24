FROM daveyarwood/alda-api:latest

WORKDIR /root/alda-api
COPY . .

# Fetch dependencies and exit.
RUN clojure -P

# Deliberately fail to start unless the tests are passing.
RUN clojure -M:test

# Serve the app.
CMD bin/run
