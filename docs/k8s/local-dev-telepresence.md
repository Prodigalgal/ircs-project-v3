# Local development with Telepresence

Telepresence is installed locally at:

```text
D:\DevlopTools\telepresence\telepresence.exe
```

The cluster-side traffic manager is installed in:

```text
namespace: ircs-dev
release: traffic-manager
managed namespaces: ircs-dev
```

## Safety rules

Use `ircs-dev` only for local development.

Do not create public HTTPRoutes in `ircs-dev`. The namespace is labeled
`gateway-access=none`, and the environment baseline expects no public route from
this namespace.

Do not intercept services in `ircs-system`. Use a dev copy of the target service
inside `ircs-dev`, then intercept that dev service only.

Do not point dev workers at `ircs-system` RabbitMQ, PostgreSQL, Valkey, or
Elasticsearch. Use the V1 key names recorded in
`docs/k8s/v1-config-environment-map.md`, but rewrite internal service hosts to
`*.ircs-dev.svc.cluster.local`.

Disable external side effects by default. For notification mail, the dev
manifest sets `APP_MAIL_ENABLED=false`.

## Connect

```powershell
$env:Path = "D:\DevlopTools\telepresence;" + $env:Path
telepresence connect --manager-namespace ircs-dev --namespace ircs-dev
telepresence status
```

## Disconnect

```powershell
telepresence quit --stop-daemons
telepresence status
```

## Example workflow

1. Deploy namespace-local dependencies and a dev Service/Deployment in `ircs-dev`.
2. Start the matching Spring Boot service locally.
3. Intercept only the dev Service:

```powershell
telepresence intercept ircs-notification-worker --namespace ircs-dev --port 8080:8080
```

4. Stop the intercept after testing:

```powershell
telepresence leave ircs-notification-worker
telepresence quit --stop-daemons
```
