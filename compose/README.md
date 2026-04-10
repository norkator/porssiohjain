# RabbitMQ MQTT TLS Notes

This folder contains RabbitMQ config used by Docker Compose.

Production uses:

- plain MQTT on `1883`.
- MQTT over TLS on `8883` what internet-facing clients should use.

The production config in `rabbitmq.config` expects these files inside the container:

```text
/etc/rabbitmq/certs/server.crt
/etc/rabbitmq/certs/server.key
```

You need to mount them from the host in `docker-compose.yml`.

Example volume mount:

```yaml
- /path/on/server/rabbitmq-certs:/etc/rabbitmq/certs:ro
```

## Create a local CA

Create a directory on the server:

```sh
mkdir -p /opt/rabbitmq-certs
cd /opt/rabbitmq-certs
```

Create a CA key and certificate:

```sh
openssl genrsa -out ca.key 4096
openssl req -x509 -new -nodes -key ca.key -sha256 -days 3650 -out ca.crt -subj "/CN=RabbitMQ Local CA"
```

## Create a server certificate

Replace `mqtt.example.com` with the exact DNS name your clients use.

Create an OpenSSL config for the certificate SAN:

```sh
cat > server.cnf <<'EOF'
[req]
default_bits = 2048
prompt = no
default_md = sha256
req_extensions = req_ext
distinguished_name = dn

[dn]
CN = mqtt.example.com

[req_ext]
subjectAltName = @alt_names

[alt_names]
DNS.1 = mqtt.example.com
EOF
```

Create server key and CSR:

```sh
openssl genrsa -out server.key 2048
openssl req -new -key server.key -out server.csr -config server.cnf
```

Sign the server certificate with your CA:

```sh
openssl x509 -req -in server.csr -CA ca.crt -CAkey ca.key -CAcreateserial -out server.crt -days 3650 -sha256 -extensions req_ext -extfile server.cnf
```

Restrict key permissions:

```sh
chmod 600 server.key
chmod 644 server.crt ca.crt
```

## Verify the server certificate

Check subject and SAN:

```sh
openssl x509 -in server.crt -noout -subject -ext subjectAltName
```

Check key and cert match:

```sh
openssl x509 -noout -modulus -in server.crt | openssl md5
openssl rsa -noout -modulus -in server.key | openssl md5
```

## Shelly

Upload `ca.crt` to the Shelly, not `server.key`.

Use the same hostname from the certificate SAN in the device MQTT settings:

```text
mqtt.example.com:8883
```

Do not use the server IP unless you also include that IP as a SAN entry in the certificate.

## Test TLS from another machine

Use the same hostname as the certificate:

```sh
openssl s_client -connect mqtt.example.com:8883 -servername mqtt.example.com -showcerts
```

If the certificate chain is trusted, the output should end with `Verify return code: 0 (ok)`.

## RabbitMQ files in this folder

- `rabbitmq.config`: production config, plain MQTT on `1883` and TLS MQTT on `8883`
- `rabbitmq.local-dev.config`: local development config, plain MQTT only on `1883`

## RabbitMQ server

Remember when mounting server.key and crt to rabbitmq container set permissions:

```terminal
chown 999:999 server.crt server.key
chmod 644 server.crt ca.crt
chmod 600 server.key 
```