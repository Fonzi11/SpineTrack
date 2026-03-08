#!/usr/bin/env python3
# -*- coding: utf-8 -*-
# =====================================
# SISTEMA DE DETECCION DE POSTURA
# SpineTrack - Version de prueba
# Adaptado para Raspberry Pi con libreria mpu6050
# Autores originales: Karen Ballen, Ismael Fonseca, Mafe Tafur
# 2026 - Todos los derechos reservados
# =====================================

from mpu6050 import mpu6050
import RPi.GPIO as GPIO
import math
import time
import os

# =============================
# CONFIGURACION DE PINES (BCM)
# =============================
VIB_PIN1 = 13   # Superior izquierdo
VIB_PIN2 = 25   # Superior derecho
VIB_PIN3 = 24   # Inferior izquierdo (GPIO 24 para evitar conflicto con UART en GPIO 14)
VIB_PIN4 = 26   # Inferior derecho

# =============================
# UMBRALES DE DESVIACION
# =============================
PITCH_ON_THRESHOLD  = 10.0  # Grados para activar alerta
ROLL_ON_THRESHOLD   = 10.0
PITCH_OFF_THRESHOLD = 1.0   # Histeresis: grados para desactivar
ROLL_OFF_THRESHOLD  = 10.0
TIEMPO_CONFIRMACION = 0.5   # segundos (500 ms)


# =============================
# FILTRO KALMAN
# =============================
class KalmanFilter:
    def __init__(self):
        self.Q_angle   = 0.001
        self.Q_bias    = 0.003
        self.R_measure = 0.03
        self.angle = 0.0
        self.bias  = 0.0
        self.P     = [[0.0, 0.0], [0.0, 0.0]]

    def get_angle(self, new_angle, new_rate, dt):
        if dt <= 0:
            return self.angle

        # Prediccion
        rate        = new_rate - self.bias
        self.angle += dt * rate

        # Actualizacion de covarianza
        self.P[0][0] += dt * (dt * self.P[1][1] - self.P[0][1] - self.P[1][0] + self.Q_angle)
        self.P[0][1] -= dt * self.P[1][1]
        self.P[1][0] -= dt * self.P[1][1]
        self.P[1][1] += self.Q_bias * dt

        # Ganancia de Kalman
        S  = self.P[0][0] + self.R_measure
        K0 = self.P[0][0] / S
        K1 = self.P[1][0] / S

        # Correccion con medicion
        y           = new_angle - self.angle
        self.angle += K0 * y
        self.bias  += K1 * y

        # Actualizar matriz P
        P00 = self.P[0][0]
        P01 = self.P[0][1]
        self.P[0][0] -= K0 * P00
        self.P[0][1] -= K0 * P01
        self.P[1][0] -= K1 * P00
        self.P[1][1] -= K1 * P01

        return self.angle


# =============================
# CONTROL DE VIBRADORES
# =============================
def setup_gpio():
    GPIO.setmode(GPIO.BCM)
    GPIO.setwarnings(False)
    for pin in [VIB_PIN1, VIB_PIN2, VIB_PIN3, VIB_PIN4]:
        GPIO.setup(pin, GPIO.OUT)
    desactivar_vibradores()

def activar_vibradores():
    for pin in [VIB_PIN1, VIB_PIN2, VIB_PIN3, VIB_PIN4]:
        GPIO.output(pin, GPIO.HIGH)

def desactivar_vibradores():
    for pin in [VIB_PIN1, VIB_PIN2, VIB_PIN3, VIB_PIN4]:
        GPIO.output(pin, GPIO.LOW)


# =============================
# CALIBRACION DEL GIROSCOPIO
# =============================
def calibrar_giroscopio(mpu):
    print("Calibrando giroscopio (no mover)...")
    gx_cal = gy_cal = gz_cal = 0.0
    muestras = 0

    for _ in range(2000):
        try:
            gyro = mpu.get_gyro_data()
            gx_cal += gyro['x']
            gy_cal += gyro['y']
            gz_cal += gyro['z']
            muestras += 1
        except Exception:
            pass
        time.sleep(0.002)

    if muestras > 0:
        gx_cal /= muestras
        gy_cal /= muestras
        gz_cal /= muestras
        print("Calibracion de giroscopio completada.")
        print("  Offset -> X: %.3f  Y: %.3f  Z: %.3f" % (gx_cal, gy_cal, gz_cal))
    else:
        print("[ERROR] No se pudieron obtener lecturas para calibracion.")

    return gx_cal, gy_cal, gz_cal


# =============================
# CALIBRACION DE POSTURA BASE
# =============================
def calibrar_postura_base(mpu):
    print("\nColoca el dispositivo en la posicion de buena postura.")
    print("La medicion comenzara en 5 segundos...")
    time.sleep(5)

    sum_pitch = sum_roll = 0.0
    muestras = 0

    for _ in range(100):
        try:
            accel = mpu.get_accel_data()
            ax = accel['x']
            ay = accel['y']
            az = accel['z']
            sum_pitch += math.atan2(ay, math.sqrt(ax**2 + az**2)) * 180.0 / math.pi
            sum_roll  += math.atan2(-ax, math.sqrt(ay**2 + az**2)) * 180.0 / math.pi
            muestras  += 1
        except Exception:
            pass
        time.sleep(0.02)

    if muestras > 0:
        pitch_base = sum_pitch / muestras
        roll_base  = sum_roll  / muestras
        print("Postura base calibrada correctamente!")
        print("  Pitch base: %.2f grados" % pitch_base)
        print("  Roll  base: %.2f grados" % roll_base)
        return pitch_base, roll_base
    else:
        print("[ERROR] No se pudieron obtener lecturas para calibracion de postura base.")
        return None, None


