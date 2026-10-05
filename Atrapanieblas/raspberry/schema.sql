-- Ejecutar en MySQL Workbench conectado a RDS
CREATE DATABASE IF NOT EXISTS atrapanieblas CHARACTER SET utf8mb4;
USE atrapanieblas;

CREATE TABLE IF NOT EXISTS usuarios (
  id INT AUTO_INCREMENT PRIMARY KEY,
  usuario VARCHAR(50) NOT NULL UNIQUE,
  hash VARCHAR(255) NOT NULL,
  creado TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS sesiones (
  token CHAR(64) PRIMARY KEY,
  usuario_id INT NOT NULL,
  expira DATETIME NOT NULL,
  FOREIGN KEY (usuario_id) REFERENCES usuarios(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS lecturas (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  ts TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  humedad FLOAT NOT NULL,
  nivel INT NOT NULL,
  origen VARCHAR(20) NOT NULL DEFAULT 'MQTT'
);

-- Registro de cada orden de riego (ON/OFF) recibida por el Nodo B
CREATE TABLE IF NOT EXISTS eventos_riego (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  ts TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  accion ENUM('ON','OFF') NOT NULL
);

CREATE INDEX idx_lecturas_ts ON lecturas(ts);

-- Usuario de BD con permisos mínimos (no usar el admin de RDS en la aplicación)
CREATE USER IF NOT EXISTS 'atrapa_app'@'%' IDENTIFIED BY 'CAMBIAR_ESTA_CLAVE';
GRANT SELECT, INSERT, UPDATE, DELETE ON atrapanieblas.* TO 'atrapa_app'@'%';
FLUSH PRIVILEGES;
