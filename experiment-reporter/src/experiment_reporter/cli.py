import argparse
import json
from pathlib import Path

from experiment_reporter.renderer import render_report


def main() -> None:
    parser = argparse.ArgumentParser(description="Render experiment result charts")
    parser.add_argument("--input", required=True, type=Path, help="Experiment result JSON")
    parser.add_argument("--output", required=True, type=Path, help="Chart output directory")
    args = parser.parse_args()

    with args.input.open(encoding="utf-8") as source:
        report = json.load(source)

    generated = render_report(report, args.output)
    for path in generated:
        print(path.resolve())
