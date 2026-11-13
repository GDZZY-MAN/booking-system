#!/bin/sh
set -eu
if [ "$#" -ne 1 ]; then
  printf 'Usage: sh scripts/verify-backup.sh path/to/backup.dump\n' >&2
  exit 2
fi
case "$1" in
  /*) backup=$1 ;;
  *) backup="$PWD/$1" ;;
esac
test -s "$backup"
cd "$(dirname "$0")/.."
restore_db="restore_$(date -u +%Y%m%d%H%M%S)_$$"
docker compose exec -T db createdb -U shopbooking "$restore_db"
trap 'docker compose exec -T db dropdb -U shopbooking "$restore_db"' EXIT
docker compose exec -T db pg_restore -U shopbooking -d "$restore_db" --no-owner --no-acl --exit-on-error < "$backup"
docker compose exec -T db psql -v ON_ERROR_STOP=1 -U shopbooking -d "$restore_db" \
  -c "SELECT count(*) AS restored_bookings FROM booking;"
constraint_count=$(docker compose exec -T db psql -At -v ON_ERROR_STOP=1 -U shopbooking -d "$restore_db" \
  -c "SELECT count(*) FROM pg_constraint WHERE conrelid = 'booking'::regclass AND conname = 'booking_no_overlap';")
if [ "$constraint_count" != 1 ]; then
  printf 'Restored booking_no_overlap constraint is missing.\n' >&2
  exit 1
fi
