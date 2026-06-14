param(
    [string]$Namespace = "ircs-dev",
    [switch]$Execute
)

$ErrorActionPreference = "Stop"
$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
$FrontendManifest = Join-Path $RepoRoot "deploy\k8s\dev\frontend-portal-admin-dev.yaml"
$MonitoringManifest = Join-Path $RepoRoot "deploy\k8s\dev\observability-monitoring-dev.yaml"

function ConvertTo-CompressedJson {
    param([object]$Value)
    return ($Value | ConvertTo-Json -Depth 30 -Compress)
}

function Invoke-Kubectl {
    param(
        [string]$Description,
        [string[]]$Arguments
    )

    Write-Host ""
    Write-Host "==> $Description"
    Write-Host ("kubectl " + ($Arguments -join " "))
    if ($Execute) {
        & kubectl @Arguments
        if ($LASTEXITCODE -ne 0) {
            throw "kubectl failed for $Description"
        }
    }
}

function Invoke-KubectlPatch {
    param(
        [string]$Description,
        [string[]]$BaseArguments,
        [hashtable]$Patch
    )

    $patchJson = ConvertTo-CompressedJson $Patch
    Write-Host ""
    Write-Host "==> $Description"
    Write-Host ("kubectl " + ($BaseArguments -join " ") + " --patch-file <generated>")
    Write-Host $patchJson
    if ($Execute) {
        $patchFile = [System.IO.Path]::GetTempFileName()
        try {
            [System.IO.File]::WriteAllText(
                $patchFile,
                $patchJson,
                [System.Text.UTF8Encoding]::new($false))
            & kubectl @BaseArguments "--patch-file" $patchFile
            if ($LASTEXITCODE -ne 0) {
                throw "kubectl patch failed for $Description"
            }
        } finally {
            if (Test-Path $patchFile) {
                Remove-Item -LiteralPath $patchFile -Force
            }
        }
    }
}

function New-DeleteEntry {
    param([string]$Name)
    return @{
        name = $Name
        '$patch' = "delete"
    }
}

function New-DeleteVolumeMountEntry {
    param(
        [string]$Name,
        [string]$MountPath
    )
    return @{
        name = $Name
        mountPath = $MountPath
        '$patch' = "delete"
    }
}

function New-EnvEntry {
    param(
        [string]$Name,
        [string]$Value
    )
    return @{
        name = $Name
        value = $Value
    }
}

function Patch-AppDeployment {
    param(
        [string]$Deployment,
        [hashtable]$HttpEnv = @{}
    )

    $removeEnvNames = @(
        "MTLS_KEYSTORE_PASSWORD",
        "JAVA_TOOL_OPTIONS",
        "SERVER_SSL_ENABLED",
        "SERVER_SSL_KEY_STORE",
        "SERVER_SSL_KEY_STORE_PASSWORD",
        "SERVER_SSL_KEY_STORE_TYPE",
        "SERVER_SSL_TRUST_STORE",
        "SERVER_SSL_TRUST_STORE_PASSWORD",
        "SERVER_SSL_TRUST_STORE_TYPE",
        "SERVER_SSL_CLIENT_AUTH"
    )

    $envPatch = @()
    foreach ($name in $removeEnvNames) {
        $envPatch += New-DeleteEntry -Name $name
    }
    foreach ($item in $HttpEnv.GetEnumerator()) {
        $envPatch += New-EnvEntry -Name $item.Key -Value $item.Value
    }

    Invoke-KubectlPatch `
        -Description "Remove manual mTLS from deployment/$Deployment" `
        -BaseArguments @(
            "-n", $Namespace,
            "patch", "deployment", $Deployment,
            "--type", "strategic"
        ) `
        -Patch @{
            spec = @{
                template = @{
                    metadata = @{
                        annotations = @{
                            "ircs.prodigalgal.com/manual-mtls" = "removed"
                        }
                    }
                    spec = @{
                        containers = @(
                            @{
                                name = "app"
                                env = $envPatch
                                volumeMounts = @(
                                    New-DeleteVolumeMountEntry -Name "ircs-mtls" -MountPath "/etc/ircs/mtls"
                                )
                            }
                        )
                        volumes = @(
                            New-DeleteEntry -Name "ircs-mtls"
                        )
                    }
                }
            }
        }
}

function Patch-GatewayDeployment {
    Invoke-KubectlPatch `
        -Description "Remove manual mTLS volume from deployment/ircs-frontend-gateway" `
        -BaseArguments @(
            "-n", $Namespace,
            "patch", "deployment", "ircs-frontend-gateway",
            "--type", "strategic"
        ) `
        -Patch @{
            spec = @{
                template = @{
                    metadata = @{
                        annotations = @{
                            "ircs.prodigalgal.com/manual-mtls" = "removed"
                        }
                    }
                    spec = @{
                        containers = @(
                            @{
                                name = "nginx"
                                volumeMounts = @(
                                    New-DeleteVolumeMountEntry -Name "frontend-gateway-mtls" -MountPath "/etc/nginx/mtls"
                                )
                            }
                        )
                        volumes = @(
                            New-DeleteEntry -Name "frontend-gateway-mtls"
                        )
                    }
                }
            }
        }
}

