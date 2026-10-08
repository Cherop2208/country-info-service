# Deploying on Kubernetes

## Prerequisites
Docker, `kubectl`, a cluster (minikube / kind / Docker Desktop / cloud), an ingress controller (optional), metrics-server (needed for the HPA).

## 1. Local cluster (fastest)
```bash
minikube start
minikube addons enable ingress metrics-server
LOAD=minikube ./scripts/deploy.sh 1.0.0      # kind: LOAD=kind
```
## 2. Real cluster with a registry
```bash
REGISTRY=myregistry.io/team ./scripts/deploy.sh 1.0.0
```
(Ensure the cluster can pull the image; add an `imagePullSecret` for private registries.)

## 3. What gets created (namespace `country-info`)
ConfigMap (`DB_URL`, `SOAP_URL`, timeouts), Secret (DB creds – **change them**), MySQL StatefulSet + 5Gi PVC, app Deployment (3 replicas, rolling update, probes, resource limits, non-root), Service, HPA (3–10 @ 70% CPU), PodDisruptionBudget, Ingress.

## 4. Verify
```bash
kubectl -n country-info get pods,svc,hpa,ingress
kubectl -n country-info port-forward svc/country-info-service 8080:80 &
./scripts/smoke-test.sh http://localhost:8080
```
Ingress: add `$(minikube ip) country-info.local` to `/etc/hosts`, then `curl http://country-info.local/actuator/health`.

## 5. Operate
```bash
kubectl -n country-info scale deploy/country-info-service --replicas=5
./scripts/deploy.sh 1.0.1                                  # rolling upgrade
kubectl -n country-info rollout undo deploy/country-info-service   # rollback
./scripts/undeploy.sh                                      # tear down (PVC remains until namespace deleted)
```
## 6. Production hardening checklist
Managed MySQL (set `DB_URL`), real secret management, Flyway, TLS on ingress, NetworkPolicies, Prometheus/Grafana + alerts, pinned image digests, CI/CD pipeline.
