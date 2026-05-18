# Google Play - First Deploy Checklist

Even with API automation, these items are manual in Play Console:

1. Content rating questionnaire
2. Target audience / age group
3. Data safety form
4. App access declaration (if login required)
5. Ads declaration
6. Privacy policy URL
   - `https://www.grenobleski.fr/privacy`

When these are done, you can acknowledge in script mode:

- Shell: `GOOGLE_PLAY_ACK_MANUAL_COMPLIANCE=true`
- Python: `--ack-manual-compliance`

If first deploy is detected and this acknowledgment is missing, deployment is intentionally blocked.
