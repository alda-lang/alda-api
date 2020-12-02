FROM clojure:openjdk-11-tools-deps-1.10.1.739
WORKDIR /root/releases-api
COPY . .
CMD clojure -M:serve
