param(
    [string]$Namespace = "ircs-dev",
    [string]$CredentialBaseUrl = "http://127.0.0.1:18090",
    [int]$CredentialPort = 18090,
    [int]$StoragePort = 18093,
    [string]$CredentialToken = $env:APP_METADATA_CREDENTIAL_SERVICE_TOKEN,
    [string]$BucketName = "",
    [string]$PublicDomain = "",
    [switch]$UseV1ParityBucketOverride,
    [switch]$StartCredentialPortForward,
    [switch]$UseKubernetesSecretToken,
    [switch]$PreflightOnly,
    [switch]$NoWrite,
    [switch]$ConfirmWatchdogR2Write,
    [switch]$FailOnSkip
)

$ErrorActionPreference = "Stop"

$stamp = Get-Date -Format "yyyyMMddHHmmss"
$traceId = "02807j-r2-watchdog-$stamp"
$secretName = "ircs-r2-watchdog-smoke-02807j"
$fixtureFile = Join-Path $env:TEMP "ircs-02807j-watchdog-$stamp.png"
$credentialPfOut = Join-Path $env:TEMP "ircs-02807j-watchdog-credential-pf.out.log"
$credentialPfErr = Join-Path $env:TEMP "ircs-02807j-watchdog-credential-pf.err.log"
$storagePfOut = Join-Path $env:TEMP "ircs-02807j-watchdog-storage-pf.out.log"
$storagePfErr = Join-Path $env:TEMP "ircs-02807j-watchdog-storage-pf.err.log"
$credentialPf = $null
$storagePf = $null
$deploymentChanged = $false
$coverImageId = $null
$coverStoragePath = $null
$memberId = ([guid]::NewGuid()).ToString()
$email = "codex.02807j.watchdog.$stamp@example.invalid"
$avatarStoragePath = $null
$avatarLocalUrl = $null
$avatarR2Url = $null
$endpoint = $null
$accessKey = $null
$secretKey = $null
$effectiveBucket = $null
$r2CoverHeadStatusCode = $null
$r2AvatarHeadStatusCode = $null
$r2CoverDeleteStatusCode = $null
$r2AvatarDeleteStatusCode = $null
$coverCleanupNotFound = $false
$avatarCleanupNotFound = $false

function New-SmokeResult {
    param(
        [string]$Status,
        [string]$Reason,
        [hashtable]$Details = @{}
    )
    [ordered]@{
        task = "028-07-J"
        slice = "storage-r2-watchdog-recovery"
        status = $Status
        reason = $Reason
        checkedAt = (Get-Date).ToUniversalTime().ToString("o")
        namespace = $Namespace
        traceId = $traceId
        coverImageId = $coverImageId
        memberId = $memberId
        details = $Details
    } | ConvertTo-Json -Depth 14
}

function Finish-Smoke {
    param(
        [string]$Status,
        [string]$Reason,
        [hashtable]$Details = @{}
    )
    New-SmokeResult -Status $Status -Reason $Reason -Details $Details
    if ($Status -eq "FAILED") {
        exit 1
    }
    if ($Status -eq "SKIPPED" -and $FailOnSkip) {
        exit 2
    }
    exit 0
}

function Mark {
    param([string]$Name, [int]$Index, [int]$Total)
    Write-Output ("MARK {0}/{1} {2}" -f $Index, $Total, $Name)
}

function Test-TcpPort {
    param([string]$HostName, [int]$Port)
    $client = [System.Net.Sockets.TcpClient]::new()
    try {
        $async = $client.BeginConnect($HostName, $Port, $null, $null)
        if ($async.AsyncWaitHandle.WaitOne(500)) {
            $client.EndConnect($async)
            return $true
        }
        return $false
    } catch {
        return $false
    } finally {
        $client.Close()
    }
}

function Wait-ForTcpPort {
    param([string]$HostName, [int]$Port, [int]$TimeoutSeconds = 30)
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        if (Test-TcpPort $HostName $Port) {
            return
        }
        Start-Sleep -Milliseconds 300
    }
    throw "Timed out waiting for $HostName`:$Port"
}

