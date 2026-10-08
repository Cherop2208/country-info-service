#!/usr/bin/env bash
# Usage: ./scripts/smoke-test.sh [base_url]   (default http://localhost:8080)
set -euo pipefail
BASE="${1:-http://localhost:8080}"
J='Content-Type: application/json'
echo "--- health";   curl -s "$BASE/actuator/health"; echo
echo "--- POST kenya"; curl -s -i -X POST "$BASE/api/v1/countries" -H "$J" -d '{"name":"kenya"}'; echo
echo "--- POST unknown"; curl -s -X POST "$BASE/api/v1/countries" -H "$J" -d '{"name":"Narnia"}'; echo
echo "--- POST blank";   curl -s -X POST "$BASE/api/v1/countries" -H "$J" -d '{"name":""}'; echo
echo "--- GET all";   curl -s "$BASE/api/v1/countries"; echo
echo "--- GET 1";     curl -s "$BASE/api/v1/countries/1"; echo
echo "--- PUT 1";     curl -s -X PUT "$BASE/api/v1/countries/1" -H "$J" -d '{"name":"Kenya","capitalCity":"Nairobi","phoneCode":"254","continentCode":"AF","currencyIsoCode":"KES","countryFlag":"x","languages":[{"isoCode":"swa","name":"Swahili"}]}'; echo
echo "--- DELETE 1";  curl -s -o /dev/null -w "%{http_code}\n" -X DELETE "$BASE/api/v1/countries/1"
echo "--- GET 1 (404)"; curl -s "$BASE/api/v1/countries/1"; echo
