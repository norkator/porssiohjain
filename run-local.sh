#!/bin/sh
# Start a disposable Spring/Vaadin instance. Usage: ./run-local.sh [port]
set -eu

if [ "$#" -gt 1 ]; then
    echo "Usage: $0 [port]" >&2
    exit 1
fi
port=${1:-8080}
case "$port" in
    ''|*[!0-9]*) echo "Port must be a number between 1 and 65535." >&2; exit 1 ;;
esac
if [ "${#port}" -gt 5 ] || [ "$port" -lt 1 ] || [ "$port" -gt 65535 ]; then
    echo "Port must be a number between 1 and 65535." >&2
    exit 1
fi

repo_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
cd "$repo_dir"

is_java_21() {
    [ -x "$1" ] && "$1" -version 2>&1 | grep -Eq 'version "21([.\"]|$)'
}

is_jdk_21() {
    is_java_21 "$1/bin/java" && [ -x "$1/bin/javac" ]
}

# Respect an existing Java 21, then find common system/IDE JDK installations.
if ! is_jdk_21 "${JAVA_HOME:-/nonexistent}"; then
    java_path=$(command -v java || true)
    javac_path=$(command -v javac || true)
    if [ -n "$java_path" ] && is_java_21 "$java_path" && [ -n "$javac_path" ] \
            && "$javac_path" -version 2>&1 | grep -Eq '^javac 21([.]|$)'; then
        unset JAVA_HOME
    else
        java_found=false
        for jdk_dir in "$HOME"/.jdks/* "$HOME"/.sdkman/candidates/java/* /usr/lib/jvm/* /Library/Java/JavaVirtualMachines/*/Contents/Home; do
            if is_jdk_21 "$jdk_dir"; then
                JAVA_HOME=$jdk_dir
                export JAVA_HOME
                java_found=true
                break
            fi
        done
        if [ "$java_found" = false ]; then
            echo "Java 21 JDK is required. Install it and run this script again." >&2
            echo "On Ubuntu/Debian: sudo apt update && sudo apt install openjdk-21-jdk" >&2
            echo "On other systems, install a JDK 21 and put it on PATH or in JAVA_HOME." >&2
            exit 1
        fi
    fi
fi

printf 'Starting disposable local instance: http://127.0.0.1:%s/login\n' "$port"
printf 'Login: admin / admin (enter admin in the UUID field).\nAll database data is discarded on restart. Stop with Ctrl+C.\n'

# A separate config location avoids loading production environment placeholders.
# Pin the database settings so inherited Spring datasource variables cannot select a real database.
exec ./gradlew --console=plain bootRunLocal --args="--spring.profiles.active=local --spring.config.location=classpath:/application-local.properties --server.address=127.0.0.1 --server.port=$port --spring.datasource.url=jdbc:h2:mem:local;MODE=PostgreSQL;DB_CLOSE_ON_EXIT=FALSE --spring.datasource.driver-class-name=org.h2.Driver --spring.jpa.hibernate.ddl-auto=create-drop --spring.flyway.enabled=false --spring.devtools.restart.enabled=false"