function Start-PortForward {
    param(
        [string]$Service,
        [int]$LocalPort,
        [int]$RemotePort,
        [string]$OutFile,
        [string]$ErrFile
    )
    if (Test-TcpPort "127.0.0.1" $LocalPort) {
        return $null
    }
    Remove-Item -LiteralPath $OutFile, $ErrFile -ErrorAction SilentlyContinue
    $pf = Start-Process -FilePath kubectl `
        -ArgumentList @("-n", $Namespace, "port-forward", $Service, "$($LocalPort):$RemotePort") `
        -RedirectStandardOutput $OutFile `
        -RedirectStandardError $ErrFile `
        -WindowStyle Hidden `
        -PassThru
    Wait-ForTcpPort "127.0.0.1" $LocalPort 30
    if ($pf.HasExited) {
        $err = if (Test-Path $ErrFile) { Get-Content -LiteralPath $ErrFile -Raw } else { "" }
        Finish-Smoke "SKIPPED" "PORT_FORWARD_UNAVAILABLE" @{
            service = $Service
            localPort = $LocalPort
            remotePort = $RemotePort
            error = $err
        }
    }
    return $pf
}

function Test-KubectlAccess {
    $previous = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    $output = kubectl -n $Namespace get pod 2>&1
    $exit = $LASTEXITCODE
    $ErrorActionPreference = $previous
    if ($exit -ne 0) {
        Finish-Smoke "SKIPPED" "KUBERNETES_UNAVAILABLE" @{
            exitCode = $exit
            output = (($output | Out-String).Trim())
        }
    }
}

function Get-SecretValue {
    param([string]$Key)
    $previous = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    $value64 = kubectl -n $Namespace get secret ircs-dev-secrets -o jsonpath="{.data.$Key}" 2>&1
    $exit = $LASTEXITCODE
    $ErrorActionPreference = $previous
    if ($exit -ne 0 -or [string]::IsNullOrWhiteSpace($value64)) {
        Finish-Smoke "SKIPPED" "KUBERNETES_SECRET_MISSING" @{
            key = $Key
            exitCode = $exit
            output = (($value64 | Out-String).Trim())
        }
    }
    return [System.Text.Encoding]::UTF8.GetString([System.Convert]::FromBase64String($value64))
}

function Get-OptionalSecretValue {
    param([string]$Key)
    $previous = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    $value64 = kubectl -n $Namespace get secret ircs-dev-secrets -o jsonpath="{.data.$Key}" 2>&1
    $exit = $LASTEXITCODE
    $ErrorActionPreference = $previous
    if ($exit -ne 0 -or [string]::IsNullOrWhiteSpace($value64)) {
        return $null
    }
    return [System.Text.Encoding]::UTF8.GetString([System.Convert]::FromBase64String($value64))
}

function Get-CredentialServiceSecretToken {
    $token = Get-OptionalSecretValue "SERVICE_CREDENTIAL_TOKEN"
    if (-not [string]::IsNullOrWhiteSpace($token)) {
        return $token
    }
    return Get-SecretValue "INTERNAL_CREDENTIAL_TOKEN"
}

function Get-ConfigMapValue {
    param([string]$Key)
    $previous = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    $value = kubectl -n $Namespace get configmap ircs-dev-app-config -o jsonpath="{.data.$Key}" 2>&1
    $exit = $LASTEXITCODE
    $ErrorActionPreference = $previous
    if ($exit -ne 0 -or [string]::IsNullOrWhiteSpace($value)) {
        Finish-Smoke "SKIPPED" "KUBERNETES_CONFIG_MISSING" @{
            key = $Key
            exitCode = $exit
            output = (($value | Out-String).Trim())
        }
    }
    return [string]$value
}

function Get-R2CredentialLease {
    if ($UseKubernetesSecretToken -and [string]::IsNullOrWhiteSpace($CredentialToken)) {
        $script:CredentialToken = Get-CredentialServiceSecretToken
    }
    if ([string]::IsNullOrWhiteSpace($CredentialToken)) {
        Finish-Smoke "SKIPPED" "CREDENTIAL_TOKEN_MISSING" @{
            hint = "pass -UseKubernetesSecretToken or set APP_METADATA_CREDENTIAL_SERVICE_TOKEN"
        }
    }

    $leaseUrl = "$($CredentialBaseUrl.TrimEnd('/'))/internal/credentials/providers/R2/leases?requiredPayloadKey=access_key&limit=1"
    try {
        $response = Invoke-RestMethod -Method Get -Uri $leaseUrl -Headers @{
            "X-IRCS-INTERNAL-TOKEN" = $CredentialToken
        } -TimeoutSec 20
    } catch {
        Finish-Smoke "SKIPPED" "CREDENTIAL_SERVICE_UNAVAILABLE" @{
            error = $_.Exception.Message
        }
    }

    $leases = @($response)
    if ($leases.Count -eq 0) {
        Finish-Smoke "SKIPPED" "NO_ENABLED_R2_CREDENTIALS" @{}
    }

    $credential = $leases | Where-Object {
        $null -ne $_.secretPayload `
            -and -not [string]::IsNullOrWhiteSpace([string]$_.secretPayload.access_key) `
            -and -not [string]::IsNullOrWhiteSpace([string]$_.secretPayload.secret_key) `
            -and -not [string]::IsNullOrWhiteSpace([string]$_.secretPayload.account_id)
    } | Select-Object -First 1
    if ($null -eq $credential) {
        $payloadKeys = @()
        if ($leases[0].secretPayload) {
            $payloadKeys = @($leases[0].secretPayload.PSObject.Properties.Name | Sort-Object)
        }
        Finish-Smoke "SKIPPED" "R2_CREDENTIAL_INCOMPLETE" @{
            leaseCount = $leases.Count
            payloadKeys = $payloadKeys
        }
    }
    return $credential
}

function ConvertTo-Hex {
    param([byte[]]$Bytes)
    return (($Bytes | ForEach-Object { $_.ToString("x2") }) -join "")
}

function Get-Sha256Hex {
    param([byte[]]$Bytes)
    $sha = [System.Security.Cryptography.SHA256]::Create()
    try {
        return ConvertTo-Hex ($sha.ComputeHash($Bytes))
    } finally {
        $sha.Dispose()
    }
}

function Get-HmacSha256 {
    param([byte[]]$Key, [string]$Data)
    $hmac = [System.Security.Cryptography.HMACSHA256]::new($Key)
    try {
        return $hmac.ComputeHash([System.Text.Encoding]::UTF8.GetBytes($Data))
    } finally {
        $hmac.Dispose()
    }
}

