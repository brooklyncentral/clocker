#! /usr/bin/env bash
set -e

PORT="${brooklyn.console}"
URL="http://localhost:${PORT}"

$1 launch --port "$PORT" --persist disabled 2>&1 | tee brooklyn.log > /dev/null 2>&1 &

# Wait for the launched server to answer.
MAX_POLLS=10
POLL_WAIT=2
MAX_WAIT=$((MAX_POLLS*POLL_WAIT))

echo "Waiting up to ${MAX_WAIT}s for server to respond at ${URL}"

ATTEMPT=0
while [ "$ATTEMPT" -lt "$MAX_POLLS" ]; do
    if $(curl "$URL" > /dev/null 2>&1); then
        echo "Ready"
        exit 0
    fi
    ATTEMPT=$((ATTEMPT+1))
    sleep "$POLL_WAIT"
done

echo "Brooklyn not responding after ${MAX_WAIT}s"
exit 1
