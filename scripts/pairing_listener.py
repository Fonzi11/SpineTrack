"""
Pairing listener for SpineTrack Raspberry Pi (polling implementation)

How to integrate:
- Import and call start_pairing_listener(raspberry_id) after firebase_admin initialization in your main script.
- The listener polls /pairing/requests/{raspberry_id} and processes new requests.
- It writes ack to /pairing/acks/{raspberry_id}/{clientUid} and updates /devices/{raspberry_id}/meta.
- The script uses firebase_admin db (firebase_admin.initialize_app(...) should already be called).

This file uses a polling loop (safe, does not depend on reference.listen API) and is compatible with typical firebase_admin versions.
"""

import threading
import time
import uuid
import logging
from typing import Optional

try:
    from firebase_admin import db
    FIREBASE_AVAILABLE = True
except Exception:
    FIREBASE_AVAILABLE = False

logger = logging.getLogger("spine_pairing")


def _validate_request(payload: dict) -> (bool, Optional[str]):
    try:
        ts = int(payload.get("timestamp", 0))
        client_uid = payload.get("clientUid")
        if not client_uid:
            return False, "clientUid missing"
        now_ms = int(time.time() * 1000)
        # Allow requests fresher than 5 minutes
        if abs(now_ms - ts) > 5 * 60 * 1000:
            return False, "timestamp too old or in future"
        return True, None
    except Exception as e:
        return False, f"invalid payload: {e}"


def _write_ack(raspberry_id: str, client_uid: str, accepted: bool, message: str, assigned_session_id: Optional[str] = None):
    ack_ref = db.reference(f"pairing/acks/{raspberry_id}/{client_uid}")
    ack_payload = {
        "timestamp": int(time.time() * 1000),
        "accepted": bool(accepted),
        "message": message,
        "deviceId": raspberry_id,
    }
    if assigned_session_id:
        ack_payload["assignedSessionId"] = assigned_session_id
    try:
        ack_ref.set(ack_payload)
        logger.info("Wrote ack for %s -> %s: %s", raspberry_id, client_uid, ack_payload)
    except Exception as e:
        logger.exception("Failed writing ack to RTDB: %s", e)


def _set_device_owner(raspberry_id: str, client_uid: str):
    meta_ref = db.reference(f"devices/{raspberry_id}/meta")
    try:
        meta_ref.update({
            "ownerUid": client_uid,
            "lastSeen": int(time.time() * 1000),
            "status": "paired"
        })
        logger.info("Device %s owner set to %s", raspberry_id, client_uid)
    except Exception as e:
        logger.exception("Failed setting device owner: %s", e)


def _start_calibration(raspberry_id: str, client_uid: str) -> str:
    session_uuid = str(uuid.uuid4())
    assigned_session_id = f"{client_uid}::{session_uuid}"
    calib_ref = db.reference(f"devices/{raspberry_id}/calibration")
    try:
        calib_ref.set({
            "status": "started",
            "startedBy": client_uid,
            "timestamp": int(time.time() * 1000),
            "sessionId": assigned_session_id
        })
        logger.info("Calibration started for %s by %s (session=%s)", raspberry_id, client_uid, assigned_session_id)
    except Exception as e:
        logger.exception("Failed writing calibration node: %s", e)
    return assigned_session_id


def _process_request(raspberry_id: str, client_uid: str, payload: dict, publish_status_fn=None):
    try:
        logger.info("Pairing request received: raspberry=%s client=%s payload=%s", raspberry_id, client_uid, payload)
        valid, reason = _validate_request(payload)
        if not valid:
            _write_ack(raspberry_id, client_uid, False, f"Invalid request: {reason}")
            return

        # business rules: accept if device unowned or owned by same client
        current_owner = db.reference(f"devices/{raspberry_id}/meta/ownerUid").get()
        if current_owner and current_owner != client_uid:
            _write_ack(raspberry_id, client_uid, False, "Device already paired with another user")
            return

        # accept: set owner, start calibration, write ack
        _set_device_owner(raspberry_id, client_uid)
        assigned_session_id = _start_calibration(raspberry_id, client_uid)
        _write_ack(raspberry_id, client_uid, True, "Pairing accepted. Calibration started.", assigned_session_id)

        # Optionally notify via MQTT if publish_status_fn provided
        if publish_status_fn:
            try:
                publish_status_fn({
                    "event": "paired",
                    "ownerUid": client_uid,
                    "sessionId": assigned_session_id,
                    "ts": int(time.time() * 1000)
                })
            except Exception:
                logger.exception("publish_status_fn failed")

    except Exception as ex:
        logger.exception("Error processing pairing request: %s", ex)
        try:
            _write_ack(raspberry_id, client_uid, False, f"Server error: {ex}")
        except Exception:
            logger.exception("Failed writing error ack")


def start_pairing_listener(raspberry_id: str, poll_interval: float = 2.0, publish_status_fn=None):
    """
    Start a background thread that polls /pairing/requests/{raspberry_id} and processes new requests.

    - `raspberry_id`: the identifier used by the device (must match the one the app will use)
    - `poll_interval`: seconds between polls
    - `publish_status_fn`: optional callable(payload_dict) to publish an MQTT/status update using existing MQTT client

    Returns the Thread object (daemon) so the caller can keep a reference if desired.
    """
    if not FIREBASE_AVAILABLE:
        raise RuntimeError("firebase_admin db not available; ensure firebase_admin is installed and initialized")

    processed = set()

    def _worker():
        logger.info("Pairing listener started for %s (poll interval %.1fs)", raspberry_id, poll_interval)
        ref = db.reference(f"pairing/requests/{raspberry_id}")
        while True:
            try:
                snapshot = ref.get()
                if snapshot:
                    # snapshot is a dict of clientUid -> payload
                    for client_uid, payload in snapshot.items():
                        if client_uid in processed:
                            continue
                        # Process this request
                        _process_request(raspberry_id, client_uid, payload or {}, publish_status_fn)
                        processed.add(client_uid)
                time.sleep(poll_interval)
            except Exception as e:
                logger.exception("Error in pairing listener loop: %s", e)
                time.sleep(poll_interval)

    t = threading.Thread(target=_worker, daemon=True)
    t.start()
    return t


if __name__ == '__main__':
    # Archivo de prueba: requiere firebase_admin inicializado en el intérprete.
    print("pairing_listener.py loaded — este módulo se importa desde tu script principal.")

