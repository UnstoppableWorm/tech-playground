from html import escape
from pathlib import Path
import re
from typing import Any, Dict, List


WIDTH = 960
PANEL_HEIGHT = 330
COLORS = ("#2563eb", "#f97316", "#16a34a", "#9333ea", "#dc2626", "#0891b2")
SAFE_ID = re.compile(r"^[a-z0-9]+(?:-[a-z0-9]+)*$")


def render_report(report: Dict[str, Any], output_directory: Path) -> List[Path]:
    _validate_report(report)
    output_directory.mkdir(parents=True, exist_ok=True)
    generated = []
    for chart in report["charts"]:
        results = [result for result in report["results"] if result["group"] == chart["group"]]
        if not results:
            raise ValueError(f"Chart group has no results: {chart['group']}")
        chart_directory = output_directory / chart.get("directory", "")
        chart_directory.mkdir(parents=True, exist_ok=True)
        target = chart_directory / f"{chart['id']}.svg"
        target.write_text(_render_chart(report, chart, results), encoding="utf-8")
        generated.append(target)
    return generated


def _validate_report(report: Dict[str, Any]) -> None:
    if report.get("schemaVersion") != 1:
        raise ValueError("Only experiment report schemaVersion 1 is supported")
    for field in ("lab", "title", "charts", "results"):
        if field not in report:
            raise ValueError(f"Missing report field: {field}")
    for chart in report["charts"]:
        if not SAFE_ID.fullmatch(chart.get("id", "")):
            raise ValueError(f"Invalid chart id: {chart.get('id')}")
        if "directory" in chart and not SAFE_ID.fullmatch(chart["directory"]):
            raise ValueError(f"Invalid chart directory: {chart['directory']}")
        if not chart.get("metrics"):
            raise ValueError(f"Chart has no metrics: {chart['id']}")
        if chart.get("type", "bar") == "line":
            if not chart.get("x", {}).get("key") or not chart.get("series"):
                raise ValueError(f"Line chart requires x and series definitions: {chart['id']}")
        elif chart.get("type", "bar") != "bar":
            raise ValueError(f"Unsupported chart type: {chart.get('type')}")


def _render_chart(report: Dict[str, Any], chart: Dict[str, Any], results: List[Dict[str, Any]]) -> str:
    metrics = chart["metrics"]
    is_line_chart = chart.get("type", "bar") == "line"
    header_height = 112 if is_line_chart else 92
    height = header_height + PANEL_HEIGHT * len(metrics)
    parts = [
        f'<svg xmlns="http://www.w3.org/2000/svg" width="{WIDTH}" height="{height}" '
        f'viewBox="0 0 {WIDTH} {height}" role="img" aria-labelledby="title description">',
        "<style>text{font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',sans-serif;fill:#172033}"
        ".title{font-size:24px;font-weight:700}.subtitle{font-size:13px;fill:#64748b}"
        ".axis{font-size:12px;fill:#64748b}.label{font-size:13px;font-weight:600}"
        ".value{font-size:13px;font-weight:700}.grid{stroke:#dbe3ef;stroke-width:1}"
        ".frame{fill:#fff;stroke:#cbd5e1;stroke-width:1}</style>",
        f'<title id="title">{escape(chart["title"])}</title>',
        f'<desc id="description">Comparison chart generated from {escape(report["lab"])} experiment results.</desc>',
        '<rect width="100%" height="100%" fill="#ffffff"/>',
        f'<text class="title" x="48" y="38">{escape(chart["title"])}</text>',
        f'<text class="subtitle" x="48" y="62">{escape(report["title"])}</text>',
    ]
    if is_line_chart:
        parts.extend(_render_legend(chart))
    for index, metric in enumerate(metrics):
        top = header_height - 10 + index * PANEL_HEIGHT
        if is_line_chart:
            parts.extend(_render_line_metric_panel(chart, metric, results, top))
        else:
            parts.extend(_render_bar_metric_panel(metric, results, top))
    parts.append("</svg>\n")
    return "\n".join(parts)


def _render_bar_metric_panel(metric: Dict[str, str], results: List[Dict[str, Any]], top: int) -> List[str]:
    key = metric["key"]
    values = [_numeric(result["metrics"].get(key), key) for result in results]
    maximum = max(values) if values else 0
    scale_max = maximum * 1.12 if maximum > 0 else 1
    left, right = 92, WIDTH - 42
    chart_top, chart_bottom = top + 48, top + 245
    chart_width = right - left
    slot_width = chart_width / len(results)
    bar_width = min(150, slot_width * 0.54)
    elements = [
        f'<text class="label" x="48" y="{top + 22}">{escape(metric["label"])} ({escape(metric["unit"])})</text>',
        f'<rect class="frame" x="{left}" y="{chart_top}" width="{chart_width}" height="{chart_bottom - chart_top}"/>',
    ]
    for tick_index in range(5):
        ratio = tick_index / 4
        value = scale_max * ratio
        y = chart_bottom - (chart_bottom - chart_top) * ratio
        elements.append(f'<line class="grid" x1="{left}" y1="{y:.1f}" x2="{right}" y2="{y:.1f}"/>')
        elements.append(f'<text class="axis" x="{left - 10}" y="{y + 4:.1f}" text-anchor="end">{_format_number(value)}</text>')

    for index, (result, value) in enumerate(zip(results, values)):
        center = left + slot_width * (index + 0.5)
        bar_height = (chart_bottom - chart_top) * value / scale_max
        bar_x = center - bar_width / 2
        bar_y = chart_bottom - bar_height
        color = COLORS[index % len(COLORS)]
        elements.append(
            f'<rect x="{bar_x:.1f}" y="{bar_y:.1f}" width="{bar_width:.1f}" height="{bar_height:.1f}" fill="{color}"/>'
        )
        elements.append(
            f'<text class="value" x="{center:.1f}" y="{max(chart_top + 15, bar_y - 8):.1f}" text-anchor="middle">'
            f'{_format_number(value)} {escape(metric["unit"])}</text>'
        )
        elements.append(
            f'<text class="axis" x="{center:.1f}" y="{chart_bottom + 25}" text-anchor="middle">'
            f'{escape(result["scenario"])}</text>'
        )
    return elements


