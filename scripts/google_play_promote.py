#!/usr/bin/env python3
from __future__ import annotations

import argparse

from google.oauth2 import service_account
from googleapiclient.discovery import build


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Promote Google Play release between tracks without rebuild")
    parser.add_argument("--service-account", required=True, help="Path to service account JSON")
    parser.add_argument("--package-name", required=True, help="Android package name")
    parser.add_argument("--from-track", required=True, help="Source track, e.g. internal")
    parser.add_argument("--to-track", required=True, help="Target track, e.g. production")
    parser.add_argument(
        "--release-status",
        default="completed",
        choices=["draft", "completed", "halted"],
        help="Target release status",
    )
    parser.add_argument("--release-name", default="", help="Optional target release name")
    return parser.parse_args()


def pick_latest_release(track_payload: dict) -> dict:
    releases = track_payload.get("releases", [])
    if not releases:
        raise SystemExit("[ERROR] No releases found on source track")

    def sort_key(release: dict) -> int:
        version_codes = release.get("versionCodes", [])
        numbers = []
        for value in version_codes:
            try:
                numbers.append(int(value))
            except Exception:
                continue
        return max(numbers) if numbers else -1

    return sorted(releases, key=sort_key, reverse=True)[0]


def main() -> None:
    args = parse_args()

    scopes = ["https://www.googleapis.com/auth/androidpublisher"]
    creds = service_account.Credentials.from_service_account_file(args.service_account, scopes=scopes)
    service = build("androidpublisher", "v3", credentials=creds, cache_discovery=False)

    edit = service.edits().insert(packageName=args.package_name, body={}).execute()
    edit_id = edit["id"]
    print(f"[INFO] Created edit: {edit_id}")

    source_track = service.edits().tracks().get(
        packageName=args.package_name,
        editId=edit_id,
        track=args.from_track,
    ).execute()

    latest_release = pick_latest_release(source_track)
    version_codes = [str(v) for v in latest_release.get("versionCodes", [])]
    if not version_codes:
        raise SystemExit("[ERROR] Source release has no version codes")

    release_name = args.release_name or latest_release.get("name") or f"Promoted from {args.from_track}"

    body = {
        "releases": [
            {
                "name": release_name,
                "versionCodes": version_codes,
                "status": args.release_status,
            }
        ]
    }

    service.edits().tracks().update(
        packageName=args.package_name,
        editId=edit_id,
        track=args.to_track,
        body=body,
    ).execute()

    service.edits().commit(packageName=args.package_name, editId=edit_id).execute()
    print(f"[OK] Promoted versions {version_codes} from '{args.from_track}' to '{args.to_track}'")


if __name__ == "__main__":
    main()
