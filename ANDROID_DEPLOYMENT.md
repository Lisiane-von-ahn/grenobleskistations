# Android Deployment Guide - Google Play Store

## Overview

Your GrenobleSki Android app is now configured for:
- ✅ **Automatic builds** - Triggered on push to main/master
- ✅ **Signed release APKs** - Pre-signed with your keystore
- ✅ **Google Play Store deployment** - Auto-upload to Internal Testing track
- ✅ **GitHub Releases** - Backup APK artifacts

## Prerequisites

### 1. Android App Already on Google Play Store

Your app (`fr.grenobleski.nativeapp`) must be created in Google Play Console:
- Go to [Google Play Console](https://play.google.com/console)
- You should see "GrenobleSki" app listed
- Internal Testing track must exist (usually pre-created)

### 2. Google Cloud Project & Service Account

Follow these steps **once** to set up Play Store credentials:

#### Step 1: Create Google Cloud Project (if not already done)

1. Go to [Google Cloud Console](https://console.cloud.google.com/)
2. Create new project: `GrenobleSki Android CI/CD`
3. Link to your Google Play Console:
   - In Play Console → Settings → Linked projects
   - Add your Cloud project

#### Step 2: Create Service Account

1. Go to [Cloud Console IAM](https://console.cloud.google.com/iam-admin/serviceaccounts)
2. Click **Create Service Account**:
   - Name: `github-actions-deploy`
   - Description: `CI/CD deployment for GitHub Actions`
   - Click Create & Continue

3. Grant Required Role:
   - Select the new service account
   - Click **Roles** → **Edit roles**
   - Search & add: `Service Account User`
   - Click Save

#### Step 3: Grant Play Store API Permissions

1. In Play Console, go to **Settings** → **Users and permissions**
2. Click **Invite user** (or invite the service account email)
3. Email: (from service account created above)
4. Grant permissions:
   - ✅ Manage releases (for uploads)
   - ✅ View app information
   - ✅ Manage in-app products (if needed)
5. Click Invite

#### Step 4: Create & Download JSON Key

1. Go back to Cloud Console → Service Accounts
2. Select `github-actions-deploy` service account
3. Click **Keys** tab
4. **Add Key** → **Create new key**
5. Select **JSON** format
6. Click **Create**
7. **Important**: Save this file safely - you'll only get it once!

#### Step 5: Encode JSON Key for GitHub

Run this command locally:

```bash
# Make sure you have the JSON key file
cd ~/Downloads
base64 -i github-actions-deploy-key.json -o github-actions-deploy-key-base64.txt

# Print the contents to copy
cat github-actions-deploy-key-base64.txt
```

**On macOS** (different command):
```bash
base64 < github-actions-deploy-key.json > github-actions-deploy-key-base64.txt
cat github-actions-deploy-key-base64.txt
```

This prints a long base64 string - copy it completely.

### Step 6: Add Secret to GitHub

1. Go to GitHub repo → **Settings** → **Secrets and variables** → **Actions**
2. Click **New repository secret**
3. Name: `PLAY_STORE_SERVICE_ACCOUNT_JSON`
4. Value: Paste the entire base64 string from above
5. Click **Add secret**

**⚠️ IMPORTANT:**
- Never paste the actual JSON file into GitHub
- Always use base64-encoded version for CI/CD
- The secret is encrypted and not visible in logs
- Treat it with same care as your keystore password

## Deployment Process

### Automatic Deployment

Every push to `main` or `master` branch automatically:

1. **Builds** Android App Bundle (AAB)
2. **Signs** with your release keystore
3. **Uploads** to Google Play Console
4. **Publishes** to Internal Testing track

### Manual Trigger

If you need to deploy without pushing code:

1. Go to GitHub repo → **Actions**
2. Select **Deploy GrenobleSki Android**
3. Click **Run workflow**
4. Select branch: `main`
5. Click **Run workflow** button

## Tracking Deployments

### GitHub Actions Tab

- Go to **Actions** → **Deploy GrenobleSki Android**
- Each workflow run shows:
  - ✅ Build status
  - ✅ APK/AAB generation
  - ✅ Play Store upload status
  - ✅ Build artifacts (downloadable)

### Google Play Console

1. Go to [Play Console](https://play.google.com/console)
2. Select GrenobleSki app
3. Go to **Testing** → **Internal testing**
4. You'll see:
   - Latest version uploaded
   - Release notes
   - Testers' devices
   - Installation status

## Testing the Deployment

### Add Testers to Internal Testing Track

1. Play Console → GrenobleSki → **Testing** → **Internal testing**
2. Scroll to **"How to join the internal test"**
3. Copy the Link
4. Send to testers (your team)
5. Testers click link → "Become a tester" → Install from Play Store

### Download the App

If you're a tester:
1. Click the Internal Testing link you received
2. Click "Become a tester"
3. Open Google Play Store
4. Search "GrenobleSki"
5. You'll see "Install" button (for internal testing)
6. Install & test

## Version Numbering

Your app versioning is **automatic**:

```
versionName = 1.BUILD_NUMBER.0
versionCode = BUILD_NUMBER

Example:
- GitHub Actions run #42 → v1.42.0 → versionCode 42
- GitHub Actions run #123 → v1.123.0 → versionCode 123
```

This ensures:
- ✅ Every build has unique version
- ✅ Builds are traceable to specific GitHub Actions run
- ✅ Play Store accepts newer versions automatically
- ✅ Users see version progression clearly

## Rollback & Versioning

### If You Need to Revert

1. Go to Play Console → Internal testing
2. Click version number
3. See release history
4. Previous versions are archived (can't directly downgrade, but you can re-release old AABs)

### Promoting to Beta/Production

When ready for wider testing:

1. Play Console → GrenobleSki → **Testing**
2. **Promote release** from Internal → Beta
3. Configure Beta testers
4. Later, promote from Beta → Production

**Note:** Current workflow only deploys to Internal. To change tracks:

Edit `.github/workflows/deploy_mobile.yml`:
```yaml
track: internal    # Change to: beta or production
```

## Troubleshooting

### Build Fails: "Keystore error"

**Cause:** `ANDROID_KEYSTORE_BASE64` secret is invalid

**Fix:**
```bash
# Re-encode your keystore
base64 < grenobleski_android_native/signing/release.keystore
# Copy output to GitHub Secrets
```

### Upload Fails: "Invalid service account"

**Cause:** Service account doesn't have permission

**Fix:**
1. Go to Play Console → Settings → Users & permissions
2. Verify service account email is invited
3. Verify permissions include "Manage releases"
4. Re-download JSON key and re-encode it

### Play Store Won't Accept APK

**Cause:** Signature mismatch or keystore issue

**Fix:**
1. Verify keystore path is correct: `grenobleski_android_native/signing/release.keystore`
2. Verify all 3 secrets (password, alias, key password) match
3. Manually re-sign locally: `./gradlew assembleRelease`
4. Check `app/build.gradle.kts` signing config

### GitHub Secret Not Working

**Cause:** Secret not properly copied or secret key name mismatch

**Fix:**
1. Verify exact secret name: `PLAY_STORE_SERVICE_ACCOUNT_JSON`
2. Verify base64 encoding is complete (no missing characters)
3. Test locally first:
   ```bash
   echo $PLAY_STORE_SERVICE_ACCOUNT_JSON | base64 -d | jq .
   # Should output valid JSON, not errors
   ```

## Security Best Practices

✅ **DO:**
- Keep JSON key safe (treat like password)
- Rotate keys annually
- Use different service accounts for different apps
- Monitor Play Store upload logs for suspicious activity
- Keep GitHub secrets private

❌ **DON'T:**
- Commit JSON keys to git
- Share service account credentials
- Use personal Google accounts for CI/CD
- Use production service account for testing

## Configuration Reference

### Workflow Secrets Required

| Secret Name | Value | Where to Get |
|-------------|-------|-------------|
| `ANDROID_KEYSTORE_BASE64` | Base64-encoded keystore file | `base64 grenobleski_android_native/signing/release.keystore` |
| `ANDROID_STORE_PASSWORD` | Keystore password | Your password for release.keystore |
| `ANDROID_KEY_ALIAS` | Key alias in keystore | Usually `android` or `grenobleski` |
| `ANDROID_KEY_PASSWORD` | Password for key | Usually same as keystore password |
| `PLAY_STORE_SERVICE_ACCOUNT_JSON` | Base64-encoded JSON key | Cloud Console → Service Accounts → Keys → JSON |

### Workflow Inputs

| Variable | Value | Purpose |
|----------|-------|---------|
| `BUILD_NUMBER` | Auto (GitHub Actions run #) | Version code |
| `VERSION_NAME` | Auto (1.{run_number}.0) | Version string |
| `packageName` | `fr.grenobleski.nativeapp` | Play Store package ID |
| `track` | `internal` | Deployment track |

## Next Steps

1. **Create service account** (follow "Prerequisites" section above)
2. **Add `PLAY_STORE_SERVICE_ACCOUNT_JSON` secret** to GitHub
3. **Push to main branch** to trigger first deployment
4. **Monitor** GitHub Actions and Play Console
5. **Invite testers** to Internal Testing track
6. **Promote to Beta** when ready for wider testing

## Support

- **Workflow failed?** Check: GitHub Actions → Deploy job → Logs
- **Upload rejected?** Check: Play Console → Release history → Details
- **Version conflict?** Ensure versionCode is higher than current Play Store version

---

**Last Updated:** March 24, 2026
**Learn More:**
- [Google Play Console Help](https://support.google.com/googleplay/android-developer)
- [Service Account Setup](https://cloud.google.com/docs/authentication/service-accounts)
- [GitHub Actions Android Workflow](https://github.com/r0adkll/upload-google-play)
