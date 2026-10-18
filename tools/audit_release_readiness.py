#!/usr/bin/env python3
"""Report remaining human/account-dependent GK Swal release gates.

Default mode is informational and exits successfully so CI can still build an
internal-test artifact. Use --strict to fail if publication placeholders or
required release infrastructure are still missing.
"""
from __future__ import annotations
import argparse
import pathlib
import re
import sys
import os

ROOT = pathlib.Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser()
parser.add_argument('--strict', action='store_true', help='exit non-zero while release blockers remain')
args = parser.parse_args()

blockers: list[str] = []
checks: list[str] = []

for rel in ('legal/privacy-policy-template.html', 'legal/delete-account.html'):
    path = ROOT / rel
    if not path.exists():
        blockers.append(f'Missing required legal page: {rel}')
        continue
    content = path.read_text(encoding='utf-8')
    if re.search(r'\[(?:ADD|REPLACE|INSERT)[^\]]*\]|\b(?:YOUR_[A-Z0-9_]+|example\.com|support@example\.com|TODO(?:_\w+)?)\b', content, re.I):
        blockers.append(f'{rel} still contains publication placeholders')
    else:
        checks.append(f'No obvious bracketed placeholders: {rel}')

workflow = (ROOT / '.github/workflows/android-release.yml')
workflow_text = workflow.read_text(encoding='utf-8') if workflow.exists() else ''
artifact_match = re.search(r'name:\s*(gk-swal-release-aab|daily-quiz-release-aab)', workflow_text)
if not artifact_match:
    blockers.append('Could not identify the AAB artifact name in the GitHub Actions workflow')
else:
    artifact_name = artifact_match.group(1)
    for rel in ('README.md', 'COMPLETE-FIXES-REPORT.md'):
        path = ROOT / rel
        if path.exists() and artifact_name not in path.read_text(encoding='utf-8'):
            blockers.append(f'{rel} does not mention the workflow artifact name {artifact_name!r}')
        else:
            checks.append(f'Workflow artifact name documented consistently: {rel}')

workflow_has_hosted_gradle = bool(re.search(r'gradle/actions/setup-gradle@[^\n]+', workflow_text))
if (not (ROOT / 'gradlew').exists() or not (ROOT / 'gradle/wrapper/gradle-wrapper.jar').exists()) and not workflow_has_hosted_gradle:
    blockers.append('No Gradle wrapper or hosted Gradle setup was found')
else:
    checks.append('Build workflow provides a Gradle installation')
if 'firebase-appcheck-playintegrity' not in (ROOT / 'gradle/libs.versions.toml').read_text(encoding='utf-8'):
    blockers.append('Play Integrity App Check dependency alias is missing from the version catalog')

app_gradle = (ROOT / 'app/build.gradle.kts').read_text(encoding='utf-8')
version_code = re.search(r'versionCode\s*=\s*(\d+)', app_gradle)
if not version_code or int(version_code.group(1)) < 1:
    blockers.append('Android versionCode is missing or invalid')
else:
    checks.append(f'Android versionCode is configured: {version_code.group(1)}')

catalog = (ROOT / 'gradle/libs.versions.toml').read_text(encoding='utf-8')
if 'play-services-ads' in catalog:
    checks.append('AdMob SDK dependency is configured; production app/ad unit IDs must be supplied')
else:
    blockers.append('AdMob SDK dependency is missing')
if 'user-messaging-platform' in catalog:
    checks.append('Google UMP consent SDK dependency is configured')
else:
    blockers.append('Google UMP consent SDK dependency is missing')
if args.strict:
    from urllib.parse import urlparse
    for key, label in (
        ('PUBLIC_PRIVACY_POLICY_URL', 'Public Privacy Policy URL'),
        ('PUBLIC_ACCOUNT_DELETION_URL', 'Public account-deletion URL'),
    ):
        value = os.environ.get(key, '').strip()
        parsed = urlparse(value)
        if not value:
            blockers.append(f'{key} repository variable is required for signed release')
        elif parsed.scheme != 'https' or not parsed.netloc:
            blockers.append(f'{key} must be a valid public HTTPS URL')
        else:
            checks.append(f'{label} is configured as HTTPS')

    app_id = os.environ.get('ADMOB_APP_ID', '').strip()
    banner_id = os.environ.get('ADMOB_BANNER_AD_UNIT_ID', '').strip()
    if not app_id:
        blockers.append('ADMOB_APP_ID environment variable is required for signed release')
    elif not re.fullmatch(r'ca-app-pub-\d{16}~\d{10}', app_id):
        blockers.append('ADMOB_APP_ID must be a production-format AdMob app ID (ca-app-pub-16digits~10digits), not a sample ID')
    elif app_id == 'ca-app-pub-3940256099942544~3347511713':
        blockers.append('Google sample AdMob App ID cannot be used for a signed release')
    if not banner_id:
        blockers.append('ADMOB_BANNER_AD_UNIT_ID environment variable is required for signed release')
    elif not re.fullmatch(r'ca-app-pub-\d{16}/\d{10}', banner_id):
        blockers.append('ADMOB_BANNER_AD_UNIT_ID must be a production-format AdMob ad unit ID (ca-app-pub-16digits/10digits)')
    elif banner_id == 'ca-app-pub-3940256099942544/6300978111':
        blockers.append('Google sample banner ad unit cannot be used for a signed release')
    for key in ('UPLOAD_KEYSTORE_BASE64', 'STORE_PASSWORD', 'KEY_PASSWORD'):
        if not os.environ.get(key, '').strip():
            blockers.append(f'{key} GitHub Actions secret is required for signed release')

print('GK Swal release readiness audit')
for item in checks:
    print(f'PASS: {item}')
for item in blockers:
    print(f'BLOCKER: {item}')
print(f'Summary: {len(checks)} checks passed; {len(blockers)} release blocker(s) identified.')
if args.strict and blockers:
    sys.exit(1)
