param(
    [string]$SourceNamespace = "ircs-system",
    [string]$TargetNamespace = "ircs-dev",
    [string]$SecretName = "harbor-secret"
)

$ErrorActionPreference = "Stop"

$source = kubectl get secret $SecretName -n $SourceNamespace -o json | ConvertFrom-Json

if ($source.type -ne "kubernetes.io/dockerconfigjson") {
    throw "$SourceNamespace/$SecretName is not a dockerconfigjson secret"
}

$dockerConfig = $source.data.".dockerconfigjson"
if (-not $dockerConfig) {
    throw ".dockerconfigjson not found in $SourceNamespace/$SecretName"
}

$manifest = @"
apiVersion: v1
kind: Secret
metadata:
  name: $SecretName
  namespace: $TargetNamespace
  labels:
    app.kubernetes.io/part-of: ircs
    environment: dev
type: kubernetes.io/dockerconfigjson
data:
  .dockerconfigjson: $dockerConfig
"@

$manifest | kubectl apply -f -
kubectl get secret $SecretName -n $TargetNamespace