$http = @{
    AdminBff = "http://ircs-admin-bff.$Namespace.svc.cluster.local:8080"
    Catalog = "http://ircs-catalog-service.$Namespace.svc.cluster.local:8080"
    Config = "http://ircs-config-service.$Namespace.svc.cluster.local:8080"
    Content = "http://ircs-content-service.$Namespace.svc.cluster.local:8080"
    Credential = "http://ircs-credential-service.$Namespace.svc.cluster.local:8080"
    Identity = "http://ircs-identity-service.$Namespace.svc.cluster.local:8080"
    Interaction = "http://ircs-interaction-service.$Namespace.svc.cluster.local:8080"
    Magnet = "http://ircs-magnet-service.$Namespace.svc.cluster.local:8080"
    Normalization = "http://ircs-normalization-worker.$Namespace.svc.cluster.local:8080"
    Ops = "http://ircs-ops-service.$Namespace.svc.cluster.local:8080"
    Portal = "http://ircs-portal-service.$Namespace.svc.cluster.local:8080"
    Scraper = "http://ircs-scraper-service.$Namespace.svc.cluster.local:8080"
    Search = "http://ircs-search-service.$Namespace.svc.cluster.local:8080"
    Storage = "http://ircs-storage-service.$Namespace.svc.cluster.local:8080"
    Task = "http://ircs-task-service.$Namespace.svc.cluster.local:8080"
}

Write-Host "IRCS dev manual mTLS removal"
Write-Host "Namespace: $Namespace"
Write-Host "Mode: $(if ($Execute) { 'EXECUTE' } else { 'DRY-RUN' })"

Invoke-Kubectl -Description "Apply frontend gateway HTTP manifest" -Arguments @(
    "apply", "-n", $Namespace, "-f", $FrontendManifest
)

Invoke-Kubectl -Description "Apply Prometheus HTTP monitoring manifest" -Arguments @(
    "apply", "-n", $Namespace, "-f", $MonitoringManifest
)

Patch-GatewayDeployment

$serverDeployments = @(
    "ircs-admin-bff",
    "ircs-catalog-service",
    "ircs-config-service",
    "ircs-content-service",
    "ircs-credential-service",
    "ircs-identity-service",
    "ircs-interaction-service",
    "ircs-magnet-service",
    "ircs-normalization-worker",
    "ircs-ops-service",
    "ircs-portal-bff",
    "ircs-portal-service",
    "ircs-scraper-service",
    "ircs-search-service",
    "ircs-storage-service",
    "ircs-task-service"
)

foreach ($deployment in $serverDeployments) {
    Patch-AppDeployment -Deployment $deployment
}

