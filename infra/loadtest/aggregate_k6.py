#!/usr/bin/env python3
"""Aggregate GardenDoctor k6 comparison results."""

import argparse
import json
import re
import statistics
from pathlib import Path


DIARY_P99_PATTERN = re.compile(
    r"\{\s*endpoint:diary-read\s*\}.*?p\(99\)=([0-9.]+)(\u00b5s|ms|s)"
)


def parse_arguments():
    parser = argparse.ArgumentParser(description="Aggregate alternating k6 Diary comparison runs.")
    parser.add_argument("--result-dir", required=True, type=Path)
    parser.add_argument("--batch-id", required=True)
    parser.add_argument("--rounds", required=True, type=int)
    parser.add_argument("--baseline", type=Path)
    parser.add_argument("--cursor-p95-limit-ms", type=float, default=100.0)
    parser.add_argument("--cursor-p99-limit-ms", type=float, default=200.0)
    parser.add_argument("--min-p95-improvement", type=float, default=3.0)
    parser.add_argument("--min-p99-improvement", type=float, default=3.0)
    parser.add_argument("--max-baseline-regression-ratio", type=float, default=1.20)
    return parser.parse_args()


def to_milliseconds(value, unit):
    if unit == "ms":
        return value
    if unit == "s":
        return value * 1000
    return value / 1000


def read_p99(log_path):
    match = DIARY_P99_PATTERN.search(log_path.read_text())
    if not match:
        raise ValueError(f"Could not find Diary p99 in {log_path}")
    return to_milliseconds(float(match.group(1)), match.group(2))


def read_sample(summary_path):
    document = json.loads(summary_path.read_text())
    metrics = {item["name"]: item for item in document["results"]["metrics"]}
    duration = metrics["http_req_duration{endpoint:diary-read}"]["values"]
    failed = metrics["http_req_failed"]["values"]
    iterations = metrics["iterations"]["values"]["count"]
    dropped = metrics.get("dropped_iterations", {"values": {"count": 0}})["values"]["count"]
    return {
        "file": summary_path.name,
        "iterations": iterations,
        "p95Ms": duration["p95"],
        # k6 v2.0 CLI emits p99 but omits it from its new JSON summary format.
        "p99Ms": read_p99(summary_path.with_suffix(".log")),
        "failureRate": failed["rate"],
        "droppedIterations": dropped,
    }


def summarize_mode(result_dir, batch_id, mode, rounds):
    samples = [
        read_sample(path)
        for path in sorted(result_dir.glob(f"{batch_id}-r*-{mode}.json"))
    ]
    if len(samples) != rounds:
        raise ValueError(f"Expected {rounds} {mode} summaries, found {len(samples)}")
    return {
        "samples": samples,
        "medianP95Ms": statistics.median(item["p95Ms"] for item in samples),
        "medianP99Ms": statistics.median(item["p99Ms"] for item in samples),
        "maxFailureRate": max(item["failureRate"] for item in samples),
        "totalDroppedIterations": sum(item["droppedIterations"] for item in samples),
        "totalIterations": sum(item["iterations"] for item in samples),
    }


def evaluate_gates(summary, args, baseline=None):
    offset = summary["modes"]["offset"]
    cursor = summary["modes"]["cursor"]
    violations = []

    if cursor["medianP95Ms"] >= args.cursor_p95_limit_ms:
        violations.append(
            f"cursor median p95 {cursor['medianP95Ms']:.2f}ms must be below "
            f"{args.cursor_p95_limit_ms:.2f}ms"
        )
    if cursor["medianP99Ms"] >= args.cursor_p99_limit_ms:
        violations.append(
            f"cursor median p99 {cursor['medianP99Ms']:.2f}ms must be below "
            f"{args.cursor_p99_limit_ms:.2f}ms"
        )
    if summary["p95ImprovementFactor"] < args.min_p95_improvement:
        violations.append(
            f"p95 improvement {summary['p95ImprovementFactor']:.2f}x must be at least "
            f"{args.min_p95_improvement:.2f}x"
        )
    if summary["p99ImprovementFactor"] < args.min_p99_improvement:
        violations.append(
            f"p99 improvement {summary['p99ImprovementFactor']:.2f}x must be at least "
            f"{args.min_p99_improvement:.2f}x"
        )

    for mode, item in summary["modes"].items():
        if item["maxFailureRate"] >= 0.01:
            violations.append(f"{mode} failure rate must stay below 1%")
        if item["totalDroppedIterations"] != 0:
            violations.append(f"{mode} dropped iterations must be 0")

    if baseline is not None:
        baseline_cursor = baseline["modes"]["cursor"]
        for percentile in ("P95", "P99"):
            metric = f"median{percentile}Ms"
            ratio = cursor[metric] / baseline_cursor[metric]
            if ratio > args.max_baseline_regression_ratio:
                violations.append(
                    f"cursor {percentile.lower()} baseline regression {ratio:.2f}x exceeds "
                    f"{args.max_baseline_regression_ratio:.2f}x"
                )

    return violations


def main():
    args = parse_arguments()
    summary = {
        "batchId": args.batch_id,
        "rounds": args.rounds,
        "modes": {
            mode: summarize_mode(args.result_dir, args.batch_id, mode, args.rounds)
            for mode in ("offset", "cursor")
        },
    }
    offset = summary["modes"]["offset"]
    cursor = summary["modes"]["cursor"]
    summary["p95ImprovementFactor"] = offset["medianP95Ms"] / cursor["medianP95Ms"]
    summary["p99ImprovementFactor"] = offset["medianP99Ms"] / cursor["medianP99Ms"]

    baseline = None
    if args.baseline is not None:
        baseline = json.loads(args.baseline.read_text())
        summary["baseline"] = str(args.baseline)
    violations = evaluate_gates(summary, args, baseline)
    summary["gates"] = {
        "passed": not violations,
        "violations": violations,
        "cursorP95LimitMs": args.cursor_p95_limit_ms,
        "cursorP99LimitMs": args.cursor_p99_limit_ms,
        "minP95Improvement": args.min_p95_improvement,
        "minP99Improvement": args.min_p99_improvement,
        "maxBaselineRegressionRatio": args.max_baseline_regression_ratio,
    }

    output = args.result_dir / f"{args.batch_id}-comparison.json"
    output.write_text(json.dumps(summary, indent=2) + "\n")

    print("\nmode    runs  iterations  median-p95  median-p99  max-error  dropped")
    for mode in ("offset", "cursor"):
        item = summary["modes"][mode]
        print(
            f"{mode:<7} {args.rounds:>4}  {item['totalIterations']:>10}  "
            f"{item['medianP95Ms']:>9.2f}ms  {item['medianP99Ms']:>9.2f}ms  "
            f"{item['maxFailureRate'] * 100:>8.2f}%  {item['totalDroppedIterations']:>7}"
        )
    print(f"p95 improvement: {summary['p95ImprovementFactor']:.2f}x")
    print(f"p99 improvement: {summary['p99ImprovementFactor']:.2f}x")
    if violations:
        print("performance gate: FAILED")
        for violation in violations:
            print(f"- {violation}")
    else:
        print("performance gate: PASSED")
    print(f"comparison summary: {output}")

    if violations:
        raise SystemExit(1)


if __name__ == "__main__":
    main()
