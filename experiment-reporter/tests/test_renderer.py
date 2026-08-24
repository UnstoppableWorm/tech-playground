from pathlib import Path
import sys
import tempfile
import unittest

sys.path.insert(0, str(Path(__file__).parents[1] / "src"))

from experiment_reporter.renderer import render_report


class RendererTests(unittest.TestCase):

    def test_renders_one_svg_per_chart(self):
        report = {
            "schemaVersion": 1,
            "lab": "sample-lab",
            "title": "Sample experiment",
            "charts": [{
                "id": "elapsed-time",
                "title": "Elapsed time",
                "group": "GROUP",
                "metrics": [{"key": "elapsedMs", "label": "Execution time", "unit": "ms"}],
            }],
            "results": [
                {"group": "GROUP", "scenario": "baseline", "metrics": {"elapsedMs": 100}},
                {"group": "GROUP", "scenario": "concurrent", "metrics": {"elapsedMs": 25}},
            ],
        }

        with tempfile.TemporaryDirectory() as directory:
            generated = render_report(report, Path(directory))
            svg = generated[0].read_text(encoding="utf-8")

        self.assertEqual(["elapsed-time.svg"], [path.name for path in generated])
        self.assertIn("baseline", svg)
        self.assertIn("100 ms", svg)
        self.assertIn("concurrent", svg)

    def test_rejects_path_traversal_in_chart_id(self):
        report = {
            "schemaVersion": 1,
            "lab": "sample-lab",
            "title": "Sample experiment",
            "charts": [{"id": "../outside", "group": "GROUP", "metrics": [{"key": "value"}]}],
            "results": [],
        }

        with tempfile.TemporaryDirectory() as directory:
            with self.assertRaisesRegex(ValueError, "Invalid chart id"):
                render_report(report, Path(directory))

    def test_renders_line_series_over_numeric_parameter(self):
        report = {
            "schemaVersion": 1,
            "lab": "sample-lab",
            "title": "Sample experiment",
            "charts": [{
                "id": "thread-comparison",
                "title": "Thread comparison",
                "group": "GROUP",
                "type": "line",
                "x": {"key": "concurrency", "label": "Concurrency"},
                "series": [
                    {"value": "platform", "label": "Platform"},
                    {"value": "virtual", "label": "Virtual"},
                ],
                "metrics": [{"key": "elapsedMs", "label": "Execution time", "unit": "ms"}],
            }],
            "results": [
                {"group": "GROUP", "series": "platform", "scenario": "platform-4",
                 "parameters": {"concurrency": 4}, "metrics": {"elapsedMs": 100}},
                {"group": "GROUP", "series": "platform", "scenario": "platform-40",
                 "parameters": {"concurrency": 40}, "metrics": {"elapsedMs": 20}},
                {"group": "GROUP", "series": "virtual", "scenario": "virtual-4",
                 "parameters": {"concurrency": 4}, "metrics": {"elapsedMs": 98}},
                {"group": "GROUP", "series": "virtual", "scenario": "virtual-40",
                 "parameters": {"concurrency": 40}, "metrics": {"elapsedMs": 18}},
            ],
        }

        with tempfile.TemporaryDirectory() as directory:
            generated = render_report(report, Path(directory))
            svg = generated[0].read_text(encoding="utf-8")

        self.assertEqual(2, svg.count("<polyline"))
        self.assertIn("Concurrency", svg)
        self.assertIn("Platform", svg)
        self.assertIn("Virtual", svg)


if __name__ == "__main__":
    unittest.main()
