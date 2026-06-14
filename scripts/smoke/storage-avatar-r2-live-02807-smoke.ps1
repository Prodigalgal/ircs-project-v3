param(
    [string]$Namespace = "ircs-dev",
    [string]$TaskId = "028-07-D",
    [string]$PortalBaseUrl = "http://127.0.0.1:18086",
    [int]$PortalPort = 18086,
    [string]$CredentialBaseUrl = "http://127.0.0.1:18090",
    [int]$CredentialPort = 18090,
    [string]$CredentialToken = $env:APP_METADATA_CREDENTIAL_SERVICE_TOKEN,
    [string]$BucketName = "",
    [string]$PublicDomain = "",
    [switch]$UseV1ParityBucketOverride,
    [switch]$StartCredentialPortForward,
    [switch]$UseKubernetesSecretToken,
    [switch]$SkipPortalPortForward,
    [switch]$PreflightOnly,
    [switch]$NoWrite,
    [switch]$ConfirmAvatarR2Write,
    [switch]$FailOnSkip
)

$ErrorActionPreference = "Stop"

$stamp = Get-Date -Format "yyyyMMddHHmmss"
$traceId = "02807-avatar-r2-live-$stamp"
$secretName = "ircs-r2-avatar-smoke-02807"
$avatarFile = Join-Path $env:TEMP "ircs-02807-avatar-r2-$stamp.png"
$portalPfOut = Join-Path $env:TEMP "ircs-02807-avatar-portal-pf.out.log"
$portalPfErr = Join-Path $env:TEMP "ircs-02807-avatar-portal-pf.err.log"
$credentialPfOut = Join-Path $env:TEMP "ircs-02807-avatar-credential-pf.out.log"
$credentialPfErr = Join-Path $env:TEMP "ircs-02807-avatar-credential-pf.err.log"
$portalPf = $null
$credentialPf = $null
$deploymentChanged = $false
$email = "codex.02807.avatar.$stamp@example.invalid"
$password = "Codex02807Avatar$stamp"
$nickname = "Codex 02807 Avatar $stamp"
$memberId = $null
$localAvatarUrl = $null
$storagePath = $null
$r2Url = $null
$endpoint = $null
$accessKey = $null
$secretKey = $null
$effectiveBucketName = $null
$r2HeadAfterPutStatusCode = $null
$r2DeleteStatusCode = $null
$r2HeadAfterDeleteStatusCode = $null
$r2HeadAfterDeleteNotFound = $false
$memberResidue = $null
$localAvatarMissing = $null

