#!/usr/bin/env bash
# Upgrade an existing local review instance without replacing runtime or volumes.
set -euo pipefail
umask 077
srm_repo=$(cd "$(dirname "$0")/.." && pwd)
srm_release=${1:-"$srm_repo/artifacts/SRM-w12-15-review"}
srm_release=$(realpath "$srm_release")
srm_bundle="$srm_repo/artifacts/SRM-local-review"
[[ -f "$srm_release/backend.jar" && -f "$srm_release/workspace/build/workspace.html" && -f "$srm_bundle/runtime/compose.env" ]] || { echo 'Built release and existing initialized review bundle required.' >&2; exit 1; }
cmp -s "$srm_release/public-key.txt" "$srm_bundle/public-key.txt" || { echo "Public authentication key changed; explicit key migration required." >&2; exit 1; }
cd "$srm_bundle"
srm_compose=(docker compose --env-file runtime/compose.env)
srm_sql(){ "${srm_compose[@]}" exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql --default-character-set=utf8mb4 -uroot --batch --skip-column-names snowy'; }
srm_backup="$srm_repo/.runtime/upgrades/$(date +%Y%m%d-%H%M%S)-$$"
mkdir -p "$srm_backup"
"${srm_compose[@]}" exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysqldump -uroot --single-transaction --no-tablespaces --skip-lock-tables snowy' | gzip > "$srm_backup/database.sql.gz"
gzip -t "$srm_backup/database.sql.gz"
cp -a runtime "$srm_backup/runtime"
cp -a backend.jar "$srm_backup/backend.jar"
cp -a build-info.json "$srm_backup/build-info.json"
cp -a workspace "$srm_backup/workspace"
cp -a scripts "$srm_backup/scripts"
cp -a initialize-workflows.mjs "$srm_backup/initialize-workflows.mjs"
cp -a compose.yml start.sh "$srm_backup/"
if [[ -f initialize-collaboration-sandbox.mjs ]]; then cp -a initialize-collaboration-sandbox.mjs sandbox "$srm_backup/"; fi
sha256sum runtime/accounts.json runtime/backend.env runtime/compose.env runtime/99-accounts.sql > "$srm_backup/credential-hashes.txt"
printf "CREATE TABLE IF NOT EXISTS SRM_REVIEW_MIGRATION (VERSION int PRIMARY KEY,CHECKSUM char(64) NOT NULL,STATUS varchar(16) NOT NULL,APPLIED_AT datetime(3) NULL);\n" | srm_sql
declare -A srm_states srm_checksums srm_paths
for srm_version in 6 7 8 9; do
 srm_candidates=("$srm_repo"/deploy/migrations/sql/V"$srm_version"__*.sql)
 [[ ${#srm_candidates[@]} == 1 && -f "${srm_candidates[0]}" ]] || { echo 'Missing or ambiguous migration.' >&2; exit 1; }
 srm_paths[$srm_version]=${srm_candidates[0]}
 srm_checksum=$(sha256sum "${srm_candidates[0]}" | cut -d ' ' -f1)
 srm_checksums[$srm_version]=$srm_checksum
 srm_packaged="$srm_release/sql/$(printf '%02d' "$srm_version")-srm.sql"
 [[ -f "$srm_packaged" ]] || { echo 'Release migration is missing.' >&2; exit 1; }
 srm_package_hash=$(tail -n +2 "$srm_packaged" | sha256sum | cut -d ' ' -f1)
 [[ "$srm_package_hash" == "$srm_checksum" ]] || { echo 'Release/source migration mismatch; existing application retained.' >&2; exit 1; }
 srm_existing=$(printf 'SELECT CONCAT(CHECKSUM,":",STATUS) FROM SRM_REVIEW_MIGRATION WHERE VERSION=%s;\n' "$srm_version" | srm_sql)
 srm_states[$srm_version]=$srm_existing
 [[ -z "$srm_existing" || "$srm_existing" == "$srm_checksum:APPLIED" || "$srm_existing" == "$srm_checksum:STARTED" ]] || { echo 'Migration checksum conflict; application retained.' >&2; exit 1; }
 if [[ -z "$srm_existing" ]]; then
  if [[ "$srm_version" == 6 ]]; then srm_table_list="'SRM_PERF_TEMPLATE','SRM_PERF_IMPORT','SRM_PERF_INPUT','SRM_SCORECARD','SRM_PERF_SCORE_VERSION','SRM_PERF_APPEAL'"; elif [[ "$srm_version" == 7 ]]; then srm_table_list="'SRM_SRC_RFQ','SRM_SRC_INVITE','SRM_SRC_QUOTE','SRM_SRC_BID','SRM_SRC_QUESTION','SRM_SRC_FILE','SRM_SRC_RISK','SRM_SRC_AWARD','SRM_SRC_AWARD_ACTION','SRM_SRC_NOTICE','SRM_SRC_EVENT'"; elif [[ "$srm_version" == 8 ]]; then srm_table_list="'SRM_COL_PO','SRM_XCH_CONNECTOR','SRM_COL_RECON','SRM_COL_INVOICE'"; else srm_table_list="'SRM_QL_CASE','SRM_QL_RISK','SRM_QL_INSPECTION'"; fi
  srm_tables=$(printf "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA='snowy' AND TABLE_NAME IN (%s);\n" "$srm_table_list" | srm_sql)
  [[ "$srm_tables" == 0 ]] || { echo 'Unknown existing schema retained; manual migration review required.' >&2; exit 1; }
 fi
 done
"${srm_compose[@]}" stop gateway backend
srm_restore(){
 echo 'Upgrade failed; restoring previous application files. Database and runtime retained.' >&2
 "${srm_compose[@]}" stop gateway backend || true
 cp -a "$srm_backup/compose.yml" "$srm_backup/start.sh" .
 if [[ -f "$srm_backup/initialize-collaboration-sandbox.mjs" ]]; then cp -a "$srm_backup/initialize-collaboration-sandbox.mjs" .; cp -a "$srm_backup/sandbox/." sandbox/; fi
 cp -a "$srm_backup/backend.jar" backend.jar
 cp -a "$srm_backup/build-info.json" build-info.json
 cp -a "$srm_backup/workspace/." workspace/
 cp -a "$srm_backup/scripts/." scripts/
 cp -a "$srm_backup/initialize-workflows.mjs" initialize-workflows.mjs
 bash start.sh > "$srm_backup/restore.log" 2>&1 || true
}
trap srm_restore ERR
for srm_version in 6 7 8 9; do
 srm_checksum=${srm_checksums[$srm_version]}
 if [[ "${srm_states[$srm_version]}" != "$srm_checksum:APPLIED" ]]; then
  printf "INSERT IGNORE INTO SRM_REVIEW_MIGRATION VALUES(%s,'%s','STARTED',NULL);\n" "$srm_version" "$srm_checksum" | srm_sql
  srm_sql < "${srm_paths[$srm_version]}"
  printf "UPDATE SRM_REVIEW_MIGRATION SET STATUS='APPLIED',APPLIED_AT=NOW(3) WHERE VERSION=%s AND CHECKSUM='%s';\n" "$srm_version" "$srm_checksum" | srm_sql
 fi
 cp -a "$srm_release/sql/$(printf '%02d' "$srm_version")-srm.sql" sql/
 done
cp -a "$srm_release/backend.jar" backend.jar
cp -a "$srm_release/build-info.json" build-info.json
cp -a "$srm_release/scripts/." scripts/
cp -a "$srm_release/initialize-workflows.mjs" initialize-workflows.mjs
cp -a "$srm_release/workspace/." workspace/
cp -a "$srm_release/compose.yml" "$srm_release/start.sh" "$srm_release/initialize-collaboration-sandbox.mjs" .
mkdir -p sandbox
cp -a "$srm_release/sandbox/." sandbox/
bash start.sh
sha256sum -c "$srm_backup/credential-hashes.txt" >/dev/null
trap - ERR
printf 'Upgrade complete; original runtime and volumes retained. Backup: %s\n' "$srm_backup"
