#!/usr/bin/env bash
set -euo pipefail

# killprocesses - kill processes listening on specified ports
# Ports: 8081 (scheduler), 9001 (auth), 8080 (adoptions)

PORTS=(8081 9001 8080)
ANY_KILLED=0

for p in "${PORTS[@]}"; do
  echo "Checking port ${p}..."
  PIDS=""
  if command -v lsof >/dev/null 2>&1; then
    # lsof -t is portable and prints PIDs only
    PIDS=$(lsof -n -iTCP:${p} -sTCP:LISTEN -t 2>/dev/null || true)
  elif command -v ss >/dev/null 2>&1; then
    # Linux fallback
    PIDS=$(ss -ltnp 2>/dev/null | awk -v port=":${p}" '$0 ~ port { gsub(/.*pid=/,"",$0); gsub(/,.*$/,"",$0); print $NF }' || true)
  else
    echo "Neither lsof nor ss found; cannot reliably detect PIDs on port ${p}."
  fi

  if [ -n "${PIDS}" ]; then
    for pid in ${PIDS}; do
      if [ -n "${pid}" ]; then
        echo "Killing PID ${pid} on port ${p} with SIGKILL"
        kill -9 "${pid}" 2>/dev/null || echo "Failed to kill PID ${pid}" >&2
        ANY_KILLED=1
      fi
    done
  else
    echo "No process found listening on port ${p}."
  fi
done

if [ ${ANY_KILLED} -eq 0 ]; then
  echo "No processes were killed."
else
  echo "Done."
fi