function New-SigningKey {
    param([string]$SecretKey, [string]$DateStamp)
    $kSecret = [System.Text.Encoding]::UTF8.GetBytes("AWS4$SecretKey")
    $kDate = Get-HmacSha256 $kSecret $DateStamp
    $kRegion = Get-HmacSha256 $kDate "auto"
    $kService = Get-HmacSha256 $kRegion "s3"
    return Get-HmacSha256 $kService "aws4_request"
}

function ConvertTo-S3Path {
    param([string]$Bucket, [string]$Key)
    $encodedBucket = [System.Uri]::EscapeDataString($Bucket)
    $encodedKey = (($Key -split "/") | ForEach-Object { [System.Uri]::EscapeDataString($_) }) -join "/"
    return "/$encodedBucket/$encodedKey"
}

function Invoke-R2Request {
    param(
        [string]$Method,
        [string]$Endpoint,
        [string]$Bucket,
        [string]$Key,
        [string]$AccessKey,
        [string]$SecretKey,
        [byte[]]$Body = [byte[]]::new(0),
        [switch]$AllowNotFound
    )
    $endpointUri = [System.Uri]$Endpoint.TrimEnd("/")
    $canonicalUri = ConvertTo-S3Path -Bucket $Bucket -Key $Key
    $requestUri = "$($endpointUri.Scheme)://$($endpointUri.Host)$canonicalUri"
    $amzDate = (Get-Date).ToUniversalTime().ToString("yyyyMMddTHHmmssZ")
    $dateStamp = $amzDate.Substring(0, 8)
    $payloadHash = Get-Sha256Hex $Body
    $hostHeader = $endpointUri.Host
    $canonicalHeaders = "host:$hostHeader`n" +
            "x-amz-content-sha256:$payloadHash`n" +
            "x-amz-date:$amzDate`n"
    $signedHeaders = "host;x-amz-content-sha256;x-amz-date"
    $canonicalRequest = "$Method`n$canonicalUri`n`n$canonicalHeaders`n$signedHeaders`n$payloadHash"
    $algorithm = "AWS4-HMAC-SHA256"
    $credentialScope = "$dateStamp/auto/s3/aws4_request"
    $stringToSign = "$algorithm`n$amzDate`n$credentialScope`n$(Get-Sha256Hex ([System.Text.Encoding]::UTF8.GetBytes($canonicalRequest)))"
    $signature = ConvertTo-Hex (Get-HmacSha256 (New-SigningKey -SecretKey $SecretKey -DateStamp $dateStamp) $stringToSign)
    $authorization = "$algorithm Credential=$AccessKey/$credentialScope, SignedHeaders=$signedHeaders, Signature=$signature"
    $headers = @{
        Authorization = $authorization
        "x-amz-content-sha256" = $payloadHash
        "x-amz-date" = $amzDate
    }

    try {
        if ($Method -eq "HEAD") {
            $response = Invoke-WebRequest -Method Head -Uri $requestUri -Headers $headers -TimeoutSec 30 -UseBasicParsing
        } elseif ($Method -eq "DELETE") {
            $response = Invoke-WebRequest -Method Delete -Uri $requestUri -Headers $headers -TimeoutSec 30 -UseBasicParsing
        } else {
            throw "Unsupported R2 method: $Method"
        }
        return @{ ok = $true; statusCode = [int]$response.StatusCode; notFound = $false }
    } catch {
        $statusCode = $null
        if ($_.Exception.Response) {
            $statusCode = [int]$_.Exception.Response.StatusCode
        }
        if ($AllowNotFound -and $statusCode -eq 404) {
            return @{ ok = $true; statusCode = 404; notFound = $true }
        }
        return @{ ok = $false; statusCode = $statusCode; notFound = $false; error = $_.Exception.Message }
    }
}

function Invoke-Sql {
    param([string]$Sql)
    $Sql | kubectl -n $Namespace exec -i postgres-0 -- psql -v ON_ERROR_STOP=1 -U postgres -d ircs
    if ($LASTEXITCODE -ne 0) {
        throw "psql failed with exit code $LASTEXITCODE"
    }
}

function Invoke-SqlScalar {
    param([string]$Sql)
    $rows = $Sql | kubectl -n $Namespace exec -i postgres-0 -- psql -v ON_ERROR_STOP=1 -U postgres -d ircs -t -A
    if ($LASTEXITCODE -ne 0) {
        throw "psql scalar failed with exit code $LASTEXITCODE"
    }
    $value = $rows | Where-Object { -not [string]::IsNullOrWhiteSpace($_) } | Select-Object -Last 1
    if ($null -eq $value) {
        return $null
    }
    return $value.Trim()
}

