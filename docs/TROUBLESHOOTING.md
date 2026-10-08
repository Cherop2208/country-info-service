# Troubleshooting on Kubernetes
Set `alias k='kubectl -n country-info'`.

## Quick triage
```bash
k get pods -o wide            # status, restarts, node
k describe pod <pod>          # Events at the bottom = first place to look
k logs deploy/country-info-service --tail=100
k logs <pod> --previous       # logs of the crashed container
k get events --sort-by=.lastTimestamp | tail -20
k top pods
```
## Symptom → cause → fix
| Symptom | Likely cause | Fix |
|---|---|---|
| `ImagePullBackOff` / `ErrImagePull` | Image not in cluster/registry, wrong tag, missing pull secret | `minikube image load` / `kind load`, or push and set `REGISTRY`; add imagePullSecret |
| `Pending` | Not enough CPU/memory, PVC unbound | `k describe pod`; lower requests or add nodes; `k get pvc` and check StorageClass |
| `CrashLoopBackOff` | DB unreachable, wrong credentials, bad env | `k logs --previous`; check `DB_URL`, secret values; confirm `mysql-0` is Ready |
| `OOMKilled` (describe shows reason) | Memory limit too low | Raise limit in deployment.yaml; keep `MaxRAMPercentage=75` |
| Running but `0/1 Ready` | Readiness probe failing (DB down) | `k exec <pod> -- wget -qO- localhost:8080/actuator/health/readiness`; fix DB |
| Probe fails during slow start | Startup too slow | Increase `startupProbe.failureThreshold` |
| App can't reach MySQL | Service/DNS/credentials | `k get svc mysql`; `k exec <pod> -- getent hosts mysql`; check `MYSQL_USER` matches `DB_USERNAME` (MySQL only reads user vars on first init of an empty volume → delete PVC to reinitialise) |
| HTTP 503 from POST | Upstream SOAP down or circuit breaker OPEN | `k logs … | grep "SOAP call failed"`; check metric `resilience4j_circuitbreaker_state`; test `curl` the WSDL URL from a pod; check egress/NetworkPolicy/proxy; breaker auto-recovers after 30s |
| HTTP 404 on POST | Country name not recognised by the SOAP service | Use the official name (e.g. "Kenya"); verify in SoapUI |
| HTTP 409 | Duplicate ISO code race | Retry – the second call returns the stored record |
| HPA shows `<unknown>` | metrics-server missing | `minikube addons enable metrics-server` |
| Ingress 404/502 | No ingress controller, wrong host, pods not Ready | `k describe ingress`; check controller pods; `/etc/hosts` entry |
| Rollout stuck | New pods failing readiness | `k rollout status deploy/country-info-service`; `k rollout undo deploy/country-info-service` |

## Debug tools
```bash
k port-forward svc/country-info-service 8080:80
curl localhost:8080/actuator/health | jq
curl localhost:8080/actuator/prometheus | grep -E 'resilience4j|http_server_requests|hikaricp'
k exec -it mysql-0 -- mysql -uroot -p countrydb -e 'select id,iso_code,name from country_info;'
k debug -it <pod> --image=busybox --target=app      # ephemeral debug container
```
## Tracing a single request
Every response carries `X-Correlation-Id` (also in the error body). Search logs for it:
`k logs -l app=country-info-service --tail=-1 | grep <id>`.
