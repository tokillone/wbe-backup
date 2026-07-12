#!/usr/bin/env python3
"""Import a complete WBE workbook through the audited batch import API."""

from __future__ import annotations

import argparse
import json
import os
import sys
import uuid
from pathlib import Path
from urllib.error import HTTPError, URLError
from urllib.parse import urlencode
from urllib.request import Request, urlopen


def api_request(url: str, token: str, body: bytes | None = None, content_type: str | None = None) -> dict:
    headers = {"Authorization": f"Bearer {token}", "Accept": "application/json"}
    if content_type:
        headers["Content-Type"] = content_type
    request = Request(url, data=body, headers=headers, method="POST")
    try:
        with urlopen(request, timeout=300) as response:
            payload = json.loads(response.read().decode("utf-8"))
    except HTTPError as error:
        detail = error.read().decode("utf-8", errors="replace")
        raise RuntimeError(f"API request failed ({error.code}): {detail}") from error
    except URLError as error:
        raise RuntimeError(f"Cannot connect to import API: {error.reason}") from error
    if payload.get("code") != 200:
        raise RuntimeError(payload.get("message") or "Import API returned an error")
    return payload["data"]


def multipart_workbook(path: Path) -> tuple[bytes, str]:
    boundary = f"----wbe-import-{uuid.uuid4().hex}"
    filename = path.name.replace('"', "")
    prefix = (
        f"--{boundary}\r\n"
        f'Content-Disposition: form-data; name="file"; filename="{filename}"\r\n'
        "Content-Type: application/vnd.openxmlformats-officedocument.spreadsheetml.sheet\r\n\r\n"
    ).encode("utf-8")
    suffix = f"\r\n--{boundary}--\r\n".encode("ascii")
    return prefix + path.read_bytes() + suffix, f"multipart/form-data; boundary={boundary}"


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--workbook", required=True, help="Complete .xlsx publication workbook")
    parser.add_argument("--base-url", default=os.getenv("WBE_API_BASE_URL", "http://127.0.0.1:8080/api"))
    parser.add_argument("--token", default=os.getenv("WBE_API_TOKEN"), help="Admin/reviewer/sync bearer token")
    parser.add_argument("--allow-duplicate", action="store_true")
    parser.add_argument("--preview-only", action="store_true")
    args = parser.parse_args()

    workbook = Path(args.workbook).expanduser().resolve()
    if not workbook.is_file() or workbook.suffix.lower() != ".xlsx":
        parser.error(f"Workbook does not exist or is not .xlsx: {workbook}")
    if not args.token:
        parser.error("Provide --token or set WBE_API_TOKEN")

    base_url = args.base_url.rstrip("/")
    body, content_type = multipart_workbook(workbook)
    query = urlencode({"allowDuplicate": str(args.allow_duplicate).lower()})
    preview = api_request(
        f"{base_url}/data-uploads/preview?{query}",
        args.token,
        body,
        content_type,
    )
    batch = preview["batch"]
    upload_id = batch["uploadId"]
    summaries = ", ".join(
        f"{item['sheetName']}={item['totalRows']}" for item in preview.get("sheetSummaries", [])
    )
    print(f"Previewed upload {upload_id}: {summaries or batch['totalRows']}")
    if batch.get("errorRows", 0) or preview.get("headerErrors"):
        raise RuntimeError(f"Upload {upload_id} has blocking validation errors; review it in the application")
    if args.preview_only:
        return

    api_request(f"{base_url}/data-uploads/{upload_id}/approve", args.token, b"")
    result = api_request(f"{base_url}/data-uploads/{upload_id}/sync", args.token, b"")
    inserted = ", ".join(f"{key}={value}" for key, value in result.get("insertedRowsBySheet", {}).items())
    print(f"Synced upload {upload_id} atomically: {inserted or result.get('insertedRows', 0)}")


if __name__ == "__main__":
    try:
        main()
    except RuntimeError as error:
        print(str(error), file=sys.stderr)
        raise SystemExit(1) from error
