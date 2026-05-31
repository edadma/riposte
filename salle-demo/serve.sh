#!/usr/bin/env bash
#
# Serve the salle demo over http://localhost:<port> (default 8000).
#
# It serves the *repo root*, not this folder, so the page's two relative
# references both resolve: the compiled bundle under
# `salle-demo/target/scala-3.8.3/salle-demo-fastopt/main.js` and the library
# stylesheet at `salle/css/salle.css`. (A plain `file://` open works too; this is
# the localhost option.)
#
# Build first if you haven't:  sbt salleDemo/fastLinkJS
# Then:                        salle-demo/serve.sh   [port]
# Open:                        http://localhost:<port>/salle-demo/index.html
#
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
port="${1:-8000}"

cd "$root"
echo "Serving $root"
echo "Open  →  http://localhost:${port}/salle-demo/index.html"
echo "(Ctrl-C to stop)"
exec python3 -m http.server "$port"
