#!/usr/bin/env python3
# -*- coding: utf-8 -*-
# Script integrado de SpineTrack para Raspberry Pi.
# Este archivo centraliza runtime de IMU, pairing/control RTDB,
# y publicacion de telemetria/sesiones por Firebase y MQTT.
#
# Autores: Karen Ballen, Ismael Fonseca, Mafe Tafur
# 2026 - Todos los derechos reservados
#
# Changelog v2:
#   - SesionPostural: agregar_muestra, registrar_evento, calcular_icp,
#     payload_realtime, payload_sesion_final y guardar_local reemplazados
#     por implementaciones completas del script pyrebase (Doc2).
#   - Agregados metodos auxiliares theta_promedio() y duracion_total_seg().
#   - Agregada funcion actualizar_umbrales_adaptativos() portada de Doc2.
#   - Toda la logica de pairing, control listener, MQTT y Firebase Admin
#     permanece intacta del script base (Doc1).

from mpu6050 import mpu6050
import RPi.GPIO as GPIO
import math
import time
import os
import json
import uuid
import logging
import threading
import ssl
from datetime import datetime
from pathlib import Path

try:
    import firebase_admin
    from firebase_admin import credentials, db
    FIREBASE_DISPONIBLE = True
except ImportError:
    FIREBASE_DISPONIBLE = False
    print("[AVISO] firebase-admin no instalado.")
    print("        Instala con: pip install firebase-admin --break-system-packages")

try:
    import paho.mqtt.client as mqtt
    from paho import mqtt as paho_mqtt
    MQTT_DISPONIBLE = True
except ImportError:
    MQTT_DISPONIBLE = False
    print("[AVISO] paho-mqtt no instalado.")
    print("        Instala con: pip install paho-mqtt --break-system-packages")

# -----------------------------------------
# RUTAS Y DIRECTORIOS
# -----------------------------------------
BASE_DIR       = Path(__file__).parent
PERFIL_PATH    = BASE_DIR / "perfil_usuario.json"
SESIONES_DIR   = BASE_DIR / "sesiones"
LOG_PATH       = BASE_DIR / "spinetrack.log"
DEVICE_ID_PATH = BASE_DIR / ".device_id"
SESIONES_DIR.mkdir(exist_ok=True)

# -----------------------------------------
# LOGGING
# -----------------------------------------
logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(message)s",
    handlers=[
        logging.FileHandler(str(LOG_PATH), encoding="utf-8"),
        logging.StreamHandler()
    ]
)
log = logging.getLogger("SpineTrack")

# -----------------------------------------
# PINES GPIO (BCM)
# -----------------------------------------
VIB_PIN1    = 13
VIB_PIN2    = 25
VIB_PIN3    = 24
VIB_PIN4    = 26
TODOS_PINES = [VIB_PIN1, VIB_PIN2, VIB_PIN3, VIB_PIN4]

# -----------------------------------------
# CONFIGURACION FIREBASE ADMIN
# -----------------------------------------
FIREBASE_DB_URL  = "https://spinetrack-c3179-default-rtdb.firebaseio.com"
CREDENTIALS_PATH = BASE_DIR / "serviceAccount.json"
FIREBASE_RT_INTERVAL = 5.0

# -----------------------------------------
# CONFIGURACION MQTT + TLS - HIVEMQ CLOUD
# -----------------------------------------
MQTT_CONFIG = {
    "host":      "58adacd73e3b45f8872bfbf3fb9bc432.s1.eu.hivemq.cloud",
    "port":      8883,
    "username":  "spinetrack",
    "password":  "3j8VxPNuS7wrd3v",
    "client_id": "spinetrack_device",
    "keepalive": 60
}

MQTT_RT_INTERVAL = 1.0   # MQTT publica mas seguido que Firebase

# -----------------------------------------
# PARAMETROS ICP
# -----------------------------------------
W1        = 0.50
W2        = 0.30
W3        = 0.20
THETA_MAX = 30.0
C_MAX     = 50

MIN_SESION_SEG      = 60
TIEMPO_CONFIRMACION = 0.5

# -----------------------------------------
# UMBRALES DE DETECCION
# -----------------------------------------
PITCH_ON_THRESHOLD  = 3.0
ROLL_ON_THRESHOLD   = 3.0
PITCH_OFF_THRESHOLD = 5.0
ROLL_OFF_THRESHOLD  = 5.0

SENSIBILIDAD_UMBRALES = {
    "baja":   {"pitch_on": 15.0, "roll_on": 15.0, "pitch_off": 3.0, "roll_off": 3.0},
    "normal": {"pitch_on": 10.0, "roll_on": 10.0, "pitch_off": 1.0, "roll_off": 1.0},
    "alta":   {"pitch_on":  6.0, "roll_on":  6.0, "pitch_off": 0.5, "roll_off": 0.5},
}

PERFIL_DEFAULT = {
    "uid":            None,
    "nombre":         "Usuario",
    "edad":           None,
    "altura_cm":      None,
    "peso_kg":        None,
    "sensibilidad":   "normal",
    "umbrales":       SENSIBILIDAD_UMBRALES["normal"].copy(),
    "calibracion": {
        "pitch_base": None,
        "roll_base":  None,
        "gx_offset":  None,
        "gy_offset":  None,
        "gz_offset":  None,
        "fecha":      None
    },
    "historial_icp":  [],
    "total_sesiones": 0
}


# ---------------------------------------------------------------
# SECCION 1 - CLASIFICADORES POSTURALES
# ---------------------------------------------------------------

def clasificar_angulo(theta_abs):
    #Clasifica la desviacion angular absoluta en etiqueta y puntaje.#
    if theta_abs <= 2.0:
        return "Excelente", 5
    elif theta_abs <= 5.0:
        return "Bueno", 4
    elif theta_abs <= 10.0:
        return "Regular", 3
    elif theta_abs <= 20.0:
        return "Malo", 2
    else:
        return "Peligroso", 1


def clasificar_icp(icp):
    #Clasifica el ICP numerico en una etiqueta textual.#
    if icp >= 90:
        return "Excelente"
    elif icp >= 75:
        return "Bueno"
    elif icp >= 60:
        return "Regular"
    elif icp >= 40:
        return "Malo"
    else:
        return "Critico"


# ---------------------------------------------------------------
# SECCION 2 - PERFIL DE USUARIO
# ---------------------------------------------------------------

def cargar_perfil():
    #Carga perfil local desde JSON para continuidad entre reinicios.#
    if PERFIL_PATH.exists():
        try:
            with open(PERFIL_PATH, "r", encoding="utf-8") as f:
                perfil = json.load(f)
            log.info("Perfil cargado: %s (UID: %s)", perfil.get("nombre"), perfil.get("uid"))
            return perfil
        except Exception as e:
            log.warning("Error cargando perfil: %s", e)
    return None


def guardar_perfil(perfil):
    #Persiste perfil local a JSON.#
    try:
        with open(PERFIL_PATH, "w", encoding="utf-8") as f:
            json.dump(perfil, f, indent=2, ensure_ascii=False)
    except Exception as e:
        log.error("Error guardando perfil: %s", e)


