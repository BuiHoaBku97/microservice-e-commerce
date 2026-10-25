# Config Server operations

## Repository

The configuration repository is a separate private GitHub repository:

```text
ecommerce-config/
├── application.yml
├── application-local.yml
├── application-docker.yml
├── gateway.yml
├── identity-service.yml
├── catalog-service.yml
├── order-service.yml
├── payment-service.yml
└── notification-service.yml
```

The application repository contains the Config Server code only. It must not
contain the GitHub token or runtime secrets.

## Local environment

Copy the following variables to the local `.env` and replace the placeholders:

```env
CONFIG_GIT_URI=https://github.com/<owner>/ecommerce-config.git
CONFIG_GIT_USERNAME=<github-username>
CONFIG_GIT_PASSWORD=<github-fine-grained-token>
CONFIG_GIT_LABEL=main
CONFIG_GIT_CLONE_ON_START=true
CONFIG_GIT_REFRESH_RATE=0
CONFIG_GIT_FORCE_PULL=true
CONFIG_WEBHOOK_SECRET=<github-webhook-secret>
```

The token needs repository read access. Never commit `.env` or the token.
`CONFIG_WEBHOOK_SECRET` is used to validate GitHub's `X-Hub-Signature-256`
header. Leave it empty only for a local, non-public Config Server.

## Startup and checks

```powershell
docker compose config --quiet
docker compose up -d --build --wait
docker compose ps

Invoke-RestMethod http://localhost:8888/actuator/health
Invoke-RestMethod http://localhost:8888/order-service/docker
Invoke-RestMethod http://localhost:8080/actuator/health
```

The expected startup order is:

```text
Kafka → Config Server → Eureka → business services → Gateway
```

## Config update flow

1. Change a YAML file in `ecommerce-config`.
2. Validate YAML and commit to `main`.
3. Push to GitHub.
4. GitHub sends a webhook to Config Server `/monitor`.
5. Config Server publishes the refresh event through Kafka topic `springCloudBus`.
6. Config clients receive the event and refresh eligible beans.

The Config Server checks the Git backend on each configuration request when
`CONFIG_GIT_REFRESH_RATE=0`. A running service still needs a refresh event or
restart; changing Git alone does not mutate already-created Spring beans.

For local development, GitHub cannot call `localhost` directly. Use a secure
tunnel or invoke `/monitor` manually from a controlled environment.

## Rollback

```powershell
git log --oneline
git revert <bad-commit>
git push origin main
```

After the rollback event, verify the affected endpoint and service health
before restarting any service.
