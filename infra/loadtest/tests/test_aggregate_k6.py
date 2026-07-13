"""Tests for the centralized k6 comparison gate."""

import importlib.util
import types
import unittest
from pathlib import Path


MODULE_PATH = Path(__file__).parents[1] / "aggregate_k6.py"
SPEC = importlib.util.spec_from_file_location("aggregate_k6", MODULE_PATH)
AGGREGATE_K6 = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(AGGREGATE_K6)


class AggregateK6GateTest(unittest.TestCase):

    def setUp(self):
        self.arguments = types.SimpleNamespace(
            cursor_p95_limit_ms=100.0,
            cursor_p99_limit_ms=200.0,
            min_p95_improvement=3.0,
            min_p99_improvement=3.0,
            max_baseline_regression_ratio=1.20,
        )
        self.summary = {
            "modes": {
                "offset": {
                    "medianP95Ms": 84.0,
                    "medianP99Ms": 94.0,
                    "maxFailureRate": 0.0,
                    "totalDroppedIterations": 0,
                },
                "cursor": {
                    "medianP95Ms": 11.0,
                    "medianP99Ms": 13.0,
                    "maxFailureRate": 0.0,
                    "totalDroppedIterations": 0,
                },
            },
            "p95ImprovementFactor": 84.0 / 11.0,
            "p99ImprovementFactor": 94.0 / 13.0,
        }
        self.baseline = {
            "modes": {
                "cursor": {"medianP95Ms": 11.17, "medianP99Ms": 13.03}
            }
        }

    def test_healthy_cursor_comparison_passes_all_gates(self):
        violations = AGGREGATE_K6.evaluate_gates(
            self.summary, self.arguments, self.baseline)

        self.assertEqual([], violations)

    def test_cursor_baseline_regression_fails_even_below_absolute_slo(self):
        self.summary["modes"]["cursor"]["medianP95Ms"] = 20.0
        self.summary["p95ImprovementFactor"] = 84.0 / 20.0

        violations = AGGREGATE_K6.evaluate_gates(
            self.summary, self.arguments, self.baseline)

        self.assertTrue(any("baseline regression" in item for item in violations))

    def test_insufficient_relative_improvement_and_dropped_iterations_fail(self):
        self.summary["p95ImprovementFactor"] = 2.5
        self.summary["modes"]["cursor"]["totalDroppedIterations"] = 1

        violations = AGGREGATE_K6.evaluate_gates(
            self.summary, self.arguments, self.baseline)

        self.assertTrue(any("improvement" in item for item in violations))
        self.assertTrue(any("dropped iterations" in item for item in violations))


if __name__ == "__main__":
    unittest.main()