def actualizar_umbrales_adaptativos(perfil):

    #Ajusta umbrales pitch_on/roll_on dinamicamente segun el ICP promedio
    #de las ultimas 5 sesiones. Factor de escala aplicado sobre umbrales base
    #de la sensibilidad configurada:
    #  ICP < 50  -> factor 0.85 (umbrales mas estrictos)
    #  ICP > 85  -> factor 1.10 (umbrales mas permisivos)
    #  ICP resto -> sin cambio  (factor 1.0)
    #Requiere al menos 5 sesiones previas en historial.

    hist = perfil.get("historial_icp", [])
    if len(hist) < 5:
        return perfil

    icp_prom_reciente = sum(hist[-5:]) / 5
    base = SENSIBILIDAD_UMBRALES[perfil.get("sensibilidad", "normal")].copy()

    if icp_prom_reciente < 50:
        factor = 0.85
    elif icp_prom_reciente > 85:
        factor = 1.10
    else:
        factor = 1.0

    perfil["umbrales"]["pitch_on"] = round(base["pitch_on"] * factor, 2)
    perfil["umbrales"]["roll_on"]  = round(base["roll_on"]  * factor, 2)

    log.info(
        "Umbrales adaptativos -> pitch_on=%.1f  roll_on=%.1f  (ICP prom reciente: %.1f)",
        perfil["umbrales"]["pitch_on"],
        perfil["umbrales"]["roll_on"],
        icp_prom_reciente
    )
    return perfil


# ---------------------------------------------------------------
# SECCION 3 - PAIRING & CONTROL LISTENERS (POLLING)
# ---------------------------------------------------------------

def get_device_id():
    #Obtiene o crea ID persistente usado en pairing de la Raspberry.
    try:
        if DEVICE_ID_PATH.exists():
            return DEVICE_ID_PATH.read_text().strip()
        did = uuid.uuid4().hex[:8]
        DEVICE_ID_PATH.write_text(did)
        return did
    except Exception:
        return uuid.uuid4().hex[:8]


def _resolver_nombre_usuario(db_ref, uid, fallback_nombre="Usuario"):
    #Busca nombre visible del usuario en config del dispositivo y nodo users.
    if not db_ref or not uid:
        return fallback_nombre
    try:
        cfg = db_ref.reference(f"dispositivos/{uid}/config").get() or {}
        nombre_cfg = cfg.get("nombre")
        if isinstance(nombre_cfg, str) and nombre_cfg.strip():
            return nombre_cfg.strip()
    except Exception:
        log.exception("Error leyendo nombre desde dispositivos/%s/config", uid)

    try:
        user = db_ref.reference(f"users/{uid}").get() or {}
        for k in ("nombre", "displayName", "name", "username"):
            v = user.get(k)
            if isinstance(v, str) and v.strip():
                return v.strip()
    except Exception:
        log.exception("Error leyendo nombre desde users/%s", uid)

    return fallback_nombre


def resolver_identidad_owner(db_ref, device_id, perfil):
    #Prioriza ownerUid del device para evitar quedarse con UID local obsoleto.
    uid_actual    = (perfil.get("uid") or "").strip()
    nombre_actual = perfil.get("nombre") or "Usuario"
    if not db_ref:
        return uid_actual, nombre_actual, None
    try:
        owner     = db_ref.reference(f"devices/{device_id}/meta/ownerUid").get()
        owner_uid = owner.strip() if isinstance(owner, str) else None
        uid_final = owner_uid or uid_actual
        nombre_final = _resolver_nombre_usuario(db_ref, uid_final, nombre_actual)
        return uid_final, nombre_final, owner_uid
    except Exception:
        log.exception("Error resolviendo identidad para device %s", device_id)
        return uid_actual, nombre_actual, None


def start_pairing_listener(raspberry_id, db_ref, on_pair_callback=None, poll_interval=2.0):
    #Listener por polling para pairing y provision inicial en RTDB.
    processed = set()

    def worker():
        log.info("Pairing listener started for %s", raspberry_id)
        ref = db_ref.reference(f"pairing/requests/{raspberry_id}")
        while True:
            try:
                snapshot = ref.get()
                if snapshot and isinstance(snapshot, dict):
                    for client_uid, payload in snapshot.items():
                        payload = payload or {}
                        client  = payload.get("clientUid") or client_uid
                        ts      = payload.get("timestamp")
                        req_id  = f"{client}:{ts}"
                        if req_id in processed:
                            continue
                        if not client or not ts:
                            try:
                                db_ref.reference(
                                    f"pairing/acks/{raspberry_id}/{client}"
                                ).set({
                                    "accepted":  False,
                                    "message":   "invalid request",
                                    "timestamp": int(time.time() * 1000)
                                })
                            except Exception:
                                pass
                            processed.add(req_id)
                            continue

                        current_owner = db_ref.reference(
                            f"devices/{raspberry_id}/meta/ownerUid"
                        ).get()
                        if current_owner and current_owner != client:
                            log.info(
                                "Reasignando ownerUid de %s a %s",
                                current_owner, client
                            )

                        try:
                            nombre_resuelto = (
                                    payload.get("clientDisplayName")
                                    or payload.get("nombre")
                            )
                            if not (isinstance(nombre_resuelto, str) and
                                    nombre_resuelto.strip()):
                                nombre_resuelto = _resolver_nombre_usuario(
                                    db_ref, client, "Usuario"
                                )
                            db_ref.reference(
                                f"devices/{raspberry_id}/meta"
                            ).update({
                                "ownerUid": client,
                                "lastSeen": int(time.time() * 1000),
                                "status":   "paired"
                            })
                            default_config = {
                                "uid":         client,
                                "nombre":      nombre_resuelto,
                                "sensibilidad": "normal",
                                "pitch_on":    PITCH_ON_THRESHOLD,
                                "roll_on":     ROLL_ON_THRESHOLD,
                                "pitch_off":   PITCH_OFF_THRESHOLD,
                                "roll_off":    ROLL_OFF_THRESHOLD
                            }
                            db_ref.reference(
                                f"dispositivos/{client}/config"
                            ).set(default_config)
                            db_ref.reference(
                                f"dispositivos/{client}/control/comando"
                            ).set("idle")
                            db_ref.reference(
                                f"dispositivos/{client}/estado"
                            ).set({
                                "activo":        False,
                                "calibrado":     False,
                                "sesion_activa": False,
                                "ts_ultimo":     0
                            })
                            db_ref.reference(
                                f"pairing/acks/{raspberry_id}/{client}"
                            ).set({
                                "accepted":  True,
                                "message":   "paired",
                                "deviceId":  raspberry_id,
                                "timestamp": int(time.time() * 1000)
                            })
                            log.info(
                                "Paired raspberry %s with client %s",
                                raspberry_id, client
                            )
                            if on_pair_callback:
                                try:
                                    on_pair_callback(client)
                                except Exception:
                                    log.exception("on_pair_callback failed")
                        except Exception:
                            log.exception("Failed processing pairing request")

                        processed.add(req_id)
                        if len(processed) > 2000:
                            processed.clear()

                time.sleep(poll_interval)
            except Exception:
                log.exception("Error in pairing listener loop")
                time.sleep(poll_interval)

    t = threading.Thread(target=worker, daemon=True)
    t.start()
    return t


def start_control_listener_for_uid(client_uid, db_ref, callback,
                                   poll_interval=1.0, stop_event=None):
    #Listener de comandos remotos en /dispositivos/{uid}/control/comando.
    last       = None
    stop_event = stop_event or threading.Event()

    def worker():
        nonlocal last
        log.info("Control listener started for uid=%s", client_uid)
        ref = db_ref.reference(f"dispositivos/{client_uid}/control/comando")
        while not stop_event.is_set():
            try:
                val = ref.get()
                if val is not None and val != last:
                    log.info(
                        "Control command changed for %s: %s",
                        client_uid, val
                    )
                    last = val
                    try:
                        callback(val)
                    except Exception:
                        log.exception("control callback failed")
                time.sleep(poll_interval)
            except Exception:
                log.exception("Error in control listener loop")
                time.sleep(poll_interval)

    t = threading.Thread(target=worker, daemon=True)
    t.start()
    return t, stop_event