def _render_legend(chart: Dict[str, Any]) -> List[str]:
    elements = []
    x = 48
    for index, series in enumerate(chart["series"]):
        color = COLORS[index % len(COLORS)]
        elements.append(f'<line x1="{x}" y1="84" x2="{x + 22}" y2="84" stroke="{color}" stroke-width="3"/>')
        elements.append(f'<circle cx="{x + 11}" cy="84" r="4" fill="{color}"/>')
        elements.append(f'<text class="axis" x="{x + 30}" y="88">{escape(series["label"])}</text>')
        x += 118
    return elements


def _render_line_metric_panel(
    chart: Dict[str, Any], metric: Dict[str, str], results: List[Dict[str, Any]], top: int
) -> List[str]:
    metric_key = metric["key"]
    x_key = chart["x"]["key"]
    x_values = sorted({_numeric(result["parameters"].get(x_key), x_key) for result in results})
    values = [_numeric(result["metrics"].get(metric_key), metric_key) for result in results]
    maximum = max(values) if values else 0
    scale_max = maximum * 1.12 if maximum > 0 else 1
    left, right = 92, WIDTH - 42
    chart_top, chart_bottom = top + 48, top + 245
    chart_width = right - left
    elements = [
        f'<text class="label" x="48" y="{top + 22}">{escape(metric["label"])} ({escape(metric["unit"])})</text>',
        f'<rect class="frame" x="{left}" y="{chart_top}" width="{chart_width}" height="{chart_bottom - chart_top}"/>',
    ]
    for tick_index in range(5):
        ratio = tick_index / 4
        value = scale_max * ratio
        y = chart_bottom - (chart_bottom - chart_top) * ratio
        elements.append(f'<line class="grid" x1="{left}" y1="{y:.1f}" x2="{right}" y2="{y:.1f}"/>')
        elements.append(
            f'<text class="axis" x="{left - 10}" y="{y + 4:.1f}" text-anchor="end">{_format_number(value)}</text>'
        )

    def x_position(value: float) -> float:
        if len(x_values) == 1:
            return left + chart_width / 2
        return left + x_values.index(value) * chart_width / (len(x_values) - 1)

    for value in x_values:
        x = x_position(value)
        elements.append(f'<line class="grid" x1="{x:.1f}" y1="{chart_top}" x2="{x:.1f}" y2="{chart_bottom}"/>')
        elements.append(
            f'<text class="axis" x="{x:.1f}" y="{chart_bottom + 24}" text-anchor="middle">{_format_number(value)}</text>'
        )
    elements.append(
        f'<text class="axis" x="{(left + right) / 2:.1f}" y="{chart_bottom + 47}" text-anchor="middle">'
        f'{escape(chart["x"]["label"])}</text>'
    )

    for series_index, series in enumerate(chart["series"]):
        series_results = sorted(
            (result for result in results if result.get("series") == series["value"]),
            key=lambda result: _numeric(result["parameters"].get(x_key), x_key),
        )
        if not series_results:
            raise ValueError(f"Series has no results: {series['value']}")
        points = []
        for result in series_results:
            x_value = _numeric(result["parameters"].get(x_key), x_key)
            y_value = _numeric(result["metrics"].get(metric_key), metric_key)
            x = x_position(x_value)
            y = chart_bottom - (chart_bottom - chart_top) * y_value / scale_max
            points.append((x, y, y_value))
        color = COLORS[series_index % len(COLORS)]
        point_text = " ".join(f"{x:.1f},{y:.1f}" for x, y, _ in points)
        elements.append(
            f'<polyline points="{point_text}" fill="none" stroke="{color}" stroke-width="3" stroke-linejoin="round"/>'
        )
        label_offset = -10 if series_index % 2 == 0 else 18
        for x, y, value in points:
            elements.append(f'<circle cx="{x:.1f}" cy="{y:.1f}" r="5" fill="{color}"/>')
            elements.append(
                f'<text class="value" x="{x:.1f}" y="{max(chart_top + 13, y + label_offset):.1f}" '
                f'text-anchor="middle">{_format_number(value)}</text>'
            )
    return elements


def _numeric(value: Any, key: str) -> float:
    if not isinstance(value, (int, float)) or isinstance(value, bool):
        raise ValueError(f"Metric must be numeric: {key}")
    return float(value)


def _format_number(value: float) -> str:
    if value >= 1_000_000:
        return f"{value / 1_000_000:.1f}M"
    if value >= 1_000:
        return f"{value / 1_000:.1f}k"
    if value.is_integer():
        return str(int(value))
    return f"{value:.1f}"
