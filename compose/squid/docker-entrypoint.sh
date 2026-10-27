#!/bin/sh
set -eu

if [ "${1:-}" != squid ]; then
    exec "$@"
fi

# Keep explicitly mounted configurations working for advanced deployments.
if [ -f /etc/squid/squid.conf ]; then
    cp /etc/squid/squid.conf /tmp/squid.conf
else
    if [ -z "${SQUID_ALLOWED_CLIENTS:-}" ]; then
        echo 'Set SQUID_ALLOWED_CLIENTS to the backend source IP or CIDR (for example 203.0.113.10/32). No config mount is needed.' >&2
        exit 1
    fi

    # Normalize a space-separated list without allowing Squid directives or
    # shell glob expansion in environment-provided configuration.
    set -f
    allowed_clients=
    for client in $SQUID_ALLOWED_CLIENTS; do
        case "$client" in
            *[!0-9a-fA-F:./]*)
                echo 'SQUID_ALLOWED_CLIENTS must contain only IP addresses or CIDRs separated by spaces.' >&2
                exit 1
                ;;
        esac
        allowed_clients="${allowed_clients:+$allowed_clients }$client"
    done
    if [ -z "$allowed_clients" ]; then
        echo 'SQUID_ALLOWED_CLIENTS must contain at least one backend IP address or CIDR.' >&2
        exit 1
    fi

    sed "s|@SQUID_ALLOWED_CLIENTS@|$allowed_clients|g" \
        /etc/squid/squid.conf.template > /tmp/squid.conf
fi

exec "$@"
