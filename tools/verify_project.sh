#!/usr/bin/env bash
# Safe local preflight checks for GK Swal. This script does not deploy or modify Firebase.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

printf '\n== Question bank structure ==\n'
python3 tools/audit_question_bank.py
printf '\n== Full-bank fact-check tracker coverage ==\n'
python3 tools/audit_question_factcheck_tracker.py
printf '\n== Android/Firebase config ==\n'
python3 tools/validate_project_config.py
printf '\n== Release blockers (informational) ==\n'
python3 tools/audit_release_readiness.py
printf '\n== JavaScript syntax ==\n'
node --check functions/index.js
node --check firestore.test.js
node --check functions.integration.test.js
printf 'PASS: JavaScript syntax checks\n'
printf '\n== JSON syntax ==\n'
python3 - <<'PY'
import json
for name in ('firebase.json', 'functions/package.json', 'app/google-services.json'):
    with open(name, encoding='utf-8') as handle:
        json.load(handle)
    print(f'PASS: {name}')
PY
printf '\n== Android build availability ==\n'
if command -v gradle >/dev/null 2>&1; then
  gradle --no-daemon testDebugUnitTest assembleDebug
elif [[ -x ./gradlew && -f ./gradle/wrapper/gradle-wrapper.jar ]]; then
  ./gradlew --no-daemon testDebugUnitTest assembleDebug
else
  printf 'NOT RUN: Gradle and a complete Gradle wrapper are unavailable. Use the hosted GitHub Actions workflow.\n'
fi
