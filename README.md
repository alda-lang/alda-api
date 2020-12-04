# releases-api

An API that provides information about Alda releases.

## Development

First, install the [official `clojure` CLI tools][clojure-cli].

Then, run `clojure -m serve 8080` to serve the app locally on port 8080 (or
replace 8080 with the port of your choice).

## Deployment

The Alda Releases API is set up as a DigitalOcean App. The [app specification
YAML file][do-app-spec-yaml] is `.do/app.yaml`. When a new commit is pushed to
the `master` branch, it triggers an automatic build and deployment in the
DigitalOcean App Platform.

DigitalOcean App Platform does not support Clojure out of the box, but they do
support Docker, and it was easy to set up a basic Dockerfile (see `Dockerfile`
in this repo) to create a container that has a recent version of the `clojure`
CLI and can fetch the dependencies and run the app.

## License

Copyright © 2012-2020 Dave Yarwood et al

Distributed under the Eclipse Public License version 2.0.

[clojure-cli]: https://clojure.org/guides/deps_and_cli
[do-app-spec-yaml]: https://www.digitalocean.com/docs/app-platform/references/app-specification-reference/