# ---------------------------------------------------------------
# SECCION 4 - FILTRO DE KALMAN
# ---------------------------------------------------------------

class KalmanFilter:
    #Filtro de Kalman 1-D para fusion acelerometro-giroscopio.
    #Estados: [angulo, bias_giroscopio]


    def __init__(self, Q_angle=0.001, Q_bias=0.003, R_measure=0.03):
        self.Q_angle   = Q_angle
        self.Q_bias    = Q_bias
        self.R_measure = R_measure
        self.angle = 0.0
        self.bias  = 0.0
        self.P     = [[0.0, 0.0], [0.0, 0.0]]

    def get_angle(self, new_angle, new_rate, dt):
        if dt <= 0:
            return self.angle

        # Prediccion
        rate         = new_rate - self.bias
        self.angle  += dt * rate
        self.P[0][0] += dt * (
                dt * self.P[1][1] - self.P[0][1] - self.P[1][0] + self.Q_angle
        )
        self.P[0][1] -= dt * self.P[1][1]
        self.P[1][0] -= dt * self.P[1][1]
        self.P[1][1] += self.Q_bias * dt

        # Actualizacion
        S  = self.P[0][0] + self.R_measure
        K0 = self.P[0][0] / S
        K1 = self.P[1][0] / S

        y           = new_angle - self.angle
        self.angle += K0 * y
        self.bias  += K1 * y

        P00_temp     = self.P[0][0]
        P01_temp     = self.P[0][1]
        self.P[0][0] -= K0 * P00_temp
        self.P[0][1] -= K0 * P01_temp
        self.P[1][0] -= K1 * P00_temp
        self.P[1][1] -= K1 * P01_temp

        return self.angle


# ---------------------------------------------------------------
# SECCION 5 - CONTROL DE VIBRADORES
# ---------------------------------------------------------------

def setup_gpio():
    #Inicializa pines GPIO en modo salida y los apaga."""
    try:
        GPIO.setmode(GPIO.BCM)
        GPIO.setwarnings(False)
        for pin in TODOS_PINES:
            GPIO.setup(pin, GPIO.OUT)
        desactivar_vibradores()
    except Exception:
        log.exception("GPIO init failed")


def activar_vibradores():
    #Activa todos los motores de vibracion."""
    for pin in TODOS_PINES:
        try:
            GPIO.output(pin, GPIO.HIGH)
        except Exception:
            pass


def desactivar_vibradores():
    #Desactiva todos los motores de vibracion."""
    for pin in TODOS_PINES:
        try:
            GPIO.output(pin, GPIO.LOW)
        except Exception:
            pass


# ---------------------------------------------------------------
# SECCION 6 - OBJETO DE SESION POSTURAL
# ---------------------------------------------------------------

class SesionPostural:

    #Acumula estadisticas posturales durante una sesion y genera
    #payloads estructurados para Firebase/MQTT.

    #Metrica ICP (Indice de Calidad Postural):
    #  ICP = 100 - [W1*(theta_prom/THETA_MAX) + W2*(t_mala/t_total)
    #               + W3*(correcciones/C_MAX)] * 100
    #  Clampeado a [0, 100].


    def __init__(self, perfil):
        self.session_id   = str(uuid.uuid4())
        self.uid          = perfil["uid"]
        self.nombre       = perfil.get("nombre", "Usuario")
        self.inicio       = datetime.now()
        self.ts_inicio    = time.monotonic()

        # Acumuladores numericos
        self.n_muestras       = 0
        self.suma_theta_abs   = 0.0
        self.t_buena_postura  = 0.0
        self.t_mala_postura   = 0.0
        self.num_correcciones = 0
        self.num_alertas      = 0
        self.temp_acum        = 0.0
        self.temp_n           = 0

        # Distribucion de clases angulares (conteo absoluto)
        self.dist_angulo = {
            "Excelente": 0,
            "Bueno":     0,
            "Regular":   0,
            "Malo":      0,
            "Peligroso": 0
        }

        self.eventos = []
        self.w1 = W1
        self.w2 = W2
        self.w3 = W3

    # ----------------------------------------------------------
    # Metodos auxiliares
    # ----------------------------------------------------------

    def theta_promedio(self):
        #Devuelve el angulo de desviacion promedio acumulado."""
        if self.n_muestras == 0:
            return 0.0
        return round(self.suma_theta_abs / self.n_muestras, 3)

    def duracion_total_seg(self):
        #Duracion total de la sesion en segundos."""
        return self.t_buena_postura + self.t_mala_postura

    # ----------------------------------------------------------
    # Actualizacion por muestra
    # ----------------------------------------------------------

    def agregar_muestra(self, pitch_dev, roll_dev, en_buena_postura, dt, temp):

        #Integra una muestra del ciclo de control principal.
        #Actualiza acumuladores de angulo, tiempos, temperatura
        #y distribucion de clases posturales.

        theta_abs = math.sqrt(pitch_dev ** 2 + roll_dev ** 2)
        self.suma_theta_abs += theta_abs
        self.n_muestras     += 1

        if en_buena_postura:
            self.t_buena_postura += dt
        else:
            self.t_mala_postura  += dt

        if temp is not None:
            self.temp_acum += temp
            self.temp_n    += 1

        clase, _ = clasificar_angulo(theta_abs)
        self.dist_angulo[clase] += 1

    # ----------------------------------------------------------
    # Registro de eventos discretos
    # ----------------------------------------------------------

    def registrar_evento(self, tipo, pitch_dev, roll_dev):

        #Registra un evento ALERTA o CORRECCION con su contexto angular
        #y tiempo relativo desde el inicio de la sesion.

        theta = math.sqrt(pitch_dev ** 2 + roll_dev ** 2)
        self.eventos.append({
            "ts_rel_s":  round(time.monotonic() - self.ts_inicio, 2),
            "timestamp": datetime.now().isoformat(),
            "tipo":      tipo,
            "theta_abs": round(theta, 3),
            "pitch_dev": round(pitch_dev, 3),
            "roll_dev":  round(roll_dev, 3),
            "clase":     clasificar_angulo(theta)[0]
        })
        if tipo == "ALERTA":
            self.num_alertas      += 1
        elif tipo == "CORRECCION":
            self.num_correcciones += 1

    # ----------------------------------------------------------
    # Calculo del ICP
    # ----------------------------------------------------------

    def calcular_icp(self):

        #Calcula el Indice de Calidad Postural (ICP) ponderado.

        #Componentes:
        #  - C1 = W1 * (theta_prom / THETA_MAX)     -> desviacion angular media
        #  - C2 = W2 * (t_mala / t_total)            -> fraccion de tiempo en mala postura
        #  - C3 = W3 * (min(correcciones, C_MAX) / C_MAX) -> tasa de correcciones

        #ICP = 100 * (1 - C1 - C2 - C3), clampeado a [0, 100].
        #Retorna 100.0 si la sesion es demasiado corta para ser significativa.

        t_total = self.t_buena_postura + self.t_mala_postura
        if t_total < 1.0 or self.n_muestras == 0:
            return 100.0

        theta_prom = self.suma_theta_abs / self.n_muestras

        icp = 100.0 - (
                self.w1 * (theta_prom / THETA_MAX) +
                self.w2 * (self.t_mala_postura / t_total) +
                self.w3 * (min(self.num_correcciones, C_MAX) / C_MAX)
        ) * 100.0

        return round(max(0.0, min(100.0, icp)), 1)

    # ----------------------------------------------------------
    # Payloads de telemetria
    # ----------------------------------------------------------

    def payload_realtime(self, pitch, roll, pitch_dev, roll_dev,
                         postura_incorrecta, temp):

        #Genera el payload de telemetria en tiempo real.
        #Publicado por Firebase cada FIREBASE_RT_INTERVAL s
        #y por MQTT cada MQTT_RT_INTERVAL s.

        theta_abs   = math.sqrt(pitch_dev ** 2 + roll_dev ** 2)
        clase, _    = clasificar_angulo(theta_abs)
        icp_parcial = self.calcular_icp()

        return {
            "v":             "1.0",
            "uid":           self.uid,
            "session_id":    self.session_id,
            "ts":            datetime.now().isoformat(),
            "pitch":         round(pitch,     3),
            "roll":          round(roll,      3),
            "pitch_dev":     round(pitch_dev, 3),
            "roll_dev":      round(roll_dev,  3),
            "theta_abs":     round(theta_abs, 3),
            "buena_postura": not postura_incorrecta,
            "clase_angulo":  clase,
            "icp_parcial":   icp_parcial,
            "clase_icp":     clasificar_icp(icp_parcial),
            "t_buena_min":   round(self.t_buena_postura / 60, 2),
            "t_mala_min":    round(self.t_mala_postura  / 60, 2),
            "num_alertas":   self.num_alertas,
            "temp_c":        round(temp, 2) if temp is not None else None
        }

    def payload_sesion_final(self):

        #Genera el payload completo de resumen de sesion.
        #Publicado al finalizar en Firebase y MQTT.

        icp     = self.calcular_icp()
        t_total = self.duracion_total_seg()
        theta_p = self.theta_promedio()

        # Distribucion porcentual de clases angulares
        dist_pct = {}
        if self.n_muestras > 0:
            for k, v in self.dist_angulo.items():
                dist_pct[k] = round(v / self.n_muestras * 100, 1)

        return {
            "v":                "1.0",
            "uid":              self.uid,
            "nombre":           self.nombre,
            "session_id":       self.session_id,
            "ts_inicio":        self.inicio.isoformat(),
            "ts_fin":           datetime.now().isoformat(),
            "duracion_min":     round(t_total / 60, 2),
            "t_buena_min":      round(self.t_buena_postura / 60, 2),
            "t_mala_min":       round(self.t_mala_postura  / 60, 2),
            "pct_buena":        round(
                self.t_buena_postura / t_total * 100, 1
            ) if t_total > 0 else 0.0,
            "pct_mala":         round(
                self.t_mala_postura  / t_total * 100, 1
            ) if t_total > 0 else 0.0,
            "theta_promedio":   theta_p,
            "num_alertas":      self.num_alertas,
            "num_correcciones": self.num_correcciones,
            "icp":              icp,
            "clase_icp":        clasificar_icp(icp),
            "dist_angulo_pct":  dist_pct,
            "temp_prom_c":      round(
                self.temp_acum / self.temp_n, 2
            ) if self.temp_n > 0 else None,
            "n_muestras":       self.n_muestras,
            "eventos":          self.eventos[-100:]   # maximo 100 eventos
        }

    def guardar_local(self):

        #Serializa payload_sesion_final() a JSON en SESIONES_DIR.
        #Nombre de archivo incluye los primeros 8 caracteres del UUID
        #y el timestamp de inicio para trazabilidad.

        payload = self.payload_sesion_final()
        fname   = SESIONES_DIR / (
                "sesion_" + self.session_id[:8] + "_" +
                self.inicio.strftime("%Y%m%d_%H%M%S") + ".json"
        )
        try:
            with open(fname, "w", encoding="utf-8") as f:
                json.dump(payload, f, indent=2, ensure_ascii=False)
            log.info("Sesion guardada localmente: %s", fname)
        except Exception as e:
            log.error("Error guardando sesion local: %s", e)
        return fname