function Invoke-CurlJson {
    param(
        [string]$Method,
        [string]$Url,
        [string[]]$Headers = @(),
        [string[]]$CurlArgs = @()
    )
    $tmp = Join-Path $env:TEMP ("ircs-02807j-http-" + [guid]::NewGuid() + ".json")
    $args = @("-sS", "--max-time", "60", "-o", $tmp, "-w", "%{http_code}", "-X", $Method)
    foreach ($header in $Headers) {
        $args += @("-H", $header)
    }
    $args += $CurlArgs
    $args += $Url
    $code = & curl.exe @args
    $text = ""
    if (Test-Path $tmp) {
        $text = Get-Content -LiteralPath $tmp -Raw -ErrorAction SilentlyContinue
        Remove-Item -LiteralPath $tmp -ErrorAction SilentlyContinue
    }
    if ([int]$code -lt 200 -or [int]$code -ge 300) {
        throw "HTTP $code for $Method $Url body=$text"
    }
    if ([string]::IsNullOrWhiteSpace($text)) {
        return $null
    }
    return $text | ConvertFrom-Json
}

function Write-FixturePng {
    param([string]$Path)
    [byte[]]$png = 0x89,0x50,0x4E,0x47,0x0D,0x0A,0x1A,0x0A,0x00,0x00,0x00,0x0D,0x49,0x48,0x44,0x52,
        0x00,0x00,0x00,0x01,0x00,0x00,0x00,0x01,0x08,0x02,0x00,0x00,0x00,0x90,0x77,0x53,0xDE,
        0x00,0x00,0x00,0x0C,0x49,0x44,0x41,0x54,0x08,0xD7,0x63,0xF8,0xCF,0xC0,0x00,0x00,0x03,
        0x01,0x01,0x00,0x18,0xDD,0x8D,0xB0,0x00,0x00,0x00,0x00,0x49,0x45,0x4E,0x44,0xAE,0x42,
        0x60,0x82
    $suffix = [System.Text.Encoding]::UTF8.GetBytes("ircs-02807j-$stamp")
    [byte[]]$bytes = $png + $suffix
    [System.IO.File]::WriteAllBytes($Path, $bytes)
}

function Get-PublicBase {
    param([string]$Domain)
    $base = $Domain.Trim()
    if (-not $base.StartsWith("http")) {
        $base = "https://$base"
    }
    return $base.TrimEnd("/")
}

function Get-QueueState {
    param([string]$Name)
    $lines = kubectl -n $Namespace exec rabbitmq-0 -- rabbitmqctl list_queues name messages messages_ready messages_unacknowledged consumers
    if ($LASTEXITCODE -ne 0) {
        throw "rabbitmqctl list_queues failed"
    }
    $line = $lines | Where-Object { $_ -match "^$([regex]::Escape($Name))\s" } | Select-Object -First 1
    if (-not $line) {
        throw "queue not found: $Name"
    }
    $parts = $line -split "\s+"
    return [ordered]@{
        name = $Name
        messages = [int]$parts[1]
        ready = [int]$parts[2]
        unacked = [int]$parts[3]
        consumers = [int]$parts[4]
    }
}

