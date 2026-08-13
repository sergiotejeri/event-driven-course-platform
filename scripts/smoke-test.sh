#!/usr/bin/env sh
set -eu

BASE_URL="${COURSE_PLATFORM_URL:-http://localhost:8080}"
PASS_COUNT=0

pass() {
  PASS_COUNT=$((PASS_COUNT + 1))
  printf '[%s/8] PASS %s\n' "$PASS_COUNT" "$1"
}

json_value() {
  python3 -c 'import json,sys; print(json.load(sys.stdin)[sys.argv[1]])' "$1"
}

curl -fsS "$BASE_URL/actuator/health/readiness" | python3 -c 'import json,sys; assert json.load(sys.stdin)["status"] == "UP"'
pass 'application is ready'

ADMIN_TOKEN=$(curl -fsS -X POST "$BASE_URL/api/v1/auth/login" -H 'Content-Type: application/json' -d '{"email":"admin@example.test","password":"password"}' | json_value token)
test -n "$ADMIN_TOKEN"
pass 'admin authenticated'

SUFFIX=$(python3 -c 'import uuid; print(uuid.uuid4().hex)')
CATEGORY_ID=$(curl -fsS -X POST "$BASE_URL/api/v1/categories" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' -d "{\"name\":\"Smoke $SUFFIX\",\"description\":\"Docker smoke test\"}" | json_value id)
COURSE_ID=$(curl -fsS -X POST "$BASE_URL/api/v1/courses" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' -d "{\"title\":\"Event-driven smoke $SUFFIX\",\"description\":\"End-to-end Docker verification\",\"estimatedHours\":4,\"level\":\"INTERMEDIATE\",\"price\":49.90,\"currency\":\"EUR\",\"capacity\":2,\"categoryId\":\"$CATEGORY_ID\",\"instructorId\":\"20000000-0000-0000-0000-000000000002\"}" | json_value id)
pass 'catalog resources created'

curl -fsS -X POST "$BASE_URL/api/v1/courses/$COURSE_ID/publish" -H "Authorization: Bearer $ADMIN_TOKEN" | python3 -c 'import json,sys; assert json.load(sys.stdin)["status"] == "PUBLISHED"'
pass 'course published'

STUDENT_TOKEN=$(curl -fsS -X POST "$BASE_URL/api/v1/auth/login" -H 'Content-Type: application/json' -d '{"email":"student@example.test","password":"password"}' | json_value token)
test -n "$STUDENT_TOKEN"
pass 'student authenticated'

ENROLLMENT_ID=$(curl -fsS -X POST "$BASE_URL/api/v1/courses/$COURSE_ID/enrollments" -H "Authorization: Bearer $STUDENT_TOKEN" -H "Idempotency-Key: smoke-$SUFFIX" -H "X-Correlation-Id: $(python3 -c 'import uuid; print(uuid.uuid4())')" | json_value enrollmentId)
ACTIVE=''
attempt=0
while [ "$attempt" -lt 30 ]; do
  ACTIVE=$(curl -fsS "$BASE_URL/api/v1/enrollments/$ENROLLMENT_ID" -H "Authorization: Bearer $STUDENT_TOKEN" || true)
  if printf '%s' "$ACTIVE" | python3 -c 'import json,sys; assert json.load(sys.stdin)["status"] == "ACTIVE"' 2>/dev/null; then break; fi
  attempt=$((attempt + 1))
  sleep 1
done
printf '%s' "$ACTIVE" | python3 -c 'import json,sys; assert json.load(sys.stdin)["status"] == "ACTIVE"'
pass 'asynchronous payment activated enrollment'

curl -fsS -X PATCH "$BASE_URL/api/v1/enrollments/$ENROLLMENT_ID/progress" -H "Authorization: Bearer $STUDENT_TOKEN" -H 'Content-Type: application/json' -d '{"progress":100}' | python3 -c 'import json,sys; assert json.load(sys.stdin)["completedNow"] is True'
pass 'enrollment completed'

CERTIFICATE=''
attempt=0
while [ "$attempt" -lt 30 ]; do
  CERTIFICATE=$(curl -fsS "$BASE_URL/api/v1/certificates/enrollment/$ENROLLMENT_ID" -H "Authorization: Bearer $STUDENT_TOKEN" || true)
  if printf '%s' "$CERTIFICATE" | python3 -c 'import json,sys; assert json.load(sys.stdin).get("verificationCode")' 2>/dev/null; then break; fi
  attempt=$((attempt + 1))
  sleep 1
done
VERIFICATION_CODE=$(printf '%s' "$CERTIFICATE" | json_value verificationCode)
curl -fsS "$BASE_URL/api/v1/certificates/verify/$VERIFICATION_CODE" | python3 -c 'import json,sys; assert json.load(sys.stdin)["enrollmentId"] == sys.argv[1]' "$ENROLLMENT_ID"
pass 'certificate issued and publicly verified'

printf '8/8 PASS\n'