# ---------------------------------------------------------------
# SECCION 7 - CALIBRACION
# ---------------------------------------------------------------

def _calibracion_vigente(perfil, campo_clave, max_horas=24.0):
    #Determina si existe calibracion reciente para el campo solicitado."""
    cal = perfil.get("calibracion", {})
    if cal.get(campo_clave) is None or cal.get("fecha") is None:
        return False
    try:
        horas = (
                        datetime.now() - datetime.fromisoformat(cal["fecha"])
                ).total_seconds() / 3600
        return horas < max_horas
    except Exception:
        return False


def calibrar_giroscopio(mpu, perfil):

    #Calibra offset del giroscopio promediando 500 muestras en reposo.
    #Reutiliza calibracion anterior si tiene menos de 24 h de antiguedad.
    #Persiste offsets en perfil local.

    cal = perfil.setdefault("calibracion", {})

    if _calibracion_vigente(perfil, "gx_offset"):
        return cal.get("gx_offset", 0.0), cal.get("gy_offset", 0.0), cal.get("gz_offset", 0.0)

    print("\nCalibrando giroscopio - no mover el dispositivo...")
    gx = gy = gz = 0.0
    n  = 0
    for _ in range(500):
        try:
            g   = mpu.get_gyro_data()
            gx += g["x"]
            gy += g["y"]
            gz += g["z"]
            n  += 1
        except Exception:
            pass
        time.sleep(0.002)

    if n > 0:
        gx /= n; gy /= n; gz /= n
        print("  Offsets -> X:%.4f  Y:%.4f  Z:%.4f  (N=%d)" % (gx, gy, gz, n))

    cal.update({
        "gx_offset": gx,
        "gy_offset": gy,
        "gz_offset": gz,
        "fecha":     datetime.now().isoformat()
    })
    guardar_perfil(perfil)
    log.info("Calibracion de giroscopio completada (N=%d).", n)
    return gx, gy, gz


def calibrar_postura_base(mpu, perfil, force=False):

    #Calibra pitch/roll de referencia (postura correcta) promediando 80 muestras.
    #El parametro force=True omite la comprobacion de vigencia (recalibracion por app).
    #Persiste valores en perfil local.

    cal = perfil.setdefault("calibracion", {})

    if not force and _calibracion_vigente(perfil, "pitch_base"):
        return cal.get("pitch_base"), cal.get("roll_base")

    nombre = perfil.get("nombre", "usuario")
    print("\n%s, adopta tu postura CORRECTA de trabajo." % nombre)
    print("Midiendo en 3 segundos...")
    time.sleep(3)

    sp = sr = 0.0
    n  = 0
    for _ in range(80):
        try:
            a        = mpu.get_accel_data()
            ax, ay, az = a["x"], a["y"], a["z"]
            sp += math.atan2(ay, math.sqrt(ax ** 2 + az ** 2)) * 180.0 / math.pi
            sr += math.atan2(-ax, math.sqrt(ay ** 2 + az ** 2)) * 180.0 / math.pi
            n  += 1
        except Exception:
            pass
        time.sleep(0.02)

    if n == 0:
        log.error("Error en calibracion de postura base.")
        return None, None

    pitch_base = sp / n
    roll_base  = sr / n
    cal.update({
        "pitch_base": pitch_base,
        "roll_base":  roll_base,
        "fecha":      datetime.now().isoformat()
    })
    guardar_perfil(perfil)
    print("  Pitch base: %.2f    Roll base: %.2f" % (pitch_base, roll_base))
    log.info(
        "Postura base calibrada: pitch=%.2f  roll=%.2f  (N=%d)",
        pitch_base, roll_base, n
    )
    return pitch_base, roll_base


