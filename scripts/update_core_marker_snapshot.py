#!/usr/bin/env python3
"""Replace the backend core-marker seed JSON from the generated prototype assets."""

from __future__ import annotations

import argparse
import json
from pathlib import Path


def load_javascript_payload(path: Path) -> object:
    source = path.read_text(encoding="utf-8")
    assignment = source.find("=")
    if assignment < 0:
        raise ValueError(f"{path} does not contain a JavaScript assignment")
    return json.loads(source[assignment + 1 :].strip().removesuffix(";"))


def main() -> None:
    backend_root = Path(__file__).resolve().parents[1]
    workspace_root = backend_root.parent
    default_asset_dir = (
        workspace_root
        / "核心标记物优先级识别模块_开发交接包_20260728"
        / "01_可运行原型"
        / "_assets"
        / "core-marker-priority"
    )
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--asset-dir", type=Path, default=default_asset_dir)
    parser.add_argument(
        "--output-dir",
        type=Path,
        default=backend_root / "src/main/resources/data",
    )
    args = parser.parse_args()

    overview = load_javascript_payload(args.asset_dir / "overview.js")
    details = load_javascript_payload(args.asset_dir / "details.js")
    rows = overview.get("rows", [])
    ids = {str(row.get("id")) for row in rows}
    if len(rows) != 516 or len(ids) != len(rows):
        raise ValueError("core-marker overview must contain 516 unique marker rows")
    if set(details) != ids:
        raise ValueError("core-marker details must have a one-to-one marker ID contract")

    args.output_dir.mkdir(parents=True, exist_ok=True)
    outputs = {
        "core-marker-priority-overview.json": overview,
        "core-marker-priority-details.json": details,
    }
    for name, payload in outputs.items():
        target = args.output_dir / name
        target.write_text(
            json.dumps(payload, ensure_ascii=False, separators=(",", ":")),
            encoding="utf-8",
        )
        print(f"updated {target} ({target.stat().st_size} bytes)")


if __name__ == "__main__":
    main()
