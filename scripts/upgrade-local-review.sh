#!/usr/bin/env bash
# Upgrade an existing local review instance without replacing runtime or volumes.
set -euo pipefail
umask 077
srm_repo=$(cd "$(dirname "$0")/.." && pwd)
srm_release=${1:-"$srm_repo/artifacts/SRM-w10-11-delivery"}
srm_bundle="$srm_repo/artifacts/SRM-local-review"
[[ -f "$srm_release/backend.jar" && -f "$srm_release/workspace/build/workspace.html" && -f "$srm_bundle/runtime/compose.env" ]] || { echo 'Built release and existing initialized review bundle required.' >&2; exit 1; }
cmp -s "$srm_release/public-key.txt" "$srm_bundle/public-key.txt" || { echo "Public authentication key changed; explicit key migration required." >&2; exit 1; }
cd "$srm_bundle"
srm_compose=(docker compose --env-file runtime/compose.env)
srm_sql(){ "${srm_compose[@]}" exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql --default-character-set=utf8mb4 -uroot --batch --skip-column-names snowy'; }
srm_migration="$srm_repo/deploy/migrations/sql/V6__performance_scorecards.sql"
srm_checksum=$(sha256sum "$srm_migration" | cut -d ' ' -f1)
srm_backup="$srm_repo/.runtime/upgrades/$(date +%Y%m%d-%H%M%S)-$$"
mkdir -p "$srm_backup"
"${srm_compose[@]}" exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysqldump -uroot --single-transaction --no-tablespaces --skip-lock-tables snowy' | gzip > "$srm_backup/database.sql.gz"
gzip -t "$srm_backup/database.sql.gz"
cp -a runtime "$srm_backup/runtime"
cp -a backend.jar "$srm_backup/backend.jar"
cp -a build-info.json "$srm_backup/build-info.json"
cp -a workspace "$srm_backup/workspace"
sha256sum runtime/accounts.json runtime/backend.env runtime/compose.env runtime/99-accounts.sql > "$srm_backup/credential-hashes.txt"
printf "CREATE TABLE IF NOT EXISTS SRM_REVIEW_MIGRATION (VERSION int PRIMARY KEY,CHECKSUM char(64) NOT NULL,STATUS varchar(16) NOT NULL,APPLIED_AT datetime(3) NULL);\n" | srm_sql
srm_existing=$(printf 'SELECT CONCAT(CHECKSUM,":",STATUS) FROM SRM_REVIEW_MIGRATION WHERE VERSION=6;\n' | srm_sql)
if [[ -n "$srm_existing" && "$srm_existing" != "$srm_checksum:APPLIED" && "$srm_existing" != "$srm_checksum:STARTED" ]]; then echo 'Migration checksum conflict; no application files replaced.' >&2; exit 1; fi
if [[ -z "$srm_existing" ]]; then
 srm_tables=$(printf "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA='snowy' AND TABLE_NAME IN ('SRM_PERF_TEMPLATE','SRM_PERF_IMPORT','SRM_PERF_INPUT','SRM_SCORECARD','SRM_PERF_SCORE_VERSION','SRM_PERF_APPEAL');\n" | srm_sql)
 [[ "$srm_tables" == 0 ]] || { echo 'Unknown performance schema retained; manual review required.' >&2; exit 1; }
fi
"${srm_compose[@]}" stop gateway backend
srm_restore(){
 echo 'Upgrade failed; restoring previous application files. Database and runtime retained.' >&2
 "${srm_compose[@]}" stop gateway backend || true
 cp -a "$srm_backup/backend.jar" backend.jar
 cp -a "$srm_backup/build-info.json" build-info.json
 cp -a "$srm_backup/workspace/." workspace/
 bash start.sh > "$srm_backup/restore.log" 2>&1 || true
}
trap srm_restore ERR
if [[ "$srm_existing" != "$srm_checksum:APPLIED" ]]; then
 printf "INSERT IGNORE INTO SRM_REVIEW_MIGRATION VALUES(6,'%s','STARTED',NULL);\n" "$srm_checksum" | srm_sql
 srm_sql < "$srm_migration"
 printf "UPDATE SRM_REVIEW_MIGRATION SET STATUS='APPLIED',APPLIED_AT=NOW(3) WHERE VERSION=6 AND CHECKSUM='%s';\n" "$srm_checksum" | srm_sql
fi
cp -a "$srm_release/backend.jar" backend.jar
cp -a "$srm_release/build-info.json" build-info.json
cp -a "$srm_release/sql/06-srm.sql" sql/06-srm.sql
cp -a "$srm_release/workspace/." workspace/
bash start.sh
sha256sum -c "$srm_backup/credential-hashes.txt" >/dev/null
trap - ERR
printf 'Upgrade complete; original runtime and volumes retained. Backup: %s\n' "$srm_backup"
