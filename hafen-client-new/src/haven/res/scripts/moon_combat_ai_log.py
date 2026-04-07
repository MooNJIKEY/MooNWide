#!/usr/bin/env python3
"""
Analyze Haven MooNWide combat AI log (~/.haven/moon-combat-ai.log).

Log line format (tab-separated fields after ISO timestamp):
  pid=<jvm_pid>  mode=<n>  slot=<n>  res=<path>  score=<float>  ip=<self>/<opp>  <note>
  (pid omitted in older lines — still parsed)

Usage:
  python scripts/moon_combat_ai_log.py
  python scripts/moon_combat_ai_log.py path/to/moon-combat-ai.log
  python scripts/moon_combat_ai_log.py --json out.json
"""

from __future__ import annotations

import argparse
import json
import os
import re
import sys
from collections import Counter, defaultdict
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Iterator

LINE_RE = re.compile(
    r"^(?P<ts>[^\t]+)\t"
    r"(?:pid=(?P<pid>\d+)\t)?"
    r"mode=(?P<mode>\d+)\t"
    r"slot=(?P<slot>-?\d+)\t"
    r"res=(?P<res>[^\t]*)\t"
    r"score=(?P<score>[-+0-9.eE]+)\t"
    r"ip=(?P<ips>-?\d+)/(?P<ipo>-?\d+)\t"
    r"(?P<note>.*)$"
)


def default_log_path() -> Path:
    home = Path(os.environ.get("USERPROFILE") or os.environ.get("HOME") or ".")
    return home / ".haven" / "moon-combat-ai.log"


def parse_lines(path: Path) -> Iterator[dict[str, Any]]:
    text = path.read_text(encoding="utf-8", errors="replace")
    for line in text.splitlines():
        # Do not use strip(): it removes trailing TAB after ip=… when note is empty,
        # but LINE_RE requires that tab before the (possibly empty) note field.
        line = line.rstrip("\r\n")
        if not line or not line.strip():
            continue
        m = LINE_RE.match(line)
        if not m:
            yield {"raw": line, "parse_error": True}
            continue
        ts_s = m.group("ts")
        try:
            if ts_s.endswith("Z"):
                ts = datetime.fromisoformat(ts_s.replace("Z", "+00:00"))
            else:
                ts = datetime.fromisoformat(ts_s)
            if ts.tzinfo is None:
                ts = ts.replace(tzinfo=timezone.utc)
        except ValueError:
            ts = None
        pid_s = m.group("pid")
        yield {
            "ts": ts,
            "ts_raw": ts_s,
            "pid": int(pid_s) if pid_s is not None else None,
            "mode": int(m.group("mode")),
            "slot": int(m.group("slot")),
            "res": m.group("res"),
            "score": float(m.group("score")),
            "ip_self": int(m.group("ips")),
            "ip_opp": int(m.group("ipo")),
            "advantage": int(m.group("ips")) - int(m.group("ipo")),
            "note": m.group("note").strip(),
            "parse_error": False,
        }


def res_short(res: str, max_len: int = 48) -> str:
    res = res.strip() or "(empty)"
    return res if len(res) <= max_len else res[: max_len - 1] + "..."


def analyze(rows: list[dict[str, Any]]) -> dict[str, Any]:
    ok = [r for r in rows if not r.get("parse_error")]
    bad = [r for r in rows if r.get("parse_error")]
    if not ok:
        return {
            "total_lines": len(rows),
            "parsed": 0,
            "parse_errors": len(bad),
            "error": "no valid lines",
        }

    modes = Counter(r["mode"] for r in ok)
    by_pid = Counter(r["pid"] for r in ok if r.get("pid") is not None)
    by_res = Counter(r["res"] or "(empty)" for r in ok)
    scores = [r["score"] for r in ok]
    adv = [r["advantage"] for r in ok]

    SESSION_GAP_SEC = 8.0
    deltas_adv: list[int] = []
    for i in range(1, len(ok)):
        a, b = ok[i - 1], ok[i]
        if a.get("pid") != b.get("pid"):
            continue
        ta, tb = a.get("ts"), b.get("ts")
        if ta is None or tb is None:
            continue
        dt = (tb - ta).total_seconds()
        if 0 <= dt <= SESSION_GAP_SEC:
            deltas_adv.append(b["advantage"] - a["advantage"])

    delta_pos = sum(1 for d in deltas_adv if d > 0)
    delta_neg = sum(1 for d in deltas_adv if d < 0)
    delta_zero = sum(1 for d in deltas_adv if d == 0)

    ts_times = [r["ts"] for r in ok if r.get("ts")]
    t_min = min(ts_times) if ts_times else None
    t_max = max(ts_times) if ts_times else None

    return {
        "total_lines": len(rows),
        "parsed": len(ok),
        "parse_errors": len(bad),
        "time_first": t_min.isoformat() if t_min else None,
        "time_last": t_max.isoformat() if t_max else None,
        "modes": dict(modes),
        "lines_by_pid": {str(k): v for k, v in sorted(by_pid.items())},
        "mode_labels": {
            0: "legacy keywords",
            1: "AI 1-ply",
            2: "expectiminimax",
        },
        "top_resources": by_res.most_common(25),
        "score_min": min(scores),
        "score_max": max(scores),
        "score_mean": sum(scores) / len(scores),
        "advantage_mean": sum(adv) / len(adv),
        "advantage_min": min(adv),
        "advantage_max": max(adv),
        "pairwise_advantage_deltas": {
            "count": len(deltas_adv),
            "improved": delta_pos,
            "worsened": delta_neg,
            "unchanged": delta_zero,
            "same_pid_only": True,
        },
        "notes_error_samples": list({r["note"] for r in ok if r.get("note") and "missing" in r["note"].lower()})[:5],
    }


