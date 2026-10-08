# Country Info Service (NCBA Integration Microservices Engineer exercise)

Spring Boot 3 / Java 17 microservice that accepts a country name over REST, calls the public **CountryInfoService SOAP API**
(name → ISO code → full country info), stores the result in **MySQL**, and exposes CRUD endpoints. Ships with Docker, Kubernetes manifests and ops guides.

## Flow
```
POST /api/v1/countries {"name":"kenya"}
  → Controller → Service (sentence case "Kenya")
  → SOAP CountryISOCode(sCountryName)        → "KE"
  → SOAP FullCountryInfo(sCountryISOCode)    → name, capital, phone, currency, flag, languages
  → upsert CountryInfo + Language rows (MySQL)  → 201 Created (200 if already stored)
```

## Endpoints
| Method | Path | Purpose | Codes |
|---|---|---|---|
| POST | `/api/v1/countries` | Look up via SOAP + store | 201, 200, 400, 404, 503 |
| GET | `/api/v1/countries?page=0&size=20&sort=name` | List (paged) | 200, 400 |
| GET | `/api/v1/countries/{id}` | Get one | 200, 404 |
| PUT | `/api/v1/countries/{id}` | Update | 200, 400, 404 |
| DELETE | `/api/v1/countries/{id}` | Delete | 204, 404 |
| GET | `/swagger-ui.html`, `/v3/api-docs` | Interactive API docs (OpenAPI) | 200 |
| GET | `/actuator/health/{liveness,readiness}`, `/actuator/prometheus` | Ops | |

Errors always look like: `{timestamp,status,error,message,path,correlationId,details[]}`.

## Run & test locally
**Option A – Docker Compose (easiest)**
```bash
docker compose up --build
./scripts/smoke-test.sh            # exercises every endpoint
```
**Option B – Maven + local MySQL**
```bash
docker run -d --name mysql -p 3306:3306 -e MYSQL_ROOT_PASSWORD=root -e MYSQL_DATABASE=countrydb mysql:8.4
mvn clean verify        # unit tests (controller, service, SOAP client, parsing)
mvn spring-boot:run
```
Manual test:
```bash
curl -X POST localhost:8080/api/v1/countries -H 'Content-Type: application/json' -d '{"name":"tanzania"}'
curl localhost:8080/api/v1/countries
```
SoapUI check of the upstream (optional): import `http://webservices.oorsprong.org/websamples.countryinfo/CountryInfoService.wso?WSDL`, run `CountryISOCode` and `FullCountryInfo`.

## Deploy to Kubernetes
See [docs/DEPLOYMENT.md](docs/DEPLOYMENT.md) (quick: `LOAD=minikube ./scripts/deploy.sh`) and [docs/TROUBLESHOOTING.md](docs/TROUBLESHOOTING.md).
System design & trade-offs: [docs/DESIGN.md](docs/DESIGN.md).

## Push to GitHub
```bash
git init && git add . && git commit -m "Country info integration service"
git branch -M main && git remote add origin https://github.com/<you>/country-info-service.git && git push -u origin main
```