function New-SmokeResult {
    param(
        [string]$Status,
        [string]$Reason,
        [hashtable]$Details = @{}
    )
    [ordered]@{
        task = $TaskId
        slice = "avatar-r2-live"
        status = $Status
        reason = $Reason
        checkedAt = (Get-Date).ToUniversalTime().ToString("o")
        namespace = $Namespace
        traceId = $traceId
        email = $email
        memberId = $memberId
        details = $Details
    } | ConvertTo-Json -Depth 12
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

function Finish-AvatarR2Preflight {
    $explicitBucketOverride = -not [string]::IsNullOrWhiteSpace($BucketName)
    if (-not $explicitBucketOverride -and -not $UseV1ParityBucketOverride) {
        Finish-Smoke "FAILED" "EXPLICIT_BUCKET_OVERRIDE_REQUIRED_FOR_PREFLIGHT" @{
            hint = "pass -UseV1ParityBucketOverride or -BucketName ircs before an explicit V1 bucket smoke"
            preflightOnly = [bool]$PreflightOnly
            noWrite = [bool]$NoWrite
            confirmAvatarR2Write = [bool]$ConfirmAvatarR2Write
            willWriteR2Objects = $false
            willPatchDeployment = $false
        }
    }

    $preflightBucket = if ($explicitBucketOverride) { $BucketName.Trim() } else { "ircs" }
    Finish-Smoke "SUCCESS" "AVATAR_R2_PREFLIGHT_OK" @{
        effectiveBucket = $preflightBucket
        bucketSource = if ($explicitBucketOverride) { "BucketName" } else { "UseV1ParityBucketOverride" }
        publicDomain = if ([string]::IsNullOrWhiteSpace($PublicDomain)) { $null } else { $PublicDomain.Trim() }
        secretName = $secretName
        preflightOnly = [bool]$PreflightOnly
        noWrite = [bool]$NoWrite
        confirmAvatarR2Write = [bool]$ConfirmAvatarR2Write
        confirmIgnoredByNoWrite = [bool](($PreflightOnly -or $NoWrite) -and $ConfirmAvatarR2Write)
        willWriteR2Objects = $false
        willPatchDeployment = $false
        requiresExplicitBucketOverride = $true
        k8sAccessed = $false
        credentialLeaseAccessed = $false
        portalAccessed = $false
    }
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
        return @{
            ok = $false
            statusCode = $statusCode
            notFound = $false
            error = $_.Exception.Message
        }
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

function Invoke-JsonRequest {
    param(
        [string]$Method,
        [string]$Url,
        [string]$Body = $null,
        [string[]]$Headers = @(),
        [string]$ContentType = "application/json"
    )
    $tmp = Join-Path $env:TEMP ("ircs-02807-avatar-http-" + [guid]::NewGuid() + ".json")
    $requestBodyFile = $null
    $args = @("-sS", "--max-time", "60", "-o", $tmp, "-w", "%{http_code}", "-X", $Method)
    foreach ($header in $Headers) {
        $args += @("-H", $header)
    }
    if ($null -ne $Body) {
        $requestBodyFile = Join-Path $env:TEMP ("ircs-02807-avatar-request-" + [guid]::NewGuid() + ".json")
        [System.IO.File]::WriteAllText($requestBodyFile, $Body, [System.Text.UTF8Encoding]::new($false))
        $args += @("-H", "Content-Type: $ContentType", "--data-binary", "@$requestBodyFile")
    }
    $args += $Url
    $code = & curl.exe @args
    $text = ""
    if (Test-Path $tmp) {
        $text = Get-Content -LiteralPath $tmp -Raw -ErrorAction SilentlyContinue
        Remove-Item -LiteralPath $tmp -ErrorAction SilentlyContinue
    }
    if ($requestBodyFile) {
        Remove-Item -LiteralPath $requestBodyFile -ErrorAction SilentlyContinue
    }
    if ([int]$code -lt 200 -or [int]$code -ge 300) {
        throw "HTTP $code for $Method $Url body=$text"
    }
    if ([string]::IsNullOrWhiteSpace($text)) {
        return $null
    }
    return $text | ConvertFrom-Json
}

function Invoke-AvatarUpload {
    param(
        [string]$Url,
        [string]$Token,
        [string]$Path
    )
    $tmp = Join-Path $env:TEMP ("ircs-02807-avatar-upload-" + [guid]::NewGuid() + ".json")
    $code = & curl.exe -sS --max-time 60 -o $tmp -w "%{http_code}" -X POST `
        -H "Authorization: Bearer $Token" `
        -H "X-Trace-Id: $traceId" `
        -F "file=@$Path;type=image/png;filename=avatar.png" `
        $Url
    $text = ""
    if (Test-Path $tmp) {
        $text = Get-Content -LiteralPath $tmp -Raw -ErrorAction SilentlyContinue
        Remove-Item -LiteralPath $tmp -ErrorAction SilentlyContinue
    }
    if ([int]$code -lt 200 -or [int]$code -ge 300) {
        throw "HTTP $code for POST $Url body=$text"
    }
    return $text | ConvertFrom-Json
}

function Solve-Pow {
    param([object]$Challenge)
    $difficulty = [int]$Challenge.difficulty
    $prefix = "0" * $difficulty
    $sha = [System.Security.Cryptography.SHA256]::Create()
    try {
        for ($i = 0; $i -lt 10000000; $i++) {
            $nonce = [string]$i
            $bytes = [System.Text.Encoding]::UTF8.GetBytes($Challenge.id + $nonce)
            $hashBytes = $sha.ComputeHash($bytes)
            $hash = -join ($hashBytes | ForEach-Object { $_.ToString("x2") })
            if ($hash.StartsWith($prefix)) {
                return @{ id = $Challenge.id; nonce = $nonce }
            }
        }
    } finally {
        $sha.Dispose()
    }
    throw "PoW solve failed for challenge $($Challenge.id)"
}

function New-PowVerification {
    $challenge = Invoke-JsonRequest "GET" "$($PortalBaseUrl.TrimEnd('/'))/api/portal/auth/pow"
    return Solve-Pow $challenge
}

function Write-FixturePng {
    param([string]$Path)
    [byte[]]$png = 0x89,0x50,0x4E,0x47,0x0D,0x0A,0x1A,0x0A,0x00,0x00,0x00,0x0D,0x49,0x48,0x44,0x52,
        0x00,0x00,0x00,0x01,0x00,0x00,0x00,0x01,0x08,0x02,0x00,0x00,0x00,0x90,0x77,0x53,0xDE,
        0x00,0x00,0x00,0x0C,0x49,0x44,0x41,0x54,0x08,0xD7,0x63,0xF8,0xCF,0xC0,0x00,0x00,0x03,
        0x01,0x01,0x00,0x18,0xDD,0x8D,0xB0,0x00,0x00,0x00,0x00,0x49,0x45,0x4E,0x44,0xAE,0x42,
        0x60,0x82
    [System.IO.File]::WriteAllBytes($Path, $png)
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

function Get-MemberAvatarRow {
    $row = Invoke-SqlScalar "select id || '|' || coalesce(avatar_url, '') from members where email = '$email';"
    if ([string]::IsNullOrWhiteSpace($row)) {
        return $null
    }
    $parts = $row -split "\|", 2
    return @{
        id = $parts[0]
        avatarUrl = if ($parts.Count -gt 1) { $parts[1] } else { "" }
    }
}

function Wait-AvatarR2Url {
    param([string]$ExpectedUrl)
    for ($attempt = 0; $attempt -lt 60; $attempt++) {
        $row = Get-MemberAvatarRow
        if ($null -ne $row -and $row.avatarUrl -eq $ExpectedUrl) {
            return $row
        }
        Start-Sleep -Seconds 2
    }
    $final = Get-MemberAvatarRow
    $currentUrl = if ($final) { $final.avatarUrl } else { "" }
    $queue = Get-QueueState "q.storage.avatar_sync"
    $dlq = Get-QueueState "q.storage.avatar_sync.dlq"
    throw "avatar_url did not become expected R2 URL. current=$currentUrl expected=$ExpectedUrl queue=$($queue.messages) dlq=$($dlq.messages)"
}

function Test-RemoteFileMissing {
    param([string]$Path)
    $previous = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    kubectl -n $Namespace exec deploy/ircs-storage-service -- sh -c "test ! -f '/app/storage/$Path'" | Out-Null
    $exit = $LASTEXITCODE
    $ErrorActionPreference = $previous
    return $exit -eq 0
}

function Cleanup-SmokeData {
    if ($storagePath) {
        try {
            kubectl -n $Namespace exec deploy/ircs-storage-service -- sh -c "rm -f '/app/storage/$storagePath'" | Out-Null
        } catch {
            Write-Warning $_
        }
    }
    try {
        Invoke-Sql "delete from members where email = '$email';" | Out-Null
    } catch {
        Write-Warning $_
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
            APP_STORAGE_R2_WATCHDOG_ENABLED- | Out-Null
        kubectl apply -f deploy/k8s/dev/storage-service-dev.yaml | Out-Null
        kubectl -n $Namespace rollout status deployment/ircs-storage-service --timeout=180s | Out-Null
        $script:deploymentChanged = $false
    }
}

try {
    if ($PreflightOnly) {
        Finish-AvatarR2Preflight
    }
    if ($NoWrite -and [string]::IsNullOrWhiteSpace($BucketName) -and -not $UseV1ParityBucketOverride) {
        Finish-Smoke "FAILED" "EXPLICIT_BUCKET_OVERRIDE_REQUIRED_FOR_NO_WRITE" @{
            hint = "pass -UseV1ParityBucketOverride or -BucketName ircs before an explicit V1 bucket smoke"
            preflightOnly = [bool]$PreflightOnly
            noWrite = [bool]$NoWrite
            confirmAvatarR2Write = [bool]$ConfirmAvatarR2Write
            willWriteR2Objects = $false
            willPatchDeployment = $false
        }
    }

    Test-KubectlAccess
    if ($StartCredentialPortForward) {
        $credentialPf = Start-PortForward "svc/ircs-credential-service" $CredentialPort 8080 $credentialPfOut $credentialPfErr
    }
    if (-not $SkipPortalPortForward -and -not $NoWrite) {
        $portalPf = Start-PortForward "svc/ircs-portal-bff" $PortalPort 8080 $portalPfOut $portalPfErr
    }

    $configBucket = Get-ConfigMapValue "R2_BUCKET_NAME"
    $explicitBucketOverride = -not [string]::IsNullOrWhiteSpace($BucketName)
    $script:effectiveBucketName = if ($explicitBucketOverride) {
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
    if ($queueBefore.consumers -lt 1) {
        throw "q.storage.avatar_sync has no consumers"
    }
    Mark "avatar R2 credential/config resolves V1 bucket override" 1 7

    if ($NoWrite) {
        Finish-Smoke "SUCCESS" "AVATAR_R2_NO_WRITE_OK" @{
            credentialId = $credential.id
            payloadKeys = $payloadKeys
            configMapBucket = $configBucket
            effectiveBucket = $effectiveBucketName
            bucketSource = if ($explicitBucketOverride) { "BucketName" } elseif ($UseV1ParityBucketOverride) { "UseV1ParityBucketOverride" } else { "ConfigMap" }
            publicDomain = $publicDomain
            endpointHost = ([System.Uri]$endpoint).Host
            queueBefore = $queueBefore
            dlqBefore = $dlqBefore
            secretName = $secretName
            preflightOnly = [bool]$PreflightOnly
            noWrite = [bool]$NoWrite
            confirmAvatarR2Write = [bool]$ConfirmAvatarR2Write
            confirmIgnoredByNoWrite = [bool]($NoWrite -and $ConfirmAvatarR2Write)
            willWriteR2Objects = $false
            willPatchDeployment = $false
            requiresExplicitBucketOverride = $true
            portalAccessed = $false
        }
    }

    if (-not $ConfirmAvatarR2Write) {
        Finish-Smoke "SKIPPED" "LIVE_AVATAR_R2_WRITE_NOT_CONFIRMED" @{
            credentialId = $credential.id
            payloadKeys = $payloadKeys
            configMapBucket = $configBucket
            effectiveBucket = $effectiveBucketName
            bucketSource = if ($explicitBucketOverride) { "BucketName" } elseif ($UseV1ParityBucketOverride) { "UseV1ParityBucketOverride" } else { "ConfigMap" }
            publicDomain = $publicDomain
            endpointHost = ([System.Uri]$endpoint).Host
            queueBefore = $queueBefore
            dlqBefore = $dlqBefore
            preflightOnly = [bool]$PreflightOnly
            noWrite = [bool]$NoWrite
            confirmAvatarR2Write = [bool]$ConfirmAvatarR2Write
            willWriteR2Objects = $false
            willPatchDeployment = $false
            hint = "rerun with -ConfirmAvatarR2Write and explicit -BucketName or -UseV1ParityBucketOverride to patch storage-service temporarily"
        }
    }

    if ($effectiveBucketName -eq "ircs" -and -not $explicitBucketOverride -and -not $UseV1ParityBucketOverride) {
        Finish-Smoke "SKIPPED" "PRODUCTION_BUCKET_AS_DEV_CONFIG_BLOCKED" @{
            configMapBucket = $configBucket
            effectiveBucket = $effectiveBucketName
            hint = "dev ConfigMap must not use V1 production bucket as its standing bucket; pass -UseV1ParityBucketOverride or -BucketName ircs for a temporary parity smoke"
        }
    }

    if (-not $explicitBucketOverride -and -not $UseV1ParityBucketOverride) {
        Finish-Smoke "SKIPPED" "EXPLICIT_BUCKET_OVERRIDE_REQUIRED_FOR_R2_WRITE" @{
            configMapBucket = $configBucket
            effectiveBucket = $effectiveBucketName
            preflightOnly = [bool]$PreflightOnly
            noWrite = [bool]$NoWrite
            confirmAvatarR2Write = [bool]$ConfirmAvatarR2Write
            willWriteR2Objects = $false
            willPatchDeployment = $false
            hint = "pass -UseV1ParityBucketOverride or -BucketName ircs before an explicit V1 bucket smoke"
        }
    }

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
        APP_STORAGE_R2_BUCKET_NAME=$effectiveBucketName `
        APP_STORAGE_R2_PUBLIC_DOMAIN=$publicDomain `
        APP_STORAGE_R2_WATCHDOG_ENABLED=false | Out-Null
    $deploymentChanged = $true
    kubectl -n $Namespace rollout status deployment/ircs-storage-service --timeout=180s | Out-Null
    Mark "storage-service temporary R2 runtime is enabled without watchdog scan" 2 7

    Cleanup-SmokeData
    Write-FixturePng $avatarFile
    $registerBody = @{
        email = $email
        password = $password
        nickname = $nickname
        powVerification = New-PowVerification
    } | ConvertTo-Json -Depth 6 -Compress
    Invoke-JsonRequest "POST" "$($PortalBaseUrl.TrimEnd('/'))/api/portal/auth/register" $registerBody @("X-Trace-Id: $traceId") | Out-Null
    Invoke-Sql "update members set status = 'ACTIVE' where email = '$email';" | Out-Null
    $loginBody = @{
        email = $email
        password = $password
        powVerification = New-PowVerification
    } | ConvertTo-Json -Depth 6 -Compress
    $tokenResponse = Invoke-JsonRequest "POST" "$($PortalBaseUrl.TrimEnd('/'))/api/portal/auth/login" $loginBody @("X-Trace-Id: $traceId")
    if (-not $tokenResponse.token) {
        throw "portal login did not return token"
    }
    Mark "portal register/login fixture is active for avatar upload" 3 7

    $avatar = Invoke-AvatarUpload "$($PortalBaseUrl.TrimEnd('/'))/api/portal/profile/avatar" $tokenResponse.token $avatarFile
    if (-not $avatar.url -or -not ([string]$avatar.url).StartsWith("/media/avatars/") -or -not ([string]$avatar.url).EndsWith(".png")) {
        throw "avatar upload response mismatch: $($avatar | ConvertTo-Json -Depth 6 -Compress)"
    }
    $script:localAvatarUrl = [string]$avatar.url
    $script:storagePath = $localAvatarUrl.Substring("/media/".Length)
    $expectedUrl = "$publicBase/$storagePath"
    $uploadedRow = Get-MemberAvatarRow
    if ($null -eq $uploadedRow) {
        throw "member row missing after avatar upload"
    }
    $script:memberId = $uploadedRow.id
    Mark "portal/identity avatar upload returns local media URL and publishes SYNC_AVATAR" 4 7

    $syncedRow = Wait-AvatarR2Url $expectedUrl
    $script:r2Url = $syncedRow.avatarUrl
    $queueAfterSync = Get-QueueState "q.storage.avatar_sync"
    $dlqAfterSync = Get-QueueState "q.storage.avatar_sync.dlq"
    if ($queueAfterSync.messages -gt $queueBefore.messages -or $dlqAfterSync.messages -gt $dlqBefore.messages) {
        throw "avatar sync queue residue increased. queueBefore=$($queueBefore.messages) queueAfter=$($queueAfterSync.messages) dlqBefore=$($dlqBefore.messages) dlqAfter=$($dlqAfterSync.messages)"
    }
    Mark "q.storage.avatar_sync drains and members.avatar_url becomes R2 public URL" 5 7

    $head = Invoke-R2Request -Method "HEAD" -Endpoint $endpoint -Bucket $effectiveBucketName -Key $storagePath `
        -AccessKey $accessKey -SecretKey $secretKey
    if (-not $head.ok -or $head.notFound) {
        throw "R2 object HEAD after avatar sync failed: status=$($head.statusCode) error=$($head.error)"
    }
    $script:r2HeadAfterPutStatusCode = $head.statusCode
    $script:localAvatarMissing = Test-RemoteFileMissing $storagePath
    if (-not $localAvatarMissing) {
        throw "local avatar file still exists after R2 sync: $storagePath"
    }
    Mark "R2 object exists and local avatar file is cleaned" 6 7

    $delete = Invoke-R2Request -Method "DELETE" -Endpoint $endpoint -Bucket $effectiveBucketName -Key $storagePath `
        -AccessKey $accessKey -SecretKey $secretKey
    if (-not $delete.ok) {
        throw "R2 object DELETE failed during cleanup: status=$($delete.statusCode) error=$($delete.error)"
    }
    $script:r2DeleteStatusCode = $delete.statusCode
    Invoke-Sql "delete from members where email = '$email';" | Out-Null
    $missing = Invoke-R2Request -Method "HEAD" -Endpoint $endpoint -Bucket $effectiveBucketName -Key $storagePath `
        -AccessKey $accessKey -SecretKey $secretKey -AllowNotFound
    $script:r2HeadAfterDeleteStatusCode = $missing.statusCode
    $script:r2HeadAfterDeleteNotFound = [bool]$missing.notFound
    $script:memberResidue = Invoke-SqlScalar "select count(*) from members where email = '$email';"
    $queueAfterCleanup = Get-QueueState "q.storage.avatar_sync"
    $dlqAfterCleanup = Get-QueueState "q.storage.avatar_sync.dlq"
    if (-not $missing.ok -or -not $missing.notFound -or $memberResidue -ne "0" `
        -or $queueAfterCleanup.messages -gt $queueBefore.messages `
        -or $dlqAfterCleanup.messages -gt $dlqBefore.messages) {
        throw "cleanup residue detected: r2NotFound=$($missing.notFound), memberResidue=$memberResidue, queue=$($queueAfterCleanup.messages), dlq=$($dlqAfterCleanup.messages)"
    }

    Restore-StorageDeployment
    kubectl -n $Namespace delete secret $secretName --ignore-not-found | Out-Null
    $httpRoutes = kubectl -n $Namespace get httproute --ignore-not-found 2>&1
    $quota = kubectl -n $Namespace get resourcequota ircs-dev-quota
    Mark "R2 object/member/queue cleanup and dev env restore complete" 7 7
    New-SmokeResult "SUCCESS" "AVATAR_R2_LIVE_SMOKE_OK" @{
        credentialId = $credential.id
        configMapBucket = $configBucket
        effectiveBucket = $effectiveBucketName
        bucketSource = if ($explicitBucketOverride) { "BucketName" } elseif ($UseV1ParityBucketOverride) { "UseV1ParityBucketOverride" } else { "ConfigMap" }
        publicDomain = $publicDomain
        localAvatarUrl = $localAvatarUrl
        r2Url = $r2Url
        publicUrl = $r2Url
        storagePath = $storagePath
        r2HeadAfterPutStatusCode = $r2HeadAfterPutStatusCode
        r2DeleteStatusCode = $r2DeleteStatusCode
        r2HeadAfterDeleteStatusCode = $r2HeadAfterDeleteStatusCode
        r2HeadAfterDeleteNotFound = $r2HeadAfterDeleteNotFound
        memberResidue = $memberResidue
        localAvatarMissing = $localAvatarMissing
        queueBefore = $queueBefore
        queueAfterSync = $queueAfterSync
        queueAfterCleanup = $queueAfterCleanup
        dlqBefore = $dlqBefore
        dlqAfterCleanup = $dlqAfterCleanup
        httpRoute = (($httpRoutes | Out-String).Trim())
        resourceQuota = (($quota | Out-String).Trim())
    }
} catch {
    Finish-Smoke "FAILED" "AVATAR_R2_LIVE_SMOKE_FAILED" @{
        message = $_.Exception.Message
        memberId = $memberId
        localAvatarUrl = $localAvatarUrl
        r2Url = $r2Url
        storagePath = $storagePath
        bucket = $effectiveBucketName
    }
} finally {
    if ($storagePath -and $endpoint -and $accessKey -and $secretKey -and $effectiveBucketName) {
        try {
            Invoke-R2Request -Method "DELETE" -Endpoint $endpoint -Bucket $effectiveBucketName -Key $storagePath `
                -AccessKey $accessKey -SecretKey $secretKey | Out-Null
        } catch {
            Write-Warning $_
        }
    }
    try { Cleanup-SmokeData } catch { Write-Warning $_ }
    if ($deploymentChanged) {
        try { Restore-StorageDeployment } catch { Write-Warning $_ }
    }
    try { kubectl -n $Namespace delete secret $secretName --ignore-not-found | Out-Null } catch { Write-Warning $_ }
    if ($credentialPf -and -not $credentialPf.HasExited) {
        Stop-Process -Id $credentialPf.Id -Force -ErrorAction SilentlyContinue
    }
    if ($portalPf -and -not $portalPf.HasExited) {
        Stop-Process -Id $portalPf.Id -Force -ErrorAction SilentlyContinue
    }
    Remove-Item -LiteralPath $avatarFile -ErrorAction SilentlyContinue
}