Patch-AppDeployment -Deployment "ircs-metadata-worker" -HttpEnv @{
    APP_METADATA_CREDENTIAL_SERVICE_BASE_URL = $http.Credential
}
Patch-AppDeployment -Deployment "ircs-notification-worker" -HttpEnv @{
    APP_MAIL_CREDENTIAL_SERVICE_BASE_URL = $http.Credential
}
Patch-AppDeployment -Deployment "ircs-aggregation-worker" -HttpEnv @{
    APP_AGGREGATION_CONTEXT_SEARCH_BASE_URL = $http.Search
}

Patch-AppDeployment -Deployment "ircs-admin-bff" -HttpEnv @{
    APP_BFF_IDENTITY_BASE_URL = $http.Identity
    APP_BFF_CONTENT_BASE_URL = $http.Content
    APP_BFF_CATALOG_BASE_URL = $http.Catalog
    APP_BFF_TASK_BASE_URL = $http.Task
    APP_BFF_OPS_BASE_URL = $http.Ops
    APP_BFF_STORAGE_BASE_URL = $http.Storage
    APP_BFF_CONFIG_BASE_URL = $http.Config
    APP_BFF_CREDENTIAL_BASE_URL = $http.Credential
    APP_BFF_MAGNET_BASE_URL = $http.Magnet
    APP_BFF_SCRAPER_BASE_URL = $http.Scraper
    APP_BFF_INTERACTION_BASE_URL = $http.Interaction
}
Patch-AppDeployment -Deployment "ircs-portal-bff" -HttpEnv @{
    APP_BFF_IDENTITY_BASE_URL = $http.Identity
    APP_BFF_INTERACTION_BASE_URL = $http.Interaction
    APP_BFF_PORTAL_BASE_URL = $http.Portal
    APP_BFF_SEARCH_BASE_URL = $http.Search
    APP_BFF_STORAGE_BASE_URL = $http.Storage
}
Patch-AppDeployment -Deployment "ircs-ops-service" -HttpEnv @{
    APP_OPS_SEARCH_SERVICE_BASE_URL = $http.Search
    APP_OPS_NORMALIZATION_WORKER_BASE_URL = $http.Normalization
}
Patch-AppDeployment -Deployment "ircs-content-service" -HttpEnv @{
    APP_CONTENT_SCRAPER_BASE_URL = $http.Scraper
}
Patch-AppDeployment -Deployment "ircs-task-service" -HttpEnv @{
    APP_TASK_RUNNER_SCRAPER_BASE_URL = $http.Scraper
}
Patch-AppDeployment -Deployment "ircs-identity-service" -HttpEnv @{
    APP_IDENTITY_STORAGE_SERVICE_BASE_URL = $http.Storage
}
Patch-AppDeployment -Deployment "ircs-normalization-worker" -HttpEnv @{
    APP_NORMALIZATION_LLM_CREDENTIAL_SERVICE_BASE_URL = $http.Credential
}

$mtlsServiceMonitors = @(
    "ircs-dev-admin-bff-mtls-service-monitor",
    "ircs-dev-catalog-mtls-service-monitor",
    "ircs-dev-config-mtls-service-monitor",
    "ircs-dev-content-mtls-service-monitor",
    "ircs-dev-credential-mtls-service-monitor",
    "ircs-dev-identity-mtls-service-monitor",
    "ircs-dev-interaction-mtls-service-monitor",
    "ircs-dev-magnet-mtls-service-monitor",
    "ircs-dev-normalization-mtls-service-monitor",
    "ircs-dev-ops-mtls-service-monitor",
    "ircs-dev-portal-bff-mtls-service-monitor",
    "ircs-dev-portal-service-mtls-service-monitor",
    "ircs-dev-scraper-mtls-service-monitor",
    "ircs-dev-search-mtls-service-monitor",
    "ircs-dev-storage-mtls-service-monitor",
    "ircs-dev-task-mtls-service-monitor"
)
Invoke-Kubectl -Description "Delete old mTLS ServiceMonitors" -Arguments (@(
    "-n", $Namespace,
    "delete", "servicemonitor"
) + $mtlsServiceMonitors + @("--ignore-not-found"))

