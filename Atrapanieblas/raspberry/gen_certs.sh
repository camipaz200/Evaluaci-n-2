#!/bin/bash
# Uso: ./gen_certs.sh 192.168.1.50      (IP de la Raspberry en la red WiFi)
set -e
IP="$1"; [ -z "$IP" ] && { echo "Uso: $0 <IP_RASPBERRY>"; exit 1; }
mkdir -p certs && cd certs
openssl genrsa -out ca.key 4096
openssl req -x509 -new -key ca.key -sha256 -days 825 -out ca.crt -subj "/CN=Atrapanieblas-CA"
openssl genrsa -out server.key 2048
openssl req -new -key server.key -out server.csr -subj "/CN=$IP"
printf "subjectAltName=IP:$IP\nextendedKeyUsage=serverAuth\n" > ext.cnf
openssl x509 -req -in server.csr -CA ca.crt -CAkey ca.key -CAcreateserial -out server.crt -days 825 -sha256 -extfile ext.cnf
chmod 600 *.key
echo "Listo. Copia certs/ca.crt a la app: app/src/main/res/raw/ca.crt"
