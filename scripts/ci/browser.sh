#!/usr/bin/env bash
set -euo pipefail
test "${GITHUB_ACTIONS:-}" = true
mkdir -p .local/g4-browser docs/evidence/g4/browser
# Windows local Chrome is reused locally; Chromium installation is CI runner only.
npm ci --ignore-scripts --prefix browser-tests
node browser-tests/node_modules/playwright-core/cli.js install --with-deps chromium
export LEASEGUARD_CHROME
LEASEGUARD_CHROME="$(node --input-type=module -e "import {chromium} from './browser-tests/node_modules/playwright-core/index.mjs';console.log(chromium.executablePath())")"
export LEASEGUARD_PYTHON=python3
export LEASEGUARD_G4_SNAPSHOT_TOOL="$PWD/scripts/ci/snapshot.py"
export LEASEGUARD_EVIDENCE_DIR="$PWD/docs/evidence/ci"
export LEASEGUARD_G4_BROWSER_EVIDENCE="$LEASEGUARD_EVIDENCE_DIR/browser"
mkdir -p "$LEASEGUARD_G4_BROWSER_EVIDENCE"
# Explicit test-class command, no application endpoint, prepares a still-PENDING due task.
java -cp "drone-rental/target/test-classes:drone-rental/target/classes:$(cat drone-rental/target/ci-classpath.txt)" com.drone.rental.BrowserFixtureG4Setup >.local/g4-browser/fixture.log 2>&1
export LEASEGUARD_JOBS_ENABLED=true
java -Xmx512m -jar drone-rental/target/drone-rental-1.0.0.jar >.local/g4-browser/backend.log 2>&1 &
backend_pid=$!
(cd drone-rental/drone-rental-web && exec node node_modules/vite/bin/vite.js preview --host 127.0.0.1 --port 15174 --strictPort) >.local/g4-browser/frontend.log 2>&1 &
frontend_pid=$!
trap 'kill "$backend_pid" "$frontend_pid" 2>/dev/null || true' EXIT
python3 - <<'PY'
import time,urllib.request
for url in ['http://127.0.0.1:18084/api/drone/list','http://127.0.0.1:15174']:
    deadline=time.monotonic()+60
    while True:
        try:
            with urllib.request.urlopen(url,timeout=2) as r:assert r.status==200
            break
        except Exception:
            if time.monotonic()>deadline:raise
            time.sleep(.25)
PY
node browser-tests/g4.mjs
