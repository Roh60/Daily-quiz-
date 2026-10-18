#!/usr/bin/env python3
import json, csv, sys
from pathlib import Path
root=Path(__file__).resolve().parents[1]
p=root/'app/src/main/assets/global_currency_gk_questions.json'
data=json.loads(p.read_text(encoding='utf-8'))
qs=data.get('questions', [])
errors=[]; ids=set(); stems=set()
for i,q in enumerate(qs,1):
    if not q.get('id') or q['id'] in ids: errors.append(f'row {i}: missing/duplicate id')
    ids.add(q.get('id'))
    if not q.get('question') or q['question'] in stems: errors.append(f'row {i}: missing/duplicate question')
    stems.add(q.get('question'))
    opts=q.get('options', [])
    if len(opts)!=4 or len(set(opts))!=4: errors.append(f'row {i}: options must be 4 unique values')
    if not isinstance(q.get('correctIndex'),int) or q['correctIndex'] not in range(4): errors.append(f'row {i}: invalid correctIndex')
    if not q.get('explanation'): errors.append(f'row {i}: missing explanation')
if len(qs)<2000: errors.append(f'expected >=2000 questions, got {len(qs)}')
if data.get('questionCount')!=len(qs): errors.append('questionCount metadata mismatch')
if errors:
    print('\n'.join(errors[:30])); sys.exit(1)
print(f'PASS: {len(qs)} global currency questions, unique IDs/stems, 4 unique options and valid answer indexes.')