# ---------------------------------------------------------------
# SECCION 8 - DETECCION DE POSTURA
# ---------------------------------------------------------------

def detectar_cambio_postura(angle_pitch, angle_roll, pitch_base, roll_base,
                            postura_incorrecta, t_inicio_postura,
                            posible_mala_postura, umbrales):

    #Detecta transiciones de postura usando doble umbral con histeresis
    #y ventana de confirmacion TIEMPO_CONFIRMACION.

    #Retorna:
    #    postura_incorrecta, posible_mala_postura, t_inicio_postura,
    #    evento ('ALERTA' | 'CORRECCION' | None), pitch_dev, roll_dev

    evento    = None
    pitch_dev = angle_pitch - pitch_base
    roll_dev  = angle_roll  - roll_base

    supera_umbral = (
            abs(pitch_dev) > umbrales.get("pitch_on", PITCH_ON_THRESHOLD)
            or abs(roll_dev) > umbrales.get("roll_on", ROLL_ON_THRESHOLD)
    )
    restaura_ok = (
            abs(pitch_dev) < umbrales.get("pitch_off", PITCH_OFF_THRESHOLD)
            and abs(roll_dev) < umbrales.get("roll_off", ROLL_OFF_THRESHOLD)
    )

    ahora = time.monotonic()

    if supera_umbral and not posible_mala_postura:
        posible_mala_postura = True
        t_inicio_postura     = ahora

    if (posible_mala_postura and supera_umbral and
            (ahora - t_inicio_postura) >= TIEMPO_CONFIRMACION):
        if not postura_incorrecta:
            postura_incorrecta = True
            activar_vibradores()
            evento = "ALERTA"

    if restaura_ok:
        if postura_incorrecta:
            evento = "CORRECCION"
        postura_incorrecta   = False
        posible_mala_postura = False
        desactivar_vibradores()

    return (postura_incorrecta, posible_mala_postura,
            t_inicio_postura, evento, pitch_dev, roll_dev)


# ---------------------------------------------------------------
# SECCION 9 - CLIENTE FIREBASE (firebase-admin)
# ---------------------------------------------------------------

def sincronizar_config_desde_app(db_ref, uid, perfil):
    #Sincroniza configuracion remota del usuario desde RTDB a perfil local."""
    try:
        config = db_ref.reference(f"dispositivos/{uid}/config").get()
        if not config:
            return perfil
        nueva_sens = config.get("sensibilidad", perfil.get("sensibilidad", "normal"))
        nuevos_umbrales = {
            "pitch_on":  config.get("pitch_on",  perfil.get("umbrales", {}).get("pitch_on",  PITCH_ON_THRESHOLD)),
            "roll_on":   config.get("roll_on",   perfil.get("umbrales", {}).get("roll_on",   ROLL_ON_THRESHOLD)),
            "pitch_off": config.get("pitch_off", perfil.get("umbrales", {}).get("pitch_off", PITCH_OFF_THRESHOLD)),
            "roll_off":  config.get("roll_off",  perfil.get("umbrales", {}).get("roll_off",  ROLL_OFF_THRESHOLD)),
        }
        if (nueva_sens != perfil.get("sensibilidad") or
                nuevos_umbrales != perfil.get("umbrales")):
            perfil["sensibilidad"] = nueva_sens
            perfil["umbrales"]     = nuevos_umbrales
            guardar_perfil(perfil)
            log.info(
                "Configuracion actualizada desde app: sensibilidad=%s",
                nueva_sens
            )
        return perfil
    except Exception:
        log.exception("Error sincronizando config")
        return perfil


class FirebaseClient:
    #Cliente de escritura RTDB para realtime/eventos/sesiones (firebase-admin)."""

    def __init__(self, perfil, db_ref=None):
        self.uid       = perfil["uid"]
        self.db_ref    = db_ref
        self.conectado = db_ref is not None
        if not self.conectado:
            log.warning("firebase-admin no disponible. Modo solo-local activo.")

    def publicar_realtime(self, payload):
        #Sobreescribe snapshot realtime en sensores/{uid}/realtime."""
        if not self.conectado:
            return
        try:
            self.db_ref.reference(f"sensores/{self.uid}/realtime").set(payload)
        except Exception:
            log.exception("Error publicando realtime Firebase")

    def publicar_evento(self, payload):
        #Agrega evento discreto al historial en eventos/{uid}."""
        if not self.conectado:
            return
        try:
            self.db_ref.reference(f"eventos/{self.uid}").push(payload)
        except Exception:
            log.exception("Error publicando evento Firebase")

    def publicar_sesion(self, payload):
        #Guarda resumen final en sesiones/{uid}/{session_id}."""
        if not self.conectado:
            log.warning("Sin conexion Firebase - sesion no publicada en la nube.")
            return False
        try:
            self.db_ref.reference(
                f"sesiones/{self.uid}/{payload['session_id']}"
            ).set(payload)
            log.info("Resumen de sesion publicado en Firebase.")
            return True
        except Exception:
            log.exception("Error publicando sesion Firebase")
            return False

    def desconectar(self):
        #Marca cliente como desconectado logicamente."""
        self.conectado = False
        log.info("Firebase desconectado.")

    def set_uid(self, uid):
        #Sincroniza UID de escritura tras pairing exitoso."""
        if isinstance(uid, str) and uid.strip():
            self.uid = uid.strip()


# ---------------------------------------------------------------
# SECCION 10 - CLIENTE MQTT
# ---------------------------------------------------------------

