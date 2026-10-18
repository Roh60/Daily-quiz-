#!/usr/bin/env python3
"""Ensure every shipped question has a matching factual-review tracking row.

This tool checks coverage and evidence fields only. It never declares a question
factually correct; a reviewer must manually compare the answer with an authoritative source.
"""
from __future__ import annotations
import csv
import importlib.util
import pathlib
import sys

ROOT = pathlib.Path(__file__).resolve().parents[1]
TRACKER = ROOT / 'question-bank-fact-check-tracker.csv'
AUDIT_SCRIPT = ROOT / 'tools/audit_question_bank.py'
spec = importlib.util.spec_from_file_location('question_bank_audit', AUDIT_SCRIPT)
if spec is None or spec.loader is None:
    print('FAILED: cannot load question-bank parser')
    sys.exit(1)
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)

question_ids: set[str] = set()
source_dir = ROOT / 'app/src/main/java/com/example/data/local'
for path in sorted(source_dir.glob('*.kt')):
    if path.name == 'GkEntities.kt':
        continue
    for body in module.calls_in(path.read_text(encoding='utf-8')):
        q = module.parse_call(body)
        if q.get('id'):
            question_ids.add(q['id'])

with TRACKER.open(encoding='utf-8-sig', newline='') as handle:
    rows = list(csv.DictReader(handle))
tracker_ids = [row.get('id', '').strip() for row in rows]
tracker_set = {qid for qid in tracker_ids if qid}
errors: list[str] = []
if len(rows) != 4800:
    errors.append(f'Expected 4,800 tracker rows, found {len(rows)}')
if len(tracker_set) != len(tracker_ids):
    errors.append('Tracker contains blank or duplicate question IDs')
missing = question_ids - tracker_set
extra = tracker_set - question_ids
if missing:
    errors.append(f'{len(missing)} question IDs are missing from tracker (e.g. {sorted(missing)[:5]})')
if extra:
    errors.append(f'{len(extra)} tracker IDs are not in shipped question bank (e.g. {sorted(extra)[:5]})')

verified_statuses = {'VERIFIED', 'FACT_CHECKED', 'APPROVED'}
for row in rows:
    if row.get('factCheckStatus', '').strip().upper() in verified_statuses:
        missing_fields = [name for name in ('sourceUrl', 'verifiedBy', 'verifiedOn') if not row.get(name, '').strip()]
        if missing_fields:
            errors.append(f"{row.get('id')}: marked verified without {', '.join(missing_fields)}")

print(f'Shipped question IDs: {len(question_ids)}')
print(f'Fact-check tracker rows: {len(rows)}')
print(f'Rows marked verified: {sum(row.get("factCheckStatus", "").strip().upper() in verified_statuses for row in rows)}')
print('FACTUAL ACCURACY: NOT VERIFIED BY THIS TOOL')
if errors:
    print(f'FAILED: {len(errors)} tracker issue(s)')
    for error in errors[:50]: print(f'- {error}')
    sys.exit(1)
print('PASS: tracker covers every shipped question; evidence fields required for verified status')
