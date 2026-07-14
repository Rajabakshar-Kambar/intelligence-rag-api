#!/usr/bin/env bash
#
# deploy.sh <image-tag>
#
# Run on the app EC2 instance (via SSM send-command from CI, or manually via
# SSM Session Manager). Resolves secrets from SSM Parameter Store into a
# runtime .env file (never committed, mode 600) and (re)starts the app
# container via docker compose. Relies on the instance's IAM role — no AWS
# keys anywhere in this script.
#
# Usage: ./deploy.sh <git-sha-or-image-tag>

set -euo pipefail

IMAGE_TAG="${1:?Usage: deploy.sh <image-tag>}"
AWS_REGION="${AWS_REGION:-ap-south-1}"
ECR_REGISTRY="${ECR_REGISTRY:?Set ECR_REGISTRY, e.g. 123456789012.dkr.ecr.ap-south-1.amazonaws.com}"
ECR_REPO="cloudspring-intelligence-rag-api"
APP_DIR="/opt/rag-api"

mkdir -p "$APP_DIR"
cd "$APP_DIR"

echo "==> Logging in to ECR"
aws ecr get-login-password --region "$AWS_REGION" \
  | docker login --username AWS --password-stdin "$ECR_REGISTRY"

echo "==> Resolving secrets from SSM Parameter Store"
QDRANT_API_KEY=$(aws ssm get-parameter \
  --name /cloudspring/rag-api/QDRANT_API_KEY \
  --with-decryption --query 'Parameter.Value' --output text \
  --region "$AWS_REGION")

API_KEY=$(aws ssm get-parameter \
  --name /cloudspring/rag-api/API_KEY \
  --with-decryption --query 'Parameter.Value' --output text \
  --region "$AWS_REGION")

QDRANT_HOST=$(aws ssm get-parameter \
  --name /cloudspring/rag-api/QDRANT_HOST \
  --query 'Parameter.Value' --output text \
  --region "$AWS_REGION")

# Written fresh every deploy; mode 600 so only root/ssm-user with sudo can read it.
umask 077
cat > "$APP_DIR/.env" << EOF
QDRANT_API_KEY=${QDRANT_API_KEY}
API_KEY=${API_KEY}
QDRANT_HOST=${QDRANT_HOST}
EOF
chmod 600 "$APP_DIR/.env"

echo "==> Pulling image ${ECR_REGISTRY}/${ECR_REPO}:${IMAGE_TAG}"
export ECR_IMAGE="${ECR_REGISTRY}/${ECR_REPO}:${IMAGE_TAG}"
export AWS_REGION

docker compose -f docker-compose.prod.yml pull
docker compose -f docker-compose.prod.yml up -d --remove-orphans

echo "==> Waiting for health check"
for i in $(seq 1 12); do
  if curl -sf http://127.0.0.1:8080/actuator/health/liveness > /dev/null; then
    echo "==> Healthy. Deployed ${IMAGE_TAG}."
    exit 0
  fi
  sleep 5
done

echo "!! Health check did not pass within 60s. Check: docker logs rag-api"
exit 1
