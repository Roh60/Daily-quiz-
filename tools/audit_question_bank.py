#!/usr/bin/env python3
"""Structural audit for GK Swal's Kotlin QuestionEntity seed bank.

This verifies code structure only. It cannot prove that answers are factually correct.
Run from the repository root: python3 tools/audit_question_bank.py
"""
from __future__ import annotations
import collections
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parents[1]
SOURCE_DIR = ROOT / "app/src/main/java/com/example/data/local"
TARGETS = {
    "History", "Indian Polity", "Geography", "General Science", "Indian Economy",
    "Static GK", "Sports", "Entertainment", "Kids", "Currency", "Current Affairs", "Facts"
}
EXPECTED_PER_CATEGORY = 400
EXPECTED_TOTAL = 7997
EXPECTED_COUNTS = {name: (691 if name in {"History", "Indian Polity", "Geography", "General Science", "Indian Economy", "Static GK", "Sports"} else 690 if name in {"Sports", "Entertainment", "Kids", "Current Affairs", "Facts"} else 400) for name in TARGETS}


def calls_in(text: str):
    """Yield the content of each QuestionEntity(...) call, respecting strings/nesting."""
    needle = "QuestionEntity("
    pos = 0
    while True:
        start = text.find(needle, pos)
        if start < 0:
            return
        i = start + len(needle)
        body_start = i
        depth = 1
        in_string = False
        escaped = False
        while i < len(text) and depth:
            ch = text[i]
            if in_string:
                if escaped:
                    escaped = False
                elif ch == "\\":
                    escaped = True
                elif ch == '"':
                    in_string = False
            else:
                if ch == '"':
                    in_string = True
                elif ch == '(':
                    depth += 1
                elif ch == ')':
                    depth -= 1
            i += 1
        if depth:
            raise ValueError(f"Unclosed QuestionEntity call near character {start}")
        yield text[body_start:i-1]
        pos = i


def split_args(body: str):
    parts, start, depth = [], 0, 0
    in_string = False
    escaped = False
    for i, ch in enumerate(body):
        if in_string:
            if escaped:
                escaped = False
            elif ch == "\\":
                escaped = True
            elif ch == '"':
                in_string = False
        else:
            if ch == '"':
                in_string = True
            elif ch in "([{":
                depth += 1
            elif ch in ")]}":
                depth -= 1
            elif ch == ',' and depth == 0:
                parts.append(body[start:i].strip())
                start = i + 1
    tail = body[start:].strip()
    if tail:
        parts.append(tail)
    return parts


def unquote(value: str):
    value = value.strip()
    if len(value) >= 2 and value[0] == value[-1] == '"':
        # Kotlin seed strings do not use interpolation; decode common escaped characters.
        return bytes(value[1:-1], "utf-8").decode("unicode_escape")
    return value


def parse_call(body: str):
    args = split_args(body)
    named = {}
    positional = []
    for arg in args:
        match = re.match(r"([A-Za-z_][A-Za-z0-9_]*)\s*=\s*(.*)$", arg, re.S)
        if match:
            named[match.group(1)] = match.group(2).strip()
        else:
            positional.append(arg)
    if named:
        return {
            "id": unquote(named.get("id", "")),
            "subject": unquote(named.get("subject", "")),
            "question": unquote(named.get("question", "")),
            "options": [unquote(named.get(f"option{x}", "")) for x in "ABCD"],
            "answer": named.get("correctOptionIndex", ""),
            "explanation": unquote(named.get("explanation", "")),
        }
    # Positional constructor order follows QuestionEntity in GkEntities.kt.
    if len(positional) < 13:
        return {"id": "", "subject": "", "question": "", "options": [], "answer": "", "explanation": ""}
    return {
        "id": unquote(positional[0]),
        "subject": unquote(positional[2]),
        "question": unquote(positional[5]),
        "options": [unquote(positional[i]) for i in range(6, 10)],
        "answer": positional[10],
        "explanation": unquote(positional[11]),
    }


def main():
    rows = []
    for path in sorted(SOURCE_DIR.glob("*.kt")):
        if path.name == "GkEntities.kt":
            continue
        text = path.read_text(encoding="utf-8")
        for body in calls_in(text):
            rows.append((path.relative_to(ROOT).as_posix(), parse_call(body)))

    errors = []
    ids = [row[1]["id"] for row in rows]
    duplicate_ids = sorted(k for k, count in collections.Counter(ids).items() if k and count > 1)
    if duplicate_ids:
        errors.append(f"Duplicate IDs ({len(duplicate_ids)}): {', '.join(duplicate_ids[:20])}")
    missing_ids = sum(not q["id"] for _, q in rows)
    if missing_ids:
        errors.append(f"{missing_ids} questions have missing/unparsed IDs")

    counts = collections.Counter(q["subject"] for _, q in rows)
    normalized_stems = [re.sub(r"\s+", " ", q["question"].strip().casefold()) for _, q in rows if q["question"].strip()]
    duplicate_stems = sorted(stem for stem, count in collections.Counter(normalized_stems).items() if count > 1)
    if duplicate_stems:
        errors.append(f"Duplicate normalized question stems ({len(duplicate_stems)} groups): {', '.join(duplicate_stems[:10])}")
    if len(rows) != EXPECTED_TOTAL:
        errors.append(f"Expected {EXPECTED_TOTAL} QuestionEntity records; parsed {len(rows)}")
    for category in sorted(TARGETS):
        if counts[category] != EXPECTED_COUNTS[category]:
            errors.append(f"{category}: expected {EXPECTED_COUNTS[category]}, found {counts[category]}")
    extras = sorted(set(counts) - TARGETS - {""})
    if extras:
        errors.append(f"Unexpected categories: {', '.join(extras)}")

    for file_name, q in rows:
        label = q["id"] or f"unknown ID in {file_name}"
        if not q["question"].strip(): errors.append(f"{label}: missing question text")
        if len(q["options"]) != 4 or any(not x.strip() for x in q["options"]):
            errors.append(f"{label}: missing one or more of four options")
        elif len({x.strip().casefold() for x in q["options"]}) != 4:
            errors.append(f"{label}: duplicate answer options")
        try:
            answer = int(q["answer"])
            if answer not in range(4): errors.append(f"{label}: correctOptionIndex is outside 0..3")
        except (TypeError, ValueError):
            errors.append(f"{label}: invalid/unparsed correctOptionIndex")
        if not q["explanation"].strip(): errors.append(f"{label}: missing explanation")

    print(f"Parsed question records: {len(rows)}")
    print(f"Unique non-empty IDs: {len(set(x for x in ids if x))}")
    print("Category counts:")
    for category in sorted(TARGETS): print(f"  {category}: {counts[category]}")
    print("FACTUAL ACCURACY: NOT CHECKED BY THIS SCRIPT")
    print("NOTE: Expansion items include alternate-wording revision variants; unique underlying facts are fewer than record count.")
    if errors:
        print(f"\nFAILED: {len(errors)} structural issue(s)")
        for error in errors[:100]: print(f"- {error}")
        if len(errors) > 100: print(f"... plus {len(errors)-100} more")
        return 1
    print("PASS: structural counts, IDs, options, answer indexes and explanations")
    return 0

if __name__ == "__main__":
    sys.exit(main())