# =============================
# DETECCION DE POSTURA
# =============================
def detectar_cambio_postura(angle_pitch, angle_roll, pitch_base, roll_base,
                             estado, t_inicio_postura, posible_mala_postura):
    postura_incorrecta = estado
    evento = None

    pitch_dev = angle_pitch - pitch_base
    roll_dev  = angle_roll  - roll_base

    supera_umbral = (abs(pitch_dev) > PITCH_ON_THRESHOLD or
                     abs(roll_dev)  > ROLL_ON_THRESHOLD)
    restaura_ok   = (abs(pitch_dev) < PITCH_OFF_THRESHOLD and
                     abs(roll_dev)  < ROLL_OFF_THRESHOLD)

    ahora = time.monotonic()

    if supera_umbral and not posible_mala_postura:
        posible_mala_postura = True
        t_inicio_postura     = ahora

    if (posible_mala_postura and supera_umbral and
            (ahora - t_inicio_postura) >= TIEMPO_CONFIRMACION):
        if not postura_incorrecta:
            postura_incorrecta = True
            activar_vibradores()
            evento = 'ALERTA'

    if restaura_ok:
        if postura_incorrecta:
            evento = 'OK'
        postura_incorrecta   = False
        posible_mala_postura = False
        desactivar_vibradores()

    return postura_incorrecta, posible_mala_postura, t_inicio_postura, evento


# =============================
# PROGRAMA PRINCIPAL
# =============================
def main():
    mpu = mpu6050(0x68)
    setup_gpio()

    os.system('clear')
    print("=============================")
    print("       Spinetrack            ")
    print("=============================")
    time.sleep(0.5)

    gx_cal, gy_cal, gz_cal = calibrar_giroscopio(mpu)
    pitch_base, roll_base   = calibrar_postura_base(mpu)

    if pitch_base is None:
        print("[ERROR FATAL] Calibracion fallida. Reinicia el dispositivo.")
        GPIO.cleanup()
        return

    kalman_pitch = KalmanFilter()
    kalman_roll  = KalmanFilter()
    postura_incorrecta   = False
    posible_mala_postura = False
    t_inicio_postura     = 0.0
    last_time            = time.monotonic()

    print("\nMonitoreo iniciado!")
    print("Presiona Ctrl+C para salir\n")

    while True:
        try:
            accel = mpu.get_accel_data()
            gyro  = mpu.get_gyro_data()
            temp  = mpu.get_temp()
        except Exception as e:
            print("[ERROR] Lectura sensor: " + str(e))
            time.sleep(0.005)
            continue

        ax = accel['x']
        ay = accel['y']
        az = accel['z']

        angle_pitch_acc = math.atan2(ay, math.sqrt(ax**2 + az**2)) * 180.0 / math.pi
        angle_roll_acc  = math.atan2(-ax, math.sqrt(ay**2 + az**2)) * 180.0 / math.pi

        now       = time.monotonic()
        dt        = now - last_time
        last_time = now
        if dt <= 0 or dt > 0.1:
            dt = 0.01

        gyro_x_rate =  gyro['x'] - gx_cal
        gyro_y_rate =  gyro['y'] - gy_cal

        angle_pitch = kalman_pitch.get_angle(angle_pitch_acc,  gyro_y_rate, dt)
        angle_roll  = kalman_roll.get_angle(angle_roll_acc,   -gyro_x_rate, dt)

        postura_incorrecta, posible_mala_postura, t_inicio_postura, evento = \
            detectar_cambio_postura(
                angle_pitch, angle_roll,
                pitch_base, roll_base,
                postura_incorrecta, t_inicio_postura, posible_mala_postura
            )

        os.system('clear')
        print("=== Spinetrack - Version de prueba ===")
        print("Temp       : %.2f C" % temp)
        print("-" * 45)

        print("ACELEROMETRO (g)")
        print("  Acc X    : %+.3f" % ax)
        print("  Acc Y    : %+.3f" % ay)
        print("  Acc Z    : %+.3f" % az)
        print("-" * 45)

        print("GIROSCOPIO (grados/s)")
        print("  Gyro X   : %+.3f" % gyro['x'])
        print("  Gyro Y   : %+.3f" % gyro['y'])
        print("  Gyro Z   : %+.3f" % gyro['z'])
        print("-" * 45)

        print("ANGULOS FILTRADOS (Kalman)")
        print("  Pitch    : %+.2f grados  (base: %+.2f  dev: %+.2f)" % (angle_pitch, pitch_base, angle_pitch - pitch_base))
        print("  Roll     : %+.2f grados  (base: %+.2f  dev: %+.2f)" % (angle_roll,  roll_base,  angle_roll  - roll_base))
        print("-" * 45)

        if postura_incorrecta:
            print("ESTADO     : *** MALA POSTURA ***")
        else:
            print("ESTADO     : OK - BUENA POSTURA")

        if evento == 'ALERTA':
            print(">>> ALERTA: Postura incorrecta detectada <<<")
        elif evento == 'OK':
            print(">>> Postura corregida <<<")

        print("-" * 45)
        print("Presiona Ctrl+C para salir")

        time.sleep(0.01)


# =============================
# PUNTO DE ENTRADA
# =============================
if __name__ == "__main__":
    try:
        main()
    except KeyboardInterrupt:
        print("\n[INFO] Interrupcion por teclado.")
    finally:
        desactivar_vibradores()
        GPIO.cleanup()
        print("Sistema detenido correctamente.")
