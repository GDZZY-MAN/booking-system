#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
umask 077
destination=${1:-backups}
mkdir -p "$destination"
file="$destination/youyue-$(date -u +%Y%m%dT%H%M%SZ)-$$.dump"
temporary="$file.partial"
trap 'rm -f -- "$temporary"' EXIT
docker compose exec -T db pg_dump -U shopbooking -d shopbooking -Fc --no-owner --no-acl > "$temporary"
test -s "$temporary"
mv -- "$temporary" "$file"
printf '%s\n' "$file"
