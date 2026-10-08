#!/usr/bin/env bash
# Usage: ./scripts/deploy.sh [tag]
# Env:   REGISTRY=myregistry.io/team  (push image there)   LOAD=kind|minikube  (load image into local cluster)
set -euo pipefail
cd "$(dirname "$0")/.."

TAG="${1:-1.0.0}"
IMAGE="country-info-service:${TAG}"
[[ -n "${REGISTRY:-}" ]] && IMAGE="${REGISTRY}/country-info-service:${TAG}"
NS=country-info

echo ">> Building image ${IMAGE}"
docker build -t "${IMAGE}" .

if [[ -n "${REGISTRY:-}" ]]; then
  echo ">> Pushing ${IMAGE}"; docker push "${IMAGE}"
elif [[ "${LOAD:-}" == "kind" ]]; then
  kind load docker-image "${IMAGE}"
elif [[ "${LOAD:-}" == "minikube" ]]; then
  minikube image load "${IMAGE}"
fi

echo ">> Applying manifests"
kubectl kustomize k8s | sed "s#image: country-info-service:1.0.0#image: ${IMAGE}#" | kubectl apply -f -

echo ">> Waiting for MySQL"
kubectl -n ${NS} rollout status statefulset/mysql --timeout=180s
echo ">> Waiting for app"
kubectl -n ${NS} rollout status deployment/country-info-service --timeout=240s

kubectl -n ${NS} get pods,svc,hpa,ingress
echo ">> Done. Try: kubectl -n ${NS} port-forward svc/country-info-service 8080:80"