def print_report(rep: dict[str, Any]) -> None:
    if rep.get("error"):
        print("Error:", rep["error"])
        if rep.get("parse_errors"):
            print("Parse errors:", rep["parse_errors"])
        return

    print("Moon combat AI log summary")
    print("-" * 44)
    print(f"Lines total:     {rep['total_lines']}")
    print(f"Parsed:          {rep['parsed']}")
    print(f"Parse errors:    {rep['parse_errors']}")
    if rep.get("time_first"):
        print(f"First timestamp: {rep['time_first']}")
        print(f"Last timestamp:  {rep['time_last']}")
    bp = rep.get("lines_by_pid") or {}
    if len(bp) >= 2:
        print()
        print("By client JVM pid (bot-vs-bot / multi-instance):")
        for pk, pv in bp.items():
            print(f"  pid {pk}: {pv} lines")
    print()
    print("By brain mode:")
    labels = rep.get("mode_labels", {})
    for m, c in sorted(rep.get("modes", {}).items()):
        lbl = labels.get(m, str(m))
        print(f"  mode {m} ({lbl}): {c}")
    print()
    print("Scores:")
    print(f"  min / max / mean: {rep['score_min']:.3f} / {rep['score_max']:.3f} / {rep['score_mean']:.3f}")
    print()
    print("IP advantage (self - opp):")
    print(f"  min / max / mean: {rep['advantage_min']} / {rep['advantage_max']} / {rep['advantage_mean']:.2f}")
    pd = rep.get("pairwise_advantage_deltas", {})
    if pd.get("count", 0) > 0:
        print()
        print("Pairwise delta advantage (same pid, successive rows <=8s apart):")
        print(f"  pairs:     {pd['count']}")
        print(f"  improved:  {pd['improved']}")
        print(f"  worsened:  {pd['worsened']}")
        print(f"  same:      {pd['unchanged']}")
    print()
    print("Top resources:")
    for res, c in rep.get("top_resources", []):
        print(f"  {c:5d}  {res_short(res, 64)}")
    print()
    print("Tuning hints (manual):")
    adv_m = rep.get("advantage_mean", 0)
    if adv_m < -0.5:
        print("  - Mean advantage negative -> try lower combatBotAggression or more defense weight in JSON/heuristics.")
    elif adv_m > 2.0:
        print("  - Mean advantage high -> optional: raise aggression slightly or check for weak opponents only.")
    if pd.get("count", 0) > 20:
        ratio = pd["worsened"] / max(1, pd["improved"] + pd["worsened"])
        if ratio > 0.55:
            print("  - Many successive steps worsen IP -> review moon-combat-data.json breach/ipPressure or enable deeper search (depth 3).")
    if rep.get("notes_error_samples"):
        print("  - Table load messages in log -> fix haven/res/moon-combat-data.json and rebuild client.")
    print()
    print("Java prefs (examples, edit via client UI or registry/prefs file):")
    print("  moon-combatbot-brain  0|1|2")
    print("  moon-combatbot-agg    0..100")
    print("  moon-combatbot-depth  2|3")
    print("  moon-combatbot-risk   0..3")


def main() -> int:
    ap = argparse.ArgumentParser(description="Analyze moon-combat-ai.log")
    ap.add_argument(
        "logfile",
        nargs="?",
        type=Path,
        default=None,
        help=f"Log path (default: {default_log_path()})",
    )
    ap.add_argument("--json", type=Path, default=None, help="Write full report as JSON")
    args = ap.parse_args()
    path = args.logfile or default_log_path()
    if not path.is_file():
        print(f"File not found: {path}", file=sys.stderr)
        print("Enable 'Log decisions' in Combat bot window and run some fights.", file=sys.stderr)
        return 1

    rows = list(parse_lines(path))
    rep = analyze(rows)
    if args.json:
        out = dict(rep)
        args.json.write_text(json.dumps(out, indent=2, ensure_ascii=False), encoding="utf-8")
        print(f"Wrote {args.json}")
    print_report(rep)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