$mtlsCertificates = @(
    "ircs-dev-root-ca",
    "ircs-catalog-service-mtls",
    "ircs-config-service-mtls",
    "ircs-content-service-mtls",
    "ircs-credential-service-mtls",
    "ircs-identity-service-mtls",
    "ircs-interaction-service-mtls",
    "ircs-magnet-service-mtls",
    "ircs-portal-service-mtls",
    "ircs-scraper-service-mtls",
    "ircs-search-service-mtls",
    "ircs-storage-service-mtls",
    "ircs-task-service-mtls",
    "ircs-metadata-worker-mtls",
    "ircs-notification-worker-mtls",
    "ircs-normalization-worker-mtls",
    "ircs-ops-service-mtls",
    "ircs-admin-bff-mtls",
    "ircs-portal-bff-mtls",
    "ircs-frontend-gateway-mtls",
    "ircs-prometheus-scraper-mtls",
    "ircs-aggregation-worker-mtls"
)
Invoke-Kubectl -Description "Delete manual mTLS Certificates" -Arguments (@(
    "-n", $Namespace,
    "delete", "certificate"
) + $mtlsCertificates + @("--ignore-not-found"))

$mtlsSecrets = @(
    "ircs-dev-mtls-keystore-password",
    "ircs-dev-mtls-ca",
    "ircs-catalog-service-mtls",
    "ircs-config-service-mtls",
    "ircs-content-service-mtls",
    "ircs-credential-service-mtls",
    "ircs-identity-service-mtls",
    "ircs-interaction-service-mtls",
    "ircs-magnet-service-mtls",
    "ircs-portal-service-mtls",
    "ircs-scraper-service-mtls",
    "ircs-search-service-mtls",
    "ircs-storage-service-mtls",
    "ircs-task-service-mtls",
    "ircs-metadata-worker-mtls",
    "ircs-notification-worker-mtls",
    "ircs-normalization-worker-mtls",
    "ircs-ops-service-mtls",
    "ircs-admin-bff-mtls",
    "ircs-portal-bff-mtls",
    "ircs-frontend-gateway-mtls",
    "ircs-prometheus-scraper-mtls",
    "ircs-aggregation-worker-mtls"
)
Invoke-Kubectl -Description "Delete manual mTLS Secrets" -Arguments (@(
    "-n", $Namespace,
    "delete", "secret"
) + $mtlsSecrets + @("--ignore-not-found"))

Invoke-Kubectl -Description "Delete manual mTLS Issuers" -Arguments @(
    "-n", $Namespace,
    "delete", "issuer",
    "ircs-dev-selfsigned-issuer",
    "ircs-dev-ca-issuer",
    "--ignore-not-found"
)

$rolloutDeployments = @(
    "ircs-frontend-gateway",
    "ircs-admin-bff",
    "ircs-catalog-service",
    "ircs-config-service",
    "ircs-content-service",
    "ircs-credential-service",
    "ircs-identity-service",
    "ircs-interaction-service",
    "ircs-magnet-service",
    "ircs-normalization-worker",
    "ircs-ops-service",
    "ircs-portal-bff",
    "ircs-portal-service",
    "ircs-scraper-service",
    "ircs-search-service",
    "ircs-storage-service",
    "ircs-task-service",
    "ircs-metadata-worker",
    "ircs-notification-worker",
    "ircs-aggregation-worker"
)

if ($Execute) {
    foreach ($deployment in $rolloutDeployments) {
        Invoke-Kubectl -Description "Wait rollout deployment/$deployment" -Arguments @(
            "-n", $Namespace,
            "rollout", "status", "deployment/$deployment",
            "--timeout=180s"
        )
    }
} else {
    Write-Host ""
    Write-Host "Dry-run only. Re-run with -Execute to remove manual mTLS from live ircs-dev."
}
