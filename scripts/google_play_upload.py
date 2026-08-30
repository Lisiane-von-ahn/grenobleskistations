#!/usr/bin/env python3
from __future__ import annotations

import argparse
import datetime as dt
from pathlib import Path

from google.oauth2 import service_account
from googleapiclient.discovery import build
from googleapiclient.errors import HttpError
from googleapiclient.http import MediaFileUpload


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Upload Android AAB to Google Play track")
    parser.add_argument("--service-account", required=True, help="Path to service account JSON")
    parser.add_argument("--package-name", required=True, help="Android package name")
    parser.add_argument("--track", default="internal", help="Play track: internal, alpha, beta, production")
    parser.add_argument(
        "--release-status",
        default="completed",
        choices=["draft", "completed", "halted", "inProgress"],
        help="Release status",
    )
    parser.add_argument(
        "--rollout-percentage",
        default="100",
        help="Staged rollout percentage 1-100. <100 sets inProgress on supported tracks",
    )
    parser.add_argument("--aab", required=True, help="Path to .aab file")
    parser.add_argument("--release-name", default="", help="Optional release name")
    parser.add_argument("--changes-not-sent-for-review", action="store_true", help="Set changesNotSentForReview")

    parser.add_argument("--default-language", default="en-US", help="Play listing language code")
    parser.add_argument("--listing-title", default="", help="Store listing title")
    parser.add_argument("--short-description", default="", help="Store listing short description")
    parser.add_argument("--full-description", default="", help="Store listing full description")
    parser.add_argument("--privacy-policy-url", default="", help="Privacy policy URL")

    parser.add_argument("--first-deploy", action="store_true", help="Force first deploy checks")
    parser.add_argument(
        "--ack-manual-compliance",
        action="store_true",
        help="Acknowledge manual tasks (content rating, age, data safety, app access, ads)",
    )
    return parser.parse_args()


def has_existing_release(service, package_name: str, edit_id: str) -> bool:
    tracks = service.edits().tracks().list(packageName=package_name, editId=edit_id).execute()
    for track in tracks.get("tracks", []):
        if track.get("releases"):
            return True
    return False


def ensure_first_deploy_requirements(args: argparse.Namespace, detected_first_deploy: bool) -> None:
    if not (args.first_deploy or detected_first_deploy):
        return

    missing = []
    if not args.listing_title.strip():
        missing.append("--listing-title")
    if not args.short_description.strip():
        missing.append("--short-description")
    if not args.full_description.strip():
        missing.append("--full-description")
    if not args.privacy_policy_url.strip():
        missing.append("--privacy-policy-url")

    if missing:
        raise SystemExit("[ERROR] First deploy detected: missing listing metadata: " + ", ".join(missing))

    if not args.ack_manual_compliance:
        raise SystemExit(
            "[ERROR] First deploy requires manual Play Console compliance tasks. "
            "Re-run with --ack-manual-compliance after completing the checklist."
        )


def update_listing(service, package_name: str, edit_id: str, args: argparse.Namespace) -> None:
    if not any(
        [
            args.listing_title.strip(),
            args.short_description.strip(),
            args.full_description.strip(),
        ]
    ):
        print(f"[INFO] Skipping listing metadata update ({args.default_language})")
        return

    body: dict[str, str] = {}
    if args.listing_title.strip():
        body["title"] = args.listing_title.strip()
    if args.short_description.strip():
        body["shortDescription"] = args.short_description.strip()
    if args.full_description.strip():
        body["fullDescription"] = args.full_description.strip()

    service.edits().listings().update(
        packageName=package_name,
        editId=edit_id,
        language=args.default_language,
        body=body,
    ).execute()
    print(f"[INFO] Updated listing metadata ({args.default_language})")



def main() -> None:
    args = parse_args()

    aab_path = Path(args.aab)
    if not aab_path.exists():
        raise SystemExit(f"[ERROR] AAB not found: {aab_path}")

    try:
        rollout_percentage = float(args.rollout_percentage)
    except ValueError as exc:
        raise SystemExit("[ERROR] --rollout-percentage must be a number between 1 and 100") from exc

    if rollout_percentage <= 0 or rollout_percentage > 100:
        raise SystemExit("[ERROR] --rollout-percentage must be between 1 and 100")

    release_status = args.release_status
    user_fraction = None
    if args.track != "internal" and rollout_percentage < 100:
        release_status = "inProgress"
        user_fraction = round(rollout_percentage / 100.0, 4)
    elif args.track == "internal" and rollout_percentage < 100:
        print("[WARN] rollout < 100 ignored on internal track")

    scopes = ["https://www.googleapis.com/auth/androidpublisher"]

    try:
        creds = service_account.Credentials.from_service_account_file(args.service_account, scopes=scopes)
        service = build("androidpublisher", "v3", credentials=creds, cache_discovery=False)

        edit = service.edits().insert(packageName=args.package_name, body={}).execute()
        edit_id = edit["id"]
        print(f"[INFO] Created edit: {edit_id}")

        detected_first_deploy = not has_existing_release(service, args.package_name, edit_id)
        ensure_first_deploy_requirements(args, detected_first_deploy)

        if detected_first_deploy and release_status != "draft":
            print("[WARN] First deploy detected, forcing release status to draft")
            release_status = "draft"
            user_fraction = None

        bundle = service.edits().bundles().upload(
            packageName=args.package_name,
            editId=edit_id,
            media_body=MediaFileUpload(str(aab_path), mimetype="application/octet-stream"),
        ).execute()

        version_code = str(bundle["versionCode"])
        release_name = args.release_name or f"Automated release {dt.datetime.utcnow():%Y-%m-%d %H:%M UTC}"

        release_body: dict[str, object] = {
            "name": release_name,
            "versionCodes": [version_code],
            "status": release_status,
        }
        if user_fraction is not None:
            release_body["userFraction"] = user_fraction

        service.edits().tracks().update(
            packageName=args.package_name,
            editId=edit_id,
            track=args.track,
            body={"releases": [release_body]},
        ).execute()

        update_listing(service, args.package_name, edit_id, args)

        commit_args: dict[str, object] = {
            "packageName": args.package_name,
            "editId": edit_id,
        }
        if args.changes_not_sent_for_review:
            commit_args["changesNotSentForReview"] = True
        service.edits().commit(
            **commit_args,
        ).execute()

        print(f"[OK] Uploaded versionCode={version_code} to track '{args.track}' with status '{release_status}'")

    except HttpError as err:
        status = getattr(err.resp, "status", None)
        body = str(err)

        if status == 404 and "Package not found" in body:
            raise SystemExit(
                "[ERROR] Google Play package not found for this account/project. "
                "Check package name and Play Console permissions."
            )

        if status == 403 and (
            "SERVICE_DISABLED" in body
            or "Android Developer API" in body
            or "androidpublisher.googleapis.com" in body
        ):
            raise SystemExit(
                "[ERROR] Android Publisher API is not enabled for the Google Cloud project linked to this service account."
            )

        raise SystemExit(f"[ERROR] Google Play API request failed ({status}): {body}")


if __name__ == "__main__":
    main()
