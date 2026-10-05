"""Simula el Nodo A (DHT22 + HC-SR04) publicando cada 5 s. Uso: python3 simular_nodo_a.py"""
import json, random, time
import paho.mqtt.client as mqtt

cl = mqtt.Client(mqtt.CallbackAPIVersion.VERSION2, client_id="nodo-a-simulado")
cl.connect("127.0.0.1", 1883, 30)
cl.loop_start()
nivel = 80
while True:
    hum = round(random.uniform(60, 98), 1)
    nivel = max(5, min(100, nivel + random.randint(-6, 4)))
    cl.publish("atrapanieblas/meneses-quintoman/captador/datos", json.dumps({"humedad": hum, "nivel": nivel}), qos=1)
    print("publicado", hum, nivel)
    time.sleep(5)
