# SpineTrack Security Rotation Guide

## 1) Rotate exposed service account key now

1. Go to Firebase Console -> Project Settings -> Service Accounts -> Manage service accounts in Google Cloud.
2. Open service account `firebase-adminsdk-...@...iam.gserviceaccount.com`.
3. In **Keys**, delete the exposed key (the one with leaked `private_key_id`).
4. Create a new JSON key and download it.
5. Save the new file as `serviceAccount.json` in a secure path outside git tracking.

## 2) Wire the new JSON into script runtime

The script now reads this variable:

- `SPINETRACK_SERVICE_ACCOUNT_PATH`

Examples:

```bash
export SPINETRACK_SERVICE_ACCOUNT_PATH=/home/pi/spinetrack/serviceAccount.json
```

Or keep file beside the script as `serviceAccount.json`.

## 3) MQTT secret rotation

If MQTT credentials were exposed, rotate username/password in HiveMQ and set:

- `SPINETRACK_MQTT_HOST`
- `SPINETRACK_MQTT_USERNAME`
- `SPINETRACK_MQTT_PASSWORD`

## 4) JSON guidance

Do not hand-edit key material. Replace the full JSON file with the new one from GCP.
Only these metadata values naturally change after rotation:

- `private_key_id`
- `private_key`
- sometimes `client_x509_cert_url`

These usually stay the same for the same service account:

- `project_id`
- `client_email`
- `token_uri`
- `auth_uri`

Use `serviceAccount.template.json` only as a schema reference.

