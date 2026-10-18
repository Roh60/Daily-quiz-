#!/usr/bin/env python3
"""Validate critical GK Swal Android/Firebase configuration before CI build."""
from __future__ import annotations
import json
import pathlib
import re
import sys
import xml.etree.ElementTree as ET

ROOT = pathlib.Path(__file__).resolve().parents[1]
errors: list[str] = []

def load_json(path: pathlib.Path):
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except Exception as exc:
        errors.append(f"{path.relative_to(ROOT)} is not valid JSON: {exc}")
        return {}

app_gradle = (ROOT / "app/build.gradle.kts").read_text(encoding="utf-8")
match = re.search(r'applicationId\s*=\s*"([^"]+)"', app_gradle)
application_id = match.group(1) if match else ""
if not application_id:
    errors.append("Could not find app/build.gradle.kts applicationId")

services = load_json(ROOT / "app/google-services.json")
registered_packages = {
    client.get("client_info", {}).get("android_client_info", {}).get("package_name")
    for client in services.get("client", [])
}
if application_id and application_id not in registered_packages:
    errors.append(f"applicationId {application_id!r} is not registered in app/google-services.json")

firebase = load_json(ROOT / "firebase.json")
configured_databases = {
    item.get("database") for item in firebase.get("firestore", []) if isinstance(item, dict)
}
functions_source = (ROOT / "functions/index.js").read_text(encoding="utf-8")
db_match = re.search(r"const DATABASE_ID\s*=\s*'([^']+)'", functions_source)
functions_database = db_match.group(1) if db_match else ""
if not functions_database:
    errors.append("Could not find Functions DATABASE_ID in functions/index.js")
elif functions_database not in configured_databases:
    errors.append(
        f"Functions DATABASE_ID {functions_database!r} is not listed in firebase.json Firestore databases"
    )

manifest = ET.parse(ROOT / "app/src/main/AndroidManifest.xml").getroot()
permission_names = {node.attrib.get("{http://schemas.android.com/apk/res/android}name") for node in manifest.findall("uses-permission")}
if "android.permission.INTERNET" not in permission_names:
    errors.append("AndroidManifest.xml is missing INTERNET permission")

package = load_json(ROOT / "functions/package.json")
if package.get("engines", {}).get("node") != "20":
    errors.append("Cloud Functions runtime should match the Node 20 CI environment")

print(f"Android applicationId: {application_id or '(missing)'}")
print(f"Registered Firebase Android clients: {len(registered_packages)}")
print(f"Firestore database configured for Functions: {functions_database or '(missing)'}")
if errors:
    print(f"FAILED: {len(errors)} configuration issue(s)")
    for error in errors:
        print(f"- {error}")
    sys.exit(1)
print("PASS: Android/Firebase package, database, manifest and Functions runtime configuration")
