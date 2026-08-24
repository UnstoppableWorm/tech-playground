#!/usr/bin/env bash

set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
project_dir="$(cd "$script_dir/.." && pwd)"
workspace_dir="$(cd "$project_dir/.." && pwd)"
report_path="$project_dir/build/reports/experiments/experiment-results.txt"
data_path="$project_dir/build/reports/experiments/experiment-results.json"
chart_dir="$project_dir/docs/images"

cd "$project_dir"
./gradlew experimentTest
python3 "$workspace_dir/experiment-reporter/render.py" --input "$data_path" --output "$chart_dir"

printf '\nReport generated: %s\n\n' "$report_path"
cat "$report_path"
printf '\nCharts generated: %s\n' "$chart_dir"
