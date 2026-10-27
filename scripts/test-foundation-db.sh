#!/usr/bin/env bash
# Creates and removes only uniquely named, disposable srm_test_* schemas. Does not alter snowy.
set -euo pipefail
cd "$(dirname "$0")/.."
if [[ -z "${SRM_DB_PASSWORD:-}" ]]; then
  set -a; source .runtime/local.env; set +a
fi
srm_container=${SRM_TEST_MYSQL_CONTAINER:-srm-dev-mysql-1}
srm_suffix="$(date +%s)_${RANDOM}"
srm_fresh="srm_test_fresh_${srm_suffix}"
srm_legacy="srm_test_legacy_${srm_suffix}"
sql() { docker exec -i "$srm_container" sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot --batch --skip-column-names "$@"' sh "$@"; }
cleanup() { printf 'DROP DATABASE IF EXISTS `%s`; DROP DATABASE IF EXISTS `%s`;\n' "$srm_fresh" "$srm_legacy" | sql; }
trap cleanup EXIT
printf 'CREATE DATABASE `%s`; CREATE DATABASE `%s`;\n' "$srm_fresh" "$srm_legacy" | sql
export SRM_MIGRATION_USER=root SRM_MIGRATION_PASSWORD="$SRM_DB_PASSWORD"
flyway() {
 if [[ "${SRM_USE_SYSTEM_MAVEN:-0}" == 1 ]]; then
  mvn -B -ntp -f deploy/migrations/pom.xml "$@"
 else
  ./scripts/mvn.sh -f deploy/migrations/pom.xml "$@"
 fi
}
export SRM_MIGRATION_URL="jdbc:mysql://127.0.0.1:13306/$srm_fresh?allowPublicKeyRetrieval=true&useSSL=false"
flyway flyway:migrate
flyway flyway:migrate flyway:validate
history=$(printf 'SELECT COUNT(*) FROM flyway_schema_history WHERE success=1;\n' | sql "$srm_fresh")
[[ "$history" == 5 ]] || { echo 'Fresh migration count mismatch'; exit 1; }
# Prepare an exact copy of the previous three-table SRM bootstrap, with sample rows.
sql "$srm_legacy" < deploy/sql/01-srm-core.sql
printf "INSERT INTO SRM_SUPPLIER(ID,SUPPLIER_CODE,NAME,CATEGORY,TIER,ORG_ID) VALUES('test-id','DEMO-LEGACY','Migration fixture','TEST','TEMP','test-org');\nINSERT INTO SRM_SUPPLIER_CERT(ID,SUPPLIER_ID,CERT_TYPE,CERT_NO,VALID_FROM,VALID_TO,FILE_ID,STATUS) VALUES('cert-id','test-id','TEST','DEMO','2026-01-01','2027-01-01','demo-file','VALID');\n" | sql "$srm_legacy"
export SRM_MIGRATION_URL="jdbc:mysql://127.0.0.1:13306/$srm_legacy?allowPublicKeyRetrieval=true&useSSL=false"
# This baseline is confined to the fixture schema created above; production adoption is never automatic.
flyway flyway:baseline -Dflyway.baselineVersion=1
flyway flyway:migrate flyway:validate
value=$(printf "SELECT ORG_ID FROM SRM_SUPPLIER_CERT WHERE ID='cert-id';\n" | sql "$srm_legacy")
[[ "$value" == test-org ]] || { echo 'Legacy organization backfill failed'; exit 1; }
# Unique credit fingerprints: same org duplicates fail; another org succeeds.
printf "UPDATE SRM_SUPPLIER SET CREDIT_CODE_FINGERPRINT=REPEAT('a',64) WHERE ID='test-id';\nINSERT INTO SRM_SUPPLIER(ID,SUPPLIER_CODE,NAME,CATEGORY,TIER,ORG_ID,CREDIT_CODE_FINGERPRINT) VALUES('other-id','DEMO-OTHER','Other org','TEST','TEMP','other-org',REPEAT('a',64));\n" | sql "$srm_legacy"
if printf "INSERT INTO SRM_SUPPLIER(ID,SUPPLIER_CODE,NAME,CATEGORY,TIER,ORG_ID,CREDIT_CODE_FINGERPRINT) VALUES('duplicate-id','DEMO-DUP','Duplicate','TEST','TEMP','test-org',REPEAT('a',64));\n" | sql "$srm_legacy" 2>/dev/null; then
 echo 'Expected organization fingerprint uniqueness failure'; exit 1
fi
echo 'PASS: fresh migration, repeat migration, legacy fixture upgrade/backfill, organization uniqueness.'