class MQTTClient:
    #Cliente MQTT TLS para realtime, eventos y sesion final (HiveMQ Cloud)."""

    def __init__(self, perfil):
        self.uid              = perfil["uid"]
        self.client           = None
        self.conectado        = False
        self._reconnect_delay = 2.0

        if not MQTT_DISPONIBLE:
            log.warning("paho-mqtt no disponible. Canal MQTT inactivo.")
            return
        try:
            self.client = mqtt.Client(
                client_id   = MQTT_CONFIG["client_id"] + "-" + self.uid[:8],
                protocol    = mqtt.MQTTv311,
                clean_session = True
            )
            self.client.tls_set(tls_version=ssl.PROTOCOL_TLS_CLIENT)
            self.client.username_pw_set(
                MQTT_CONFIG["username"], MQTT_CONFIG["password"]
            )
            self.client.on_connect    = self._on_connect
            self.client.on_disconnect = self._on_disconnect
            self.client.on_publish    = self._on_publish
            self.client.connect_async(
                MQTT_CONFIG["host"],
                MQTT_CONFIG["port"],
                keepalive=MQTT_CONFIG["keepalive"]
            )
            self.client.loop_start()
            log.info("Cliente MQTT iniciado (loop_start)")
        except Exception:
            log.exception("Error iniciando MQTT")

    def _on_connect(self, client, userdata, flags, rc):
        self.conectado = (rc == 0)
        if self.conectado:
            self._reconnect_delay = 2.0
            log.info("MQTT conectado rc=%s", rc)
        else:
            log.warning("MQTT no conecto correctamente rc=%s", rc)

    def _on_disconnect(self, client, userdata, rc):
        log.info("MQTT desconectado rc=%s", rc)
        self.conectado = False
        if rc != 0 and self.client is not None:
            try:
                time.sleep(self._reconnect_delay)
                self.client.reconnect()
                self._reconnect_delay = min(self._reconnect_delay * 2.0, 60.0)
            except Exception:
                log.exception("Fallo reconexion MQTT")

    def _on_publish(self, client, userdata, mid):
        log.debug("MQTT mensaje publicado mid=%s", mid)

    def publicar_realtime(self, payload):
        if not self.conectado or not self.client:
            return
        try:
            topic = f"spinetrack/{self.uid}/realtime"
            self.client.publish(topic, json.dumps(payload), qos=0, retain=True)
        except Exception:
            log.exception("Error publicando MQTT realtime")

    def publicar_evento(self, payload):
        if not self.conectado or not self.client:
            return
        try:
            topic = f"spinetrack/{self.uid}/eventos"
            self.client.publish(topic, json.dumps(payload), qos=1, retain=False)
        except Exception:
            log.exception("Error publicando MQTT evento")

    def publicar_sesion_final(self, payload):
        if not self.conectado or not self.client:
            log.warning("MQTT no conectado - sesion no publicada en MQTT")
            return
        try:
            topic = f"spinetrack/{self.uid}/sesiones"
            self.client.publish(topic, json.dumps(payload), qos=1, retain=False)
            log.info("Sesion publicada en MQTT.")
        except Exception:
            log.exception("Error publicando MQTT sesion")

    def publicar_status(self, payload):
        if not self.conectado or not self.client:
            return
        try:
            topic = f"spinetrack/{self.uid}/status"
            self.client.publish(topic, json.dumps(payload), qos=1, retain=False)
        except Exception:
            log.exception("Error publicando MQTT status")

    def desconectar(self):
        if self.client:
            try:
                self.client.loop_stop()
                self.client.disconnect()
            except Exception:
                log.exception("Error al desconectar MQTT")
        self.conectado = False

    def set_uid(self, uid):
        #Ajusta topic root activo sin reiniciar el cliente."""
        if isinstance(uid, str) and uid.strip():
            self.uid = uid.strip()


# ---------------------------------------------------------------
# SECCION 11 - HELPERS DE ESTADO Y CONSOLA
# ---------------------------------------------------------------

def reportar_estado(db_ref, uid, activo, calibrado, sesion_activa, calibrando=False):
    #Actualiza estado operativo visible para la app en RTDB."""
    if db_ref is None or not uid:
        return
    db_ref.reference(f"dispositivos/{uid}/estado").update({
        "activo":        bool(activo),
        "calibrado":     bool(calibrado),
        "calibrando":    bool(calibrando),
        "sesion_activa": bool(sesion_activa),
        "ts_ultimo":     int(time.time() * 1000),
    })


