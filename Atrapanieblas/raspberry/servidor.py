"""
Servidor de la Raspberry (RASPBIAN-IOT-V2):
  1) API REST HTTPS  -> login/registro/historial contra MySQL (RDS)
  2) Puente MQTT     -> guarda en MySQL cada lectura del Nodo A
  3) Nodo B (riego)  -> recibe ON/OFF, acciona el relé (si hay GPIO) y publica el estado
"""
import json, os, secrets, threading, time
from datetime import datetime, timedelta

import paho.mqtt.client as mqtt
import pymysql
from dotenv import load_dotenv
from flask import Flask, jsonify, request
from werkzeug.security import check_password_hash, generate_password_hash

load_dotenv()
BASE = "atrapanieblas/meneses-quintoman"
T_DATOS, T_CMD, T_ESTADO = f"{BASE}/captador/datos", f"{BASE}/riego/cmd", f"{BASE}/riego/estado"

try:  # relé en GPIO17 (opcional)
    from gpiozero import OutputDevice
    rele = OutputDevice(17, active_high=False, initial_value=False)
except Exception:
    rele = None

app = Flask(__name__)
fallos = {}  # usuario -> (intentos, bloqueado_hasta)


def conexion():
    ssl = {"ca": os.environ["DB_SSL_CA"]} if os.environ.get("DB_SSL_CA") else None
    return pymysql.connect(host=os.environ["DB_HOST"], user=os.environ["DB_USER"],
                           password=os.environ["DB_PASS"], database=os.environ["DB_NAME"],
                           cursorclass=pymysql.cursors.DictCursor, autocommit=True, ssl=ssl)


def usuario_de_token():
    auth = request.headers.get("Authorization", "")
    if not auth.startswith("Bearer "):
        return None
    with conexion() as cn, cn.cursor() as c:
        c.execute("SELECT usuario_id FROM sesiones WHERE token=%s AND expira>NOW()", (auth[7:],))
        r = c.fetchone()
        return r["usuario_id"] if r else None


@app.post("/api/registro")
def registro():
    d = request.get_json(silent=True) or {}
    u, p = str(d.get("usuario", "")).strip().lower(), str(d.get("password", ""))
    if len(u) < 3 or len(u) > 50 or len(p) < 8 or not any(x.isalpha() for x in p) or not any(x.isdigit() for x in p):
        return jsonify(error="Datos inválidos (contraseña: 8+ con letras y números)"), 400
    try:
        with conexion() as cn, cn.cursor() as c:
            c.execute("INSERT INTO usuarios(usuario, hash) VALUES (%s,%s)", (u, generate_password_hash(p)))
    except pymysql.err.IntegrityError:
        return jsonify(error="Ese usuario ya existe"), 409
    return jsonify(ok=True), 201


@app.post("/api/login")
def login():
    d = request.get_json(silent=True) or {}
    u, p = str(d.get("usuario", "")).strip().lower(), str(d.get("password", ""))
    intentos, hasta = fallos.get(u, (0, 0))
    if time.time() < hasta:
        return jsonify(error="Cuenta bloqueada temporalmente"), 429
    with conexion() as cn, cn.cursor() as c:
        c.execute("SELECT id, hash FROM usuarios WHERE usuario=%s", (u,))
        r = c.fetchone()
        if not r or not check_password_hash(r["hash"], p):
            intentos += 1
            fallos[u] = (intentos, time.time() + 300 if intentos >= 5 else 0)
            return jsonify(error="Credenciales incorrectas"), 401
        fallos.pop(u, None)
        token = secrets.token_hex(32)
        c.execute("INSERT INTO sesiones(token, usuario_id, expira) VALUES (%s,%s,%s)",
                  (token, r["id"], datetime.now() + timedelta(hours=8)))
    return jsonify(token=token, mqtt_user=os.environ["MQTT_APP_USER"], mqtt_pass=os.environ["MQTT_APP_PASS"])


@app.get("/api/lecturas")
def lecturas():
    if not usuario_de_token():
        return jsonify(error="No autorizado"), 401
    limit = max(1, min(int(request.args.get("limit", 100)), 500))
    with conexion() as cn, cn.cursor() as c:
        c.execute("SELECT UNIX_TIMESTAMP(ts)*1000 AS ts, humedad, nivel, origen FROM lecturas ORDER BY id DESC LIMIT %s", (limit,))
        filas = c.fetchall()
    for f in filas:
        f["ts"] = int(f["ts"])
    prom = sum(f["humedad"] for f in filas) / len(filas) if filas else 0
    return jsonify(lecturas=filas, promedio_humedad=prom)


# ---------- MQTT ----------
def on_connect(cl, ud, flags, rc, props=None):
    print("MQTT local conectado:", rc)
    cl.subscribe([(T_DATOS, 1), (T_CMD, 1)])


def on_message(cl, ud, msg):
    try:
        txt = msg.payload.decode().strip()
        if msg.topic == T_DATOS:
            j = json.loads(txt)
            with conexion() as cn, cn.cursor() as c:
                c.execute("INSERT INTO lecturas(humedad, nivel, origen) VALUES (%s,%s,'MQTT')",
                          (float(j["humedad"]), int(j["nivel"])))
        elif msg.topic == T_CMD and txt in ("ON", "OFF"):
            if rele:
                rele.on() if txt == "ON" else rele.off()
            with conexion() as cn, cn.cursor() as c:
                c.execute("INSERT INTO eventos_riego(accion) VALUES (%s)", (txt,))
            cl.publish(T_ESTADO, json.dumps({"riego": txt}), qos=1, retain=True)
    except Exception as e:
        print("Mensaje ignorado:", e)


def iniciar_mqtt():
    cl = mqtt.Client(mqtt.CallbackAPIVersion.VERSION2, client_id="servidor-raspberry")
    cl.on_connect, cl.on_message = on_connect, on_message
    cl.connect(os.environ.get("MQTT_LOCAL_HOST", "127.0.0.1"), 1883, 30)
    cl.loop_forever()


if __name__ == "__main__":
    threading.Thread(target=iniciar_mqtt, daemon=True).start()
    app.run(host="0.0.0.0", port=5000, ssl_context=(os.environ["TLS_CERT"], os.environ["TLS_KEY"]))
