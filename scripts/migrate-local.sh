#!/usr/bin/env bash
set -euo pipefail
umask 077
umask 077
cd "$(dirname "$0")/.."
set -a; source .runtime/local.env; set +a
compose=(docker compose --env-file .runtime/local.env -f deploy/compose.yml)
sql() { "${compose[@]}" exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot --batch --skip-column-names "$@"' sh "$@"; }
export SRM_MIGRATION_URL='jdbc:mysql://127.0.0.1:13306/snowy?allowPublicKeyRetrieval=true&useSSL=false'
export SRM_MIGRATION_USER=root SRM_MIGRATION_PASSWORD="$SRM_DB_PASSWORD"
history=$(printf "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA='snowy' AND TABLE_NAME='flyway_schema_history';\n" | sql)
version=0
if [[ "$history" == 1 ]]; then version=$(printf "SELECT COALESCE(MAX(CAST(version AS UNSIGNED)),0) FROM snowy.flyway_schema_history WHERE success=1;\n" | sql); fi
if [[ "$version" -lt 6 ]]; then
  mkdir -p .runtime/backups
  backup=".runtime/backups/snowy-before-migration-$(date +%Y%m%d%H%M%S).sql.gz"
  "${compose[@]}" exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysqldump -uroot --single-transaction --no-tablespaces --skip-lock-tables snowy' | gzip > "$backup"
  gzip -t "$backup"
  [[ -s "$backup" ]] || { echo 'Backup verification failed'; exit 1; }
  if [[ "$history" == 0 ]]; then
    count=$(printf "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA='snowy' AND TABLE_NAME IN ('SRM_SUPPLIER','SRM_SUPPLIER_CERT','SRM_SUPPLIER_CONTACT');\n" | sql)
    if [[ "$count" == 3 ]]; then
      reference="srm_test_baseline_$(date +%s)_${RANDOM}"
      trap 'printf "DROP DATABASE IF EXISTS \`%s\`;\n" "$reference" | sql' EXIT
      printf 'CREATE DATABASE `%s`;\n' "$reference" | sql
      sql "$reference" < deploy/migrations/sql/V1__supplier_core.sql
      for table in SRM_SUPPLIER SRM_SUPPLIER_CERT SRM_SUPPLIER_CONTACT; do
        metadata="SELECT CONCAT(COLUMN_NAME,':',COLUMN_TYPE,':',IS_NULLABLE,':',COALESCE(COLUMN_DEFAULT,'NULL')) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='%s' AND TABLE_NAME='$table' ORDER BY ORDINAL_POSITION;"
        actual=$(printf "$metadata" snowy | sql)
        expected=$(printf "$metadata" "$reference" | sql)
        [[ "$actual" == "$expected" ]] || { echo "Existing $table differs from V1; manual migration review required"; exit 1; }
      done
      sensitive=$(printf "SELECT (SELECT COUNT(*) FROM snowy.SRM_SUPPLIER WHERE COALESCE(CREDIT_CODE,'')<>'' OR COALESCE(BANK_ACCOUNT,'')<>'')+(SELECT COUNT(*) FROM snowy.SRM_SUPPLIER_CONTACT WHERE COALESCE(PHONE,'')<>'');\n" | sql)
      [[ "$sensitive" == 0 ]] || { echo 'Legacy sensitive values require controlled key migration; no baseline applied'; exit 1; }
      ./scripts/mvn.sh -f deploy/migrations/pom.xml flyway:baseline -Dflyway.baselineVersion=1
    elif [[ "$count" != 0 ]]; then
      echo 'Partial unknown SRM schema detected; migration stopped'; exit 1
    fi
  fi
fi
./scripts/mvn.sh -f deploy/migrations/pom.xml flyway:migrate flyway:validate
