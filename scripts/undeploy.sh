#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
kubectl kustomize k8s | kubectl delete --ignore-not-found -f -