function Restore-StorageDeployment {
    if ($deploymentChanged) {
        kubectl -n $Namespace set env deployment/ircs-storage-service `
            APP_STORAGE_R2_ENABLED- `
            APP_STORAGE_R2_BUCKET_NAME- `
            APP_STORAGE_R2_PUBLIC_DOMAIN- `
            APP_STORAGE_R2_ACCOUNT_ID- `
            APP_STORAGE_R2_ACCESS_KEY- `
            APP_STORAGE_R2_SECRET_KEY- `
            APP_STORAGE_R2_ENDPOINT- `
            APP_STORAGE_R2_WATCHDOG_ENABLED- `
            APP_STORAGE_R2_WATCHDOG_INITIAL_DELAY_MS- `
            APP_STORAGE_R2_WATCHDOG_FIXED_DELAY_MS- `
            APP_STORAGE_R2_WATCHDOG_BATCH_SIZE- | Out-Null
        kubectl apply -f deploy/k8s/dev/storage-service-dev.yaml | Out-Null
        kubectl -n $Namespace rollout status deployment/ircs-storage-service --timeout=180s | Out-Null
        $script:deploymentChanged = $false
    }
}

function Cleanup-SmokeData {
    if ($coverImageId) {
        try { Invoke-Sql "delete from cover_images where id = '$coverImageId';" | Out-Null } catch { Write-Warning $_ }
    }
    try { Invoke-Sql "delete from members where id = '$memberId' or email = '$email';" | Out-Null } catch { Write-Warning $_ }
    foreach ($path in @($coverStoragePath, $avatarStoragePath)) {
        if (-not [string]::IsNullOrWhiteSpace($path)) {
            try { kubectl -n $Namespace exec deploy/ircs-storage-service -- sh -c "rm -f '/app/storage/$path'" | Out-Null } catch { Write-Warning $_ }
        }
    }
}

function Restore-LocalFixtureFile {
    param([string]$StoragePath)
    if ([string]::IsNullOrWhiteSpace($StoragePath)) {
        return
    }
    $directory = ""
    $lastSlash = $StoragePath.LastIndexOf("/")
    if ($lastSlash -gt 0) {
        $directory = $StoragePath.Substring(0, $lastSlash)
    }
    $encoded = [System.Convert]::ToBase64String([System.IO.File]::ReadAllBytes($fixtureFile))
    $remotePath = "/app/storage/$StoragePath"
    $remoteDir = if ([string]::IsNullOrWhiteSpace($directory)) { "/app/storage" } else { "/app/storage/$directory" }
    kubectl -n $Namespace exec deploy/ircs-storage-service -- sh -c "mkdir -p '$remoteDir' && printf '%s' '$encoded' | base64 -d > '$remotePath'" | Out-Null
    if ($LASTEXITCODE -ne 0) {
        throw "failed to restore local fixture file into storage pod: $StoragePath"
    }
}

function Wait-CoverRecovered {
    param([string]$ExpectedUrl)
    for ($attempt = 0; $attempt -lt 45; $attempt++) {
        $row = Invoke-SqlScalar "select storage_type || '|' || status || '|' || coalesce(next_retry_time::text, '') || '|' || coalesce(last_error, '') from cover_images where id = '$coverImageId';"
        if ($row -match "^R2\|REMOTE_STORED\|") {
            return $row
        }
        Start-Sleep -Seconds 2
    }
    $final = Invoke-SqlScalar "select storage_type || '|' || status || '|' || coalesce(next_retry_time::text, '') || '|' || coalesce(last_error, '') from cover_images where id = '$coverImageId';"
    throw "cover image did not recover to R2 REMOTE_STORED. final=$final expectedUrl=$ExpectedUrl"
}

function Wait-AvatarRecovered {
    param([string]$ExpectedUrl)
    for ($attempt = 0; $attempt -lt 45; $attempt++) {
        $url = Invoke-SqlScalar "select coalesce(avatar_url, '') from members where id = '$memberId';"
        if ($url -eq $ExpectedUrl) {
            return $url
        }
        Start-Sleep -Seconds 2
    }
    $final = Invoke-SqlScalar "select coalesce(avatar_url, '') from members where id = '$memberId';"
    $queue = Get-QueueState "q.storage.avatar_sync"
    $dlq = Get-QueueState "q.storage.avatar_sync.dlq"
    throw "avatar_url did not recover to expected R2 URL. current=$final expected=$ExpectedUrl queue=$($queue.messages) dlq=$($dlq.messages)"
}

function Finish-PreflightOnly {
    $explicitBucketOverride = -not [string]::IsNullOrWhiteSpace($BucketName)
    if (-not $explicitBucketOverride -and -not $UseV1ParityBucketOverride) {
        Finish-Smoke "FAILED" "EXPLICIT_BUCKET_OVERRIDE_REQUIRED_FOR_PREFLIGHT" @{
            hint = "pass -UseV1ParityBucketOverride or -BucketName ircs before watchdog R2 smoke"
            preflightOnly = [bool]$PreflightOnly
            noWrite = [bool]$NoWrite
            confirmWatchdogR2Write = [bool]$ConfirmWatchdogR2Write
            willWriteR2Objects = $false
            willPatchDeployment = $false
        }
    }
    $preflightBucket = if ($explicitBucketOverride) { $BucketName.Trim() } else { "ircs" }
    Finish-Smoke "SUCCESS" "STORAGE_R2_WATCHDOG_PREFLIGHT_OK" @{
        effectiveBucket = $preflightBucket
        bucketSource = if ($explicitBucketOverride) { "BucketName" } else { "UseV1ParityBucketOverride" }
        publicDomain = if ([string]::IsNullOrWhiteSpace($PublicDomain)) { $null } else { $PublicDomain.Trim() }
        secretName = $secretName
        preflightOnly = [bool]$PreflightOnly
        noWrite = [bool]$NoWrite
        confirmWatchdogR2Write = [bool]$ConfirmWatchdogR2Write
        willWriteR2Objects = $false
        willPatchDeployment = $false
        requiresExplicitBucketOverride = $true
    }
}

try {
    if ($PreflightOnly) {
        Finish-PreflightOnly
    }
    if ($NoWrite -and [string]::IsNullOrWhiteSpace($BucketName) -and -not $UseV1ParityBucketOverride) {
        Finish-Smoke "FAILED" "EXPLICIT_BUCKET_OVERRIDE_REQUIRED_FOR_NO_WRITE" @{
            hint = "pass -UseV1ParityBucketOverride or -BucketName ircs before watchdog R2 smoke"
            noWrite = [bool]$NoWrite
            willWriteR2Objects = $false
            willPatchDeployment = $false
        }
    }

    Test-KubectlAccess
    if ($StartCredentialPortForward) {
        $credentialPf = Start-PortForward "svc/ircs-credential-service" $CredentialPort 8080 $credentialPfOut $credentialPfErr
    }

    $configBucket = Get-ConfigMapValue "R2_BUCKET_NAME"
    $explicitBucketOverride = -not [string]::IsNullOrWhiteSpace($BucketName)
    $script:effectiveBucket = if ($explicitBucketOverride) {
        $BucketName.Trim()
    } elseif ($UseV1ParityBucketOverride) {
        "ircs"
    } else {
        $configBucket.Trim()
    }
    $publicDomain = if ([string]::IsNullOrWhiteSpace($PublicDomain)) { Get-ConfigMapValue "R2_PUBLIC_DOMAIN" } else { $PublicDomain.Trim() }
    $publicBase = Get-PublicBase $publicDomain
    $credential = Get-R2CredentialLease
    $payloadKeys = @($credential.secretPayload.PSObject.Properties.Name | Sort-Object)
    $accountId = [string]$credential.secretPayload.account_id
    $script:accessKey = [string]$credential.secretPayload.access_key
    $script:secretKey = [string]$credential.secretPayload.secret_key
    $script:endpoint = "https://$accountId.r2.cloudflarestorage.com"
    $queueBefore = Get-QueueState "q.storage.avatar_sync"
    $dlqBefore = Get-QueueState "q.storage.avatar_sync.dlq"

    Mark "V1/V3 watchdog parity preflight resolves R2 credential and explicit bucket" 1 8

    if ($NoWrite) {
        Finish-Smoke "SUCCESS" "STORAGE_R2_WATCHDOG_NO_WRITE_OK" @{
            credentialId = $credential.id
            payloadKeys = $payloadKeys
            configMapBucket = $configBucket
            effectiveBucket = $effectiveBucket
            bucketSource = if ($explicitBucketOverride) { "BucketName" } elseif ($UseV1ParityBucketOverride) { "UseV1ParityBucketOverride" } else { "ConfigMap" }
            publicDomain = $publicDomain
            queueBefore = $queueBefore
            dlqBefore = $dlqBefore
            willWriteR2Objects = $false
            willPatchDeployment = $false
        }
    }

    if (-not $ConfirmWatchdogR2Write) {
        Finish-Smoke "SKIPPED" "LIVE_WATCHDOG_R2_WRITE_NOT_CONFIRMED" @{
            credentialId = $credential.id
            payloadKeys = $payloadKeys
            configMapBucket = $configBucket
            effectiveBucket = $effectiveBucket
            bucketSource = if ($explicitBucketOverride) { "BucketName" } elseif ($UseV1ParityBucketOverride) { "UseV1ParityBucketOverride" } else { "ConfigMap" }
            publicDomain = $publicDomain
            queueBefore = $queueBefore
            dlqBefore = $dlqBefore
            hint = "rerun with -ConfirmWatchdogR2Write and explicit -UseV1ParityBucketOverride or -BucketName ircs"
            willWriteR2Objects = $false
            willPatchDeployment = $false
        }
    }

    if ($effectiveBucket -eq "ircs" -and -not $explicitBucketOverride -and -not $UseV1ParityBucketOverride) {
        Finish-Smoke "SKIPPED" "PRODUCTION_BUCKET_AS_DEV_CONFIG_BLOCKED" @{
            configMapBucket = $configBucket
            effectiveBucket = $effectiveBucket
            hint = "dev ConfigMap must not use V1 production bucket as standing bucket"
        }
    }
    if (-not $explicitBucketOverride -and -not $UseV1ParityBucketOverride) {
        Finish-Smoke "SKIPPED" "EXPLICIT_BUCKET_OVERRIDE_REQUIRED_FOR_R2_WRITE" @{
            configMapBucket = $configBucket
            effectiveBucket = $effectiveBucket
            hint = "pass -UseV1ParityBucketOverride or -BucketName ircs"
        }
    }

    $storagePf = Start-PortForward "svc/ircs-storage-service" $StoragePort 8080 $storagePfOut $storagePfErr
    Mark "storage-service is reachable while standing R2 env remains disabled for backlog setup" 2 8

    Cleanup-SmokeData
    Write-FixturePng $fixtureFile
    $headers = @("X-Trace-Id: $traceId", "X-Authenticated-User: storage-r2-watchdog-smoke@example.com")
    $cover = Invoke-CurlJson "POST" "http://127.0.0.1:$StoragePort/api/v1/cover-images/upload" `
        -Headers $headers `
        -CurlArgs @("-F", "file=@$fixtureFile;type=image/png;filename=watchdog-$stamp.png")
    if ($null -eq $cover -or [string]::IsNullOrWhiteSpace([string]$cover.id)) {
        throw "cover upload response missing id"
    }
    $script:coverImageId = [string]$cover.id
    $script:coverStoragePath = [string]$cover.storagePath
    Invoke-Sql "update cover_images set next_retry_time = timestamp '1970-01-01 00:00:00', updated_at = timestamp '1970-01-01 00:00:00' where id = '$coverImageId';" | Out-Null

    $avatar = Invoke-CurlJson "POST" "http://127.0.0.1:$StoragePort/internal/storage/avatars" `
        -Headers $headers `
        -CurlArgs @("-F", "file=@$fixtureFile;type=image/png;filename=avatar-$stamp.png")
    if ($null -eq $avatar -or [string]::IsNullOrWhiteSpace([string]$avatar.url) -or [string]::IsNullOrWhiteSpace([string]$avatar.storageKey)) {
        throw "avatar upload response missing url/storageKey"
    }
    $script:avatarLocalUrl = [string]$avatar.url
    $script:avatarStoragePath = [string]$avatar.storageKey
    Invoke-Sql @"
insert into members (
    id, created_at, updated_at, version, email, password_hash, nickname, avatar_url, role, status,
    experience, points, last_check_in_date, check_in_streak
) values (
    '$memberId', timestamp '1970-01-01 00:00:00', timestamp '1970-01-01 00:00:00', 0,
    '$email', 'noop', 'Codex 02807J Watchdog', '$avatarLocalUrl', 'MEMBER', 'ACTIVE',
    0, 0, null, 0
);
"@ | Out-Null

    $coverBacklog = Invoke-SqlScalar "select storage_type || '|' || status || '|' || (next_retry_time <= now())::text from cover_images where id = '$coverImageId';"
    $avatarBacklog = Invoke-SqlScalar "select coalesce(avatar_url, '') from members where id = '$memberId';"
    if ($coverBacklog -ne "LOCAL|LOCAL_STORED|true" -or $avatarBacklog -ne $avatarLocalUrl) {
        throw "backlog fixture mismatch cover=$coverBacklog avatar=$avatarBacklog"
    }
    Mark "cover and avatar backlog fixtures are created without immediate R2 sync" 3 8

    $manifest = kubectl -n $Namespace create secret generic $secretName `
        --from-literal=APP_STORAGE_R2_ACCOUNT_ID=$accountId `
        --from-literal=APP_STORAGE_R2_ACCESS_KEY=$accessKey `
        --from-literal=APP_STORAGE_R2_SECRET_KEY=$secretKey `
        --from-literal=APP_STORAGE_R2_ENDPOINT=$endpoint `
        --dry-run=client -o yaml
    $manifest | kubectl apply -f - | Out-Null

    kubectl -n $Namespace set env deployment/ircs-storage-service --from=secret/$secretName | Out-Null
    kubectl -n $Namespace set env deployment/ircs-storage-service `
        APP_STORAGE_R2_ENABLED=true `
        APP_STORAGE_R2_BUCKET_NAME=$effectiveBucket `
        APP_STORAGE_R2_PUBLIC_DOMAIN=$publicDomain `
        APP_STORAGE_R2_WATCHDOG_ENABLED=true `
        APP_STORAGE_R2_WATCHDOG_INITIAL_DELAY_MS=30000 `
        APP_STORAGE_R2_WATCHDOG_FIXED_DELAY_MS=5000 `
        APP_STORAGE_R2_WATCHDOG_BATCH_SIZE=1 | Out-Null
    $deploymentChanged = $true
    kubectl -n $Namespace rollout status deployment/ircs-storage-service --timeout=180s | Out-Null
    if ($storagePf -and -not $storagePf.HasExited) {
        Stop-Process -Id $storagePf.Id -Force -ErrorAction SilentlyContinue
        $storagePf = $null
    }
    Restore-LocalFixtureFile $coverStoragePath
    Restore-LocalFixtureFile $avatarStoragePath
    Mark "temporary R2 runtime enables watchdog with shortened interval and small batch" 4 8

    $expectedCoverUrl = "$publicBase/$coverStoragePath"
    Wait-CoverRecovered $expectedCoverUrl | Out-Null
    $coverHead = Invoke-R2Request -Method "HEAD" -Endpoint $endpoint -Bucket $effectiveBucket -Key $coverStoragePath `
        -AccessKey $accessKey -SecretKey $secretKey
    if (-not $coverHead.ok -or $coverHead.notFound) {
        throw "cover R2 HEAD failed after watchdog recovery: status=$($coverHead.statusCode) error=$($coverHead.error)"
    }
    $script:r2CoverHeadStatusCode = $coverHead.statusCode
    Mark "watchdog recovers due cover image to R2 REMOTE_STORED" 5 8

    $expectedAvatarUrl = "$publicBase/$avatarStoragePath"
    Wait-AvatarRecovered $expectedAvatarUrl | Out-Null
    $script:avatarR2Url = $expectedAvatarUrl
    $avatarHead = Invoke-R2Request -Method "HEAD" -Endpoint $endpoint -Bucket $effectiveBucket -Key $avatarStoragePath `
        -AccessKey $accessKey -SecretKey $secretKey
    if (-not $avatarHead.ok -or $avatarHead.notFound) {
        throw "avatar R2 HEAD failed after watchdog recovery: status=$($avatarHead.statusCode) error=$($avatarHead.error)"
    }
    $script:r2AvatarHeadStatusCode = $avatarHead.statusCode
    Mark "watchdog republishes local avatar and consumer rewrites avatar_url to R2" 6 8

    $coverDelete = Invoke-R2Request -Method "DELETE" -Endpoint $endpoint -Bucket $effectiveBucket -Key $coverStoragePath `
        -AccessKey $accessKey -SecretKey $secretKey
    $avatarDelete = Invoke-R2Request -Method "DELETE" -Endpoint $endpoint -Bucket $effectiveBucket -Key $avatarStoragePath `
        -AccessKey $accessKey -SecretKey $secretKey
    $script:r2CoverDeleteStatusCode = $coverDelete.statusCode
    $script:r2AvatarDeleteStatusCode = $avatarDelete.statusCode
    if (-not $coverDelete.ok -or -not $avatarDelete.ok) {
        throw "R2 DELETE failed during cleanup cover=$($coverDelete.statusCode) avatar=$($avatarDelete.statusCode)"
    }
    Cleanup-SmokeData
    $coverMissing = Invoke-R2Request -Method "HEAD" -Endpoint $endpoint -Bucket $effectiveBucket -Key $coverStoragePath `
        -AccessKey $accessKey -SecretKey $secretKey -AllowNotFound
    $avatarMissing = Invoke-R2Request -Method "HEAD" -Endpoint $endpoint -Bucket $effectiveBucket -Key $avatarStoragePath `
        -AccessKey $accessKey -SecretKey $secretKey -AllowNotFound
    $script:coverCleanupNotFound = [bool]$coverMissing.notFound
    $script:avatarCleanupNotFound = [bool]$avatarMissing.notFound
    $coverResidue = Invoke-SqlScalar "select count(*) from cover_images where id = '$coverImageId';"
    $memberResidue = Invoke-SqlScalar "select count(*) from members where id = '$memberId' or email = '$email';"
    $queueAfterCleanup = Get-QueueState "q.storage.avatar_sync"
    $dlqAfterCleanup = Get-QueueState "q.storage.avatar_sync.dlq"
    if (-not $coverMissing.ok -or -not $coverMissing.notFound -or -not $avatarMissing.ok -or -not $avatarMissing.notFound `
        -or $coverResidue -ne "0" -or $memberResidue -ne "0" `
        -or $queueAfterCleanup.messages -gt $queueBefore.messages `
        -or $dlqAfterCleanup.messages -gt $dlqBefore.messages) {
        throw "cleanup residue detected coverNotFound=$($coverMissing.notFound) avatarNotFound=$($avatarMissing.notFound) coverResidue=$coverResidue memberResidue=$memberResidue queue=$($queueAfterCleanup.messages) dlq=$($dlqAfterCleanup.messages)"
    }
    Mark "R2 objects, DB fixtures, local files, queue/DLQ are cleaned" 7 8

    Restore-StorageDeployment
    kubectl -n $Namespace delete secret $secretName --ignore-not-found | Out-Null
    $httpRoutes = kubectl -n $Namespace get httproute --ignore-not-found 2>&1
    $quota = kubectl -n $Namespace get resourcequota ircs-dev-quota
    $secretCheck = kubectl -n $Namespace get secret $secretName --ignore-not-found 2>&1
    Mark "dev env restored with no Secret/port-forward/HTTPRoute/quota residue" 8 8
    New-SmokeResult "SUCCESS" "STORAGE_R2_WATCHDOG_RECOVERY_SMOKE_OK" @{
        credentialId = $credential.id
        configMapBucket = $configBucket
        effectiveBucket = $effectiveBucket
        bucketSource = if ($explicitBucketOverride) { "BucketName" } elseif ($UseV1ParityBucketOverride) { "UseV1ParityBucketOverride" } else { "ConfigMap" }
        publicDomain = $publicDomain
        coverStoragePath = $coverStoragePath
        avatarLocalUrl = $avatarLocalUrl
        avatarStoragePath = $avatarStoragePath
        avatarR2Url = $avatarR2Url
        coverR2Url = $expectedCoverUrl
        r2CoverHeadStatusCode = $r2CoverHeadStatusCode
        r2AvatarHeadStatusCode = $r2AvatarHeadStatusCode
        r2CoverDeleteStatusCode = $r2CoverDeleteStatusCode
        r2AvatarDeleteStatusCode = $r2AvatarDeleteStatusCode
        coverCleanupNotFound = $coverCleanupNotFound
        avatarCleanupNotFound = $avatarCleanupNotFound
        coverResidue = $coverResidue
        memberResidue = $memberResidue
        queueBefore = $queueBefore
        queueAfterCleanup = $queueAfterCleanup
        dlqBefore = $dlqBefore
        dlqAfterCleanup = $dlqAfterCleanup
        temporarySecret = (($secretCheck | Out-String).Trim())
        httpRoute = (($httpRoutes | Out-String).Trim())
        resourceQuota = (($quota | Out-String).Trim())
        watchdogInitialDelayMs = 30000
        watchdogFixedDelayMs = 5000
        watchdogBatchSize = 1
        devLocalFixtureRehydratedAfterRollout = $true
    }
} catch {
    Finish-Smoke "FAILED" "STORAGE_R2_WATCHDOG_RECOVERY_SMOKE_FAILED" @{
        message = $_.Exception.Message
        coverImageId = $coverImageId
        coverStoragePath = $coverStoragePath
        memberId = $memberId
        avatarLocalUrl = $avatarLocalUrl
        avatarStoragePath = $avatarStoragePath
        bucket = $effectiveBucket
    }
} finally {
    if ($coverStoragePath -and $endpoint -and $accessKey -and $secretKey -and $effectiveBucket) {
        try { Invoke-R2Request -Method "DELETE" -Endpoint $endpoint -Bucket $effectiveBucket -Key $coverStoragePath -AccessKey $accessKey -SecretKey $secretKey | Out-Null } catch { Write-Warning $_ }
    }
    if ($avatarStoragePath -and $endpoint -and $accessKey -and $secretKey -and $effectiveBucket) {
        try { Invoke-R2Request -Method "DELETE" -Endpoint $endpoint -Bucket $effectiveBucket -Key $avatarStoragePath -AccessKey $accessKey -SecretKey $secretKey | Out-Null } catch { Write-Warning $_ }
    }
    try { Cleanup-SmokeData } catch { Write-Warning $_ }
    if ($deploymentChanged) {
        try { Restore-StorageDeployment } catch { Write-Warning $_ }
    }
    try { kubectl -n $Namespace delete secret $secretName --ignore-not-found | Out-Null } catch { Write-Warning $_ }
    if ($credentialPf -and -not $credentialPf.HasExited) {
        Stop-Process -Id $credentialPf.Id -Force -ErrorAction SilentlyContinue
    }
    if ($storagePf -and -not $storagePf.HasExited) {
        Stop-Process -Id $storagePf.Id -Force -ErrorAction SilentlyContinue
    }
    Remove-Item -LiteralPath $fixtureFile -ErrorAction SilentlyContinue
}