def mostrar_consola(perfil, sesion, angle_pitch, angle_roll,
                    pitch_dev, roll_dev, postura_incorrecta, evento,
                    temp, icp, clase_angulo, firebase_ok, mqtt_ok):
    #Renderiza panel de diagnostico en consola local."""
    os.system("clear")
    t_sesion  = time.monotonic() - sesion.ts_inicio
    t_total   = sesion.duracion_total_seg()
    pct_buena = (sesion.t_buena_postura / max(t_total, 0.001)) * 100

    print("=================================================")
    print("  SpineTrack  |  %s" % perfil.get("nombre", "Usuario"))
    print("  Firebase: %s | MQTT: %s" % (
        "ONLINE" if firebase_ok else "OFFLINE",
        "ONLINE" if mqtt_ok     else "OFFLINE"
    ))
    print("=================================================")
    print("  Sesion: %02d:%02d  |  Temp: %.1f C" % (
        int(t_sesion // 60), int(t_sesion % 60), temp
    ))
    print("-------------------------------------------------")
    print("  Pitch : %+7.2f  (dev: %+6.2f)" % (angle_pitch, pitch_dev))
    print("  Roll  : %+7.2f  (dev: %+6.2f)" % (angle_roll,  roll_dev))
    theta = math.sqrt(pitch_dev ** 2 + roll_dev ** 2)
    print("  Theta : %6.2f  ->  Clase: %s" % (theta, clase_angulo))
    print("-------------------------------------------------")
    print("  ICP parcial  : %.1f  [%s]" % (icp, clasificar_icp(icp)))
    print("  Buena postura: %.1f%%  (%02d:%02d min)" % (
        pct_buena,
        int(sesion.t_buena_postura // 60),
        int(sesion.t_buena_postura % 60)
    ))
    print("  Alertas : %d    Correcciones: %d" % (
        sesion.num_alertas, sesion.num_correcciones
    ))
    print("-------------------------------------------------")
    if postura_incorrecta:
        print("  ESTADO : *** MALA POSTURA ***")
    else:
        print("  ESTADO : OK - BUENA POSTURA")
    if evento == "ALERTA":
        print("  >>> Postura incorrecta detectada!")
    elif evento == "CORRECCION":
        print("  >>> Postura corregida - bien hecho!")
    else:
        print("")
    print("=================================================")
    print("  Ctrl+C para finalizar sesion y guardar datos")


# ---------------------------------------------------------------
# SECCION 12 - FINALIZACION DE SESION
# ---------------------------------------------------------------

def finalizar_sesion(sesion, firebase_client, mqtt_client, perfil):

    #Cierra sesion con resumen completo:
    #  1. Desactiva vibradores.
    #  2. Serializa payload y guarda localmente.
    #  3. Publica en Firebase y MQTT.
    #  4. Actualiza historial ICP y total de sesiones en perfil.
    #  5. Imprime resumen en consola.

    desactivar_vibradores()

    icp_final = sesion.calcular_icp()
    payload   = sesion.payload_sesion_final()

    print("\n\n[INFO] Finalizando y guardando sesion...")
    ruta_local = sesion.guardar_local()
    print("  -> Guardado local : " + ruta_local.name)

    if firebase_client.publicar_sesion(payload):
        print("  -> Firebase       : OK")
    else:
        print("  -> Firebase       : sin conexion, solo local.")

    mqtt_client.publicar_sesion_final(payload)

    hist = perfil.get("historial_icp", [])
    hist.append(icp_final)
    perfil["historial_icp"]  = hist[-30:]
    perfil["total_sesiones"] = perfil.get("total_sesiones", 0) + 1
    guardar_perfil(perfil)

    t_total = sesion.duracion_total_seg()
    print("\n" + "=" * 52)
    print("  RESUMEN DE SESION - " + sesion.inicio.strftime("%d/%m/%Y %H:%M"))
    print("-" * 52)
    print("  Duracion          : %.1f min" % (t_total / 60))
    print("  Tiempo buena pos. : %.1f min (%.1f%%)" % (
        payload["t_buena_min"], payload["pct_buena"]
    ))
    print("  Tiempo mala pos.  : %.1f min (%.1f%%)" % (
        payload["t_mala_min"], payload["pct_mala"]
    ))
    print("  Angulo promedio   : %.2f" % payload["theta_promedio"])
    print("  Alertas           : %d" % payload["num_alertas"])
    print("  Correcciones      : %d" % payload["num_correcciones"])
    print("-" * 52)
    print("  ICP Final         : %.1f  [%s]" % (icp_final, clasificar_icp(icp_final)))

    if payload.get("dist_angulo_pct"):
        print("\n  Distribucion postural:")
        for clase, pct in payload["dist_angulo_pct"].items():
            bar = "#" * int(pct / 5)
            print("    %-12s: %5.1f%%  %s" % (clase, pct, bar))
    print("=" * 52 + "\n")

    log.info(
        "Sesion finalizada. ICP=%.1f [%s]  Duracion=%.1f min",
        icp_final, clasificar_icp(icp_final), t_total / 60
    )

    firebase_client.desconectar()
    mqtt_client.desconectar()


# ---------------------------------------------------------------
# SECCION 13 - CONTROL CALLBACK
# ---------------------------------------------------------------

comando_pendiente = None


def _handle_control_cmd(val, firebase_client):
    #Normaliza comando remoto y lo almacena en comando_pendiente."""
    global comando_pendiente
    try:
        v = (val or "").strip().lower()
        if v in ("iniciar", "detener", "calibrar", "idle"):
            comando_pendiente = v
            log.info("_handle_control_cmd -> comando_pendiente='%s'", v)
    except Exception:
        log.exception("_handle_control_cmd error")


# ---------------------------------------------------------------
# SECCION 14 - MAIN
# ---------------------------------------------------------------

def main():
    #Orquestador principal del runtime en Raspberry Pi."""
    global comando_pendiente

    # 1) Inicializacion segura de Firebase Admin (singleton).
    db_ref = None
    if FIREBASE_DISPONIBLE and CREDENTIALS_PATH.exists():
        try:
            try:
                firebase_admin.get_app()
                log.info("Firebase app ya inicializada")
            except ValueError:
                cred = credentials.Certificate(str(CREDENTIALS_PATH))
                firebase_admin.initialize_app(cred, {"databaseURL": FIREBASE_DB_URL})
                log.info("Firebase inicializado")
            db_ref = db
        except Exception:
            log.exception("No se pudo inicializar Firebase Admin")
            db_ref = None

    # 2) Carga o creacion de perfil local.
    perfil = cargar_perfil()
    if perfil is None:
        print("\n[PRIMERA VEZ] No se encontro perfil de usuario.")
        perfil = {
            "uid":            str(uuid.uuid4())[:8],
            "nombre":         "Usuario",
            "edad":           None,
            "altura_cm":      None,
            "peso_kg":        None,
            "sensibilidad":   "normal",
            "umbrales":       SENSIBILIDAD_UMBRALES["normal"].copy(),
            "calibracion":    PERFIL_DEFAULT["calibracion"].copy(),
            "historial_icp":  [],
            "total_sesiones": 0
        }
        guardar_perfil(perfil)
    else:
        # Ajuste adaptativo de umbrales basado en historial de ICP previo
        perfil = actualizar_umbrales_adaptativos(perfil)
        if db_ref:
            perfil = sincronizar_config_desde_app(db_ref, perfil.get("uid"), perfil)

    device_id = get_device_id()
    uid_resuelto, nombre_resuelto, owner_inicial = resolver_identidad_owner(
        db_ref, device_id, perfil
    )
    if uid_resuelto:
        perfil["uid"] = uid_resuelto
    perfil["nombre"] = nombre_resuelto
    guardar_perfil(perfil)

    # 3) Inicializacion de hardware local (IMU + GPIO).
    mpu = None
    try:
        mpu = mpu6050(0x68)
    except Exception:
        log.exception("No se pudo inicializar MPU6050 (ejecutando sin sensor)")
    setup_gpio()

    # 4) Banner de arranque.
    os.system("clear")
    print("==============================")
    print("        SpineTrack            ")
    print("  Bienvenido, " + perfil["nombre"])
    print("  UID: " + perfil["uid"])
    print("==============================")
    time.sleep(0.5)

    # 5) Inicializacion de clientes de comunicacion.
    firebase_client = FirebaseClient(perfil, db_ref)
    mqtt_client     = MQTTClient(perfil)

    uid              = perfil["uid"]
    owner_uid        = owner_inicial
    control_listener = None
    control_stop     = None

    def restart_control_listener(target_uid):
        nonlocal control_listener, control_stop
        if not db_ref or not target_uid:
            return
        if control_stop is not None:
            control_stop.set()
        control_stop = threading.Event()
        control_listener, _ = start_control_listener_for_uid(
            target_uid, db_ref,
            lambda v: _handle_control_cmd(v, firebase_client),
            stop_event=control_stop
        )

    # 6) Listener de pairing para vinculacion desde app Android.
    if db_ref:
        def on_pair(client_uid):
            nonlocal owner_uid, uid
            log.info("Device paired with %s", client_uid)
            owner_uid        = client_uid
            uid              = client_uid
            perfil["uid"]    = client_uid
            try:
                perfil["nombre"] = _resolver_nombre_usuario(
                    db_ref, client_uid, perfil.get("nombre", "Usuario")
                )
                guardar_perfil(perfil)
            except Exception:
                log.exception("Error leyendo nombre para ownerUid")
            firebase_client.set_uid(client_uid)
            mqtt_client.set_uid(client_uid)
            restart_control_listener(client_uid)

        start_pairing_listener(device_id, db_ref, on_pair_callback=on_pair)

    # 7) Si ya existe owner registrado, activa listener de control de inmediato.
    if db_ref:
        try:
            owner = db_ref.reference(f"devices/{device_id}/meta/ownerUid").get()
            if owner:
                log.info(
                    "Found owner %s for device %s, starting control listener",
                    owner, device_id
                )
                owner_uid     = owner
                uid           = owner
                perfil["uid"] = owner
                firebase_client.set_uid(owner)
                mqtt_client.set_uid(owner)
                try:
                    perfil["nombre"] = _resolver_nombre_usuario(
                        db_ref, owner, perfil.get("nombre", "Usuario")
                    )
                    guardar_perfil(perfil)
                except Exception:
                    log.exception("Error sincronizando nombre para ownerUid inicial")
                restart_control_listener(owner)
        except Exception:
            log.exception("Error checking device owner")

    # 8) Publica estado inicial.
    if firebase_client.conectado:
        try:
            reportar_estado(firebase_client.db_ref, uid, True, False, False)
        except Exception:
            log.exception("Error reportando estado inicial")

    print("\n[INFO] Esperando comando desde la app o presiona Enter para calibrar...")
    print("       Comandos validos: 'iniciar', 'calibrar'")

    # 9) Espera comando remoto (con fallback manual y timeout de 5 min).
    t_espera = time.monotonic()
    while True:
        if db_ref and not owner_uid:
            try:
                owner = db_ref.reference(
                    f"devices/{device_id}/meta/ownerUid"
                ).get()
                if owner:
                    owner_uid     = owner
                    uid           = owner
                    perfil["uid"] = owner
                    try:
                        perfil["nombre"] = _resolver_nombre_usuario(
                            db_ref, owner, perfil.get("nombre", "Usuario")
                        )
                        guardar_perfil(perfil)
                    except Exception:
                        log.exception("Error actualizando nombre al refrescar ownerUid")
                    firebase_client.set_uid(owner)
                    mqtt_client.set_uid(owner)
                    restart_control_listener(owner)
            except Exception:
                log.exception("Error refrescando ownerUid")

        if comando_pendiente in ("iniciar", "calibrar"):
            print(f"\n[APP] Comando recibido: {comando_pendiente}")
            break

        import select, sys
        if select.select([sys.stdin], [], [], 0.1)[0]:
            sys.stdin.readline()
            break

        if (time.monotonic() - t_espera) > 300:
            log.info("Timeout esperando comando. Iniciando calibracion automatica.")
            break

        time.sleep(0.1)

    comando_arranque  = comando_pendiente
    comando_pendiente = None

    # 10) Calibracion inicial.
    pitch_base = roll_base = None
    gx_cal = gy_cal = gz_cal = 0.0

    if mpu:
        try:
            if firebase_client.conectado:
                reportar_estado(
                    firebase_client.db_ref, uid, True, False, False, calibrando=True
                )
            gx_cal, gy_cal, gz_cal = calibrar_giroscopio(mpu, perfil)
            pitch_base, roll_base  = calibrar_postura_base(
                mpu, perfil, force=(comando_arranque == "calibrar")
            )
        except Exception:
            log.exception("Calibracion fallida")

    if pitch_base is None:
        log.error("Calibracion fallida. Reinicia el dispositivo.")
        if firebase_client.conectado:
            reportar_estado(firebase_client.db_ref, uid, True, False, False)
        try:
            GPIO.cleanup()
        except Exception:
            pass
        return

    if firebase_client.conectado:
        reportar_estado(firebase_client.db_ref, uid, True, True, True)
        try:
            firebase_client.db_ref.reference(
                f"dispositivos/{uid}/control/comando"
            ).set("idle")
        except Exception:
            log.exception("Error confirmando comando idle")

    # 11) Inicializa sesion y filtros.
    sesion       = SesionPostural(perfil)
    kalman_pitch = KalmanFilter()
    kalman_roll  = KalmanFilter()

    postura_incorrecta   = False
    posible_mala_postura = False
    t_inicio_postura     = 0.0
    last_time            = time.monotonic()
    last_firebase_rt     = time.monotonic()
    last_mqtt_rt         = time.monotonic()
    last_estado_rt       = time.monotonic()
    last_config_sync     = time.monotonic()

    log.info("Sesion iniciada: %s", sesion.session_id)
    print("\nMonitoreo iniciado - Presiona Ctrl+C para finalizar\n")

    try:
        while True:
            # 12) Comandos remotos durante sesion.
            if comando_pendiente == "detener":
                log.info("Comando DETENER recibido desde app.")
                comando_pendiente = None
                break
            if comando_pendiente == "calibrar":
                log.info("Recalibrando por comando de app...")
                comando_pendiente = None
                if firebase_client.conectado:
                    reportar_estado(
                        firebase_client.db_ref, uid, True, False, True,
                        calibrando=True
                    )
                if mpu:
                    pitch_base, roll_base = calibrar_postura_base(
                        mpu, perfil, force=True
                    )
                if firebase_client.conectado:
                    reportar_estado(
                        firebase_client.db_ref, uid, True,
                        pitch_base is not None, True, calibrando=False
                    )
                    try:
                        firebase_client.db_ref.reference(
                            f"dispositivos/{uid}/control/comando"
                        ).set("idle")
                    except Exception:
                        log.exception("Error setting idle post-recalibracion")

            # 13) Lectura de sensores con fallback.
            try:
                accel = mpu.get_accel_data() if mpu else {"x": 0, "y": 0, "z": 1}
                gyro  = mpu.get_gyro_data()  if mpu else {"x": 0, "y": 0, "z": 0}
                temp  = mpu.get_temp()       if mpu else 25.0
            except Exception:
                log.exception("Error lectura sensor")
                time.sleep(0.005)
                continue

            # 14) Calculo de angulos crudos.
            ax, ay, az = accel["x"], accel["y"], accel["z"]
            angle_pitch_acc = (
                    math.atan2(ay, math.sqrt(ax ** 2 + az ** 2)) * 180.0 / math.pi
            )
            angle_roll_acc = (
                    math.atan2(-ax, math.sqrt(ay ** 2 + az ** 2)) * 180.0 / math.pi
            )

            # 15) Delta temporal robusto.
            now       = time.monotonic()
            dt        = now - last_time
            last_time = now
            if dt <= 0 or dt > 0.1:
                dt = 0.01

            gyro_x_rate = gyro["x"] - gx_cal
            gyro_y_rate = gyro["y"] - gy_cal

            angle_pitch = kalman_pitch.get_angle(angle_pitch_acc,  gyro_y_rate, dt)
            angle_roll  = kalman_roll.get_angle(angle_roll_acc,   -gyro_x_rate, dt)

            # 16) Deteccion de mala postura.
            (postura_incorrecta, posible_mala_postura,
             t_inicio_postura, evento,
             pitch_dev, roll_dev) = detectar_cambio_postura(
                angle_pitch, angle_roll, pitch_base, roll_base,
                postura_incorrecta, t_inicio_postura,
                posible_mala_postura, perfil["umbrales"]
            )

            sesion.agregar_muestra(
                pitch_dev, roll_dev, not postura_incorrecta, dt, temp
            )

            # 17) Publicacion de eventos discretos.
            if evento in ("ALERTA", "CORRECCION"):
                sesion.registrar_evento(evento, pitch_dev, roll_dev)
                ev_payload = {
                    "uid":        sesion.uid,
                    "session_id": sesion.session_id,
                    "ts":         datetime.now().isoformat(),
                    "tipo":       evento,
                    "pitch_dev":  round(pitch_dev, 3),
                    "roll_dev":   round(roll_dev,  3),
                    "theta_abs":  round(
                        math.sqrt(pitch_dev ** 2 + roll_dev ** 2), 3
                    )
                }
                firebase_client.publicar_evento(ev_payload)
                mqtt_client.publicar_evento(ev_payload)

            # 18) Publicacion MQTT (mayor frecuencia).
            if (now - last_mqtt_rt) >= MQTT_RT_INTERVAL:
                rt_payload = sesion.payload_realtime(
                    angle_pitch, angle_roll, pitch_dev, roll_dev,
                    postura_incorrecta, temp
                )
                mqtt_client.publicar_realtime(rt_payload)
                last_mqtt_rt = now

            # 19) Publicacion Firebase y sincronizacion eventual de config.
            if (now - last_firebase_rt) >= FIREBASE_RT_INTERVAL:
                rt_payload = sesion.payload_realtime(
                    angle_pitch, angle_roll, pitch_dev, roll_dev,
                    postura_incorrecta, temp
                )
                firebase_client.publicar_realtime(rt_payload)
                last_firebase_rt = now

            if (now - last_estado_rt) >= 15.0:
                if firebase_client.conectado:
                    reportar_estado(
                        firebase_client.db_ref, uid, True, True, True
                    )
                last_estado_rt = now

            if (now - last_config_sync) >= 60.0:
                if firebase_client.conectado:
                    perfil = sincronizar_config_desde_app(
                        firebase_client.db_ref, uid, perfil
                    )
                last_config_sync = now

            # 20) Render local.
            icp          = sesion.calcular_icp()
            clase_ang, _ = clasificar_angulo(
                math.sqrt(pitch_dev ** 2 + roll_dev ** 2)
            )
            mostrar_consola(
                perfil, sesion,
                angle_pitch, angle_roll, pitch_dev, roll_dev,
                postura_incorrecta, evento, temp, icp, clase_ang,
                firebase_client.conectado, mqtt_client.conectado
            )

            time.sleep(0.01)

    except KeyboardInterrupt:
        print("\n[INFO] Interrupcion por teclado.")

    finally:
        # 21) Cierre seguro.
        if firebase_client.conectado:
            try:
                reportar_estado(firebase_client.db_ref, uid, False, False, False)
                firebase_client.db_ref.reference(
                    f"dispositivos/{uid}/control/comando"
                ).set("idle")
            except Exception:
                log.exception("Error reportando estado final")
        finalizar_sesion(sesion, firebase_client, mqtt_client, perfil)
        try:
            GPIO.cleanup()
        except Exception:
            log.exception("Error en GPIO.cleanup()")
        print("Sistema detenido correctamente.")


if __name__ == "__main__":
    main()