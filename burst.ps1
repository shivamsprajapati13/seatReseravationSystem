param(
    [Parameter(Mandatory = $true)]
    [string]$BaseUrl,
    [int]$TotalRequests = 2000,
    [int]$Parallelism = 32,
    [string]$AdminToken = "admin-secret"
)

$ErrorActionPreference = "Stop"
$BaseUrl = $BaseUrl.TrimEnd("/")

function Invoke-ReserveOnce {
    param(
        [string]$BaseUrl,
        [string]$ShowId,
        [string]$User,
        [string]$Key,
        [string]$Seat
    )
    $uri = "$BaseUrl/shows/$ShowId/reserve"
    $body = "{`"seats`":[`"$Seat`"]}"
    try {
        $r = Invoke-WebRequest -Uri $uri -Method POST -Headers @{
            Authorization     = "Bearer $User"
            "Idempotency-Key" = $Key
        } -Body $body -ContentType "application/json" -TimeoutSec 180 -ErrorAction Stop
        return [int]$r.StatusCode
    } catch {
        if ($null -ne $_.Exception.Response) {
            return [int]$_.Exception.Response.StatusCode
        }
        return 0
    }
}

function Classify-Status {
    param([int]$Code)
    switch ($Code) {
        201 { "confirmed_201" }
        200 { "idempotent_200" }
        default {
            if ($Code -ge 500) { "server_5xx" }
            elseif ($Code -eq 409) { "declined_409" }
            elseif ($Code -eq 401) { "auth_401" }
            elseif ($Code -eq 0) { "network_error" }
            else { "other_$Code" }
        }
    }
}

Write-Host "=== Burst: $TotalRequests requests against $BaseUrl (parallelism=$Parallelism) ==="
$sw = [System.Diagnostics.Stopwatch]::StartNew()

# Enough seats for scatter + limit tests; hot seat is B1
$seatCount = [Math]::Max(500, [Math]::Min(2000, [int]($TotalRequests / 20)))
$seatList = 1..$seatCount | ForEach-Object { "B$_" }
$seatsJson = ($seatList | ForEach-Object { "`"$_`"" }) -join ","
$createBody = "{`"name`":`"burst-$(Get-Date -Format 'yyyyMMddHHmmss')`",`"seats`":[$seatsJson],`"price_paise`":1000,`"per_user_limit`":4}"

$create = Invoke-WebRequest -Uri "$BaseUrl/shows" -Method POST -Headers @{
    "X-Admin-Token" = $AdminToken
} -Body $createBody -ContentType "application/json" -TimeoutSec 180
if ($create.StatusCode -ne 201) {
    throw "Create show failed: $($create.StatusCode) $($create.Content)"
}
$showId = ($create.Content | ConvertFrom-Json).id
$runId = Get-Date -Format "yyyyMMddHHmmss"
Write-Host "ShowId: $showId seats=$seatCount per_user_limit=4 runId=$runId"

# Work plan (~20k by default)
$hotCount = [int]($TotalRequests * 0.60)      # same hot seat
$idemCount = [int]($TotalRequests * 0.25)     # same idempotency key
$limitCount = [int]($TotalRequests * 0.10)    # per-user limit storm
$scatterCount = $TotalRequests - $hotCount - $idemCount - $limitCount

$works = [System.Collections.Generic.List[object]]::new()
for ($i = 1; $i -le $hotCount; $i++) {
    $works.Add([pscustomobject]@{ Phase = "hot"; User = "hot-$i"; Key = "hot-$runId-$i"; Seat = "B1" })
}
for ($i = 1; $i -le $idemCount; $i++) {
    $works.Add([pscustomobject]@{ Phase = "idem"; User = "idem-$runId"; Key = "idem-$runId"; Seat = "B2" })
}
for ($i = 1; $i -le $limitCount; $i++) {
    $seatNum = ($i % 40) + 3
    $works.Add([pscustomobject]@{
        Phase = "limit"; User = "limit-$runId"; Key = "limit-$runId-$i"; Seat = "B$seatNum"
    })
}
for ($i = 1; $i -le $scatterCount; $i++) {
    $seatNum = ($i % ($seatCount - 2)) + 3
    $works.Add([pscustomobject]@{
        Phase = "scatter"; User = "scatter-$i"; Key = "scatter-$runId-$i"; Seat = "B$seatNum"
    })
}

Write-Host "Plan: hot=$hotCount idem=$idemCount limit=$limitCount scatter=$scatterCount total=$($works.Count)"
Write-Host "Firing requests (this may take several minutes on Windows)..."

$counts = @{}
$worksArray = $works.ToArray()
$workFile = Join-Path $env:TEMP ("burst-work-{0}.ndjson" -f $runId)
try {
    foreach ($w in $worksArray) {
        @{ User = $w.User; Key = $w.Key; Seat = $w.Seat } | ConvertTo-Json -Compress | Add-Content -Path $workFile -Encoding utf8
    }
    $chunkSize = [int][Math]::Ceiling($worksArray.Count / [double]$Parallelism)
    $workerScript = {
        param([string]$Path, [int]$Skip, [int]$Take, [string]$Base, [string]$Show)
        $codes = New-Object System.Collections.Generic.List[int]
        Get-Content -Path $Path -Encoding utf8 | Select-Object -Skip $Skip -First $Take | ForEach-Object {
            $w = $_ | ConvertFrom-Json
            $uri = "$Base/shows/$Show/reserve"
            $body = "{`"seats`":[`"$($w.Seat)`"]}"
            $status = 0
            for ($attempt = 1; $attempt -le 4; $attempt++) {
                try {
                    $r = Invoke-WebRequest -Uri $uri -Method POST -Headers @{
                        Authorization     = "Bearer $($w.User)"
                        "Idempotency-Key" = $w.Key
                    } -Body $body -ContentType "application/json" -TimeoutSec 180 -ErrorAction Stop
                    $status = [int]$r.StatusCode
                    break
                } catch {
                    if ($null -ne $_.Exception.Response) {
                        $status = [int]$_.Exception.Response.StatusCode
                        break
                    }
                    if ($attempt -lt 4) { Start-Sleep -Milliseconds (100 * $attempt) }
                }
            }
            $codes.Add($status)
        }
        return ,$codes.ToArray()
    }

    $jobs = @()
    for ($skip = 0; $skip -lt $worksArray.Count; $skip += $chunkSize) {
        $take = [Math]::Min($chunkSize, $worksArray.Count - $skip)
        $jobs += Start-Job -ScriptBlock $workerScript -ArgumentList $workFile, $skip, $take, $BaseUrl, $showId
    }
    Wait-Job -Job $jobs | Out-Null
    $bag = [System.Collections.Generic.List[int]]::new()
    foreach ($j in $jobs) {
        $out = Receive-Job -Job $j
        if ($null -eq $out) { continue }
        foreach ($code in @($out)) {
            $bag.Add([int]$code)
        }
    }
    Remove-Job -Job $jobs -Force
} finally {
    if (Test-Path $workFile) { Remove-Item -Force $workFile }
}

foreach ($code in $bag) {
    $bucket = Classify-Status $code
    if (-not $counts.ContainsKey($bucket)) { $counts[$bucket] = 0 }
    $counts[$bucket]++
}

$sw.Stop()
Write-Host "`n--- Outcome distribution ($($bag.Count) responses in $([math]::Round($sw.Elapsed.TotalSeconds, 1))s) ---"
$counts.GetEnumerator() | Sort-Object Name | ForEach-Object { Write-Host ("  {0}: {1}" -f $_.Key, $_.Value) }

$c201 = if ($counts.ContainsKey("confirmed_201")) { $counts["confirmed_201"] } else { 0 }
$c5xx = if ($counts.ContainsKey("server_5xx")) { $counts["server_5xx"] } else { 0 }
$c0 = if ($counts.ContainsKey("network_error")) { $counts["network_error"] } else { 0 }

$show = $null
for ($attempt = 1; $attempt -le 6; $attempt++) {
    try {
        $show = Invoke-RestMethod -Uri "$BaseUrl/shows/$showId" -Method GET -TimeoutSec 180 -ErrorAction Stop
        break
    } catch {
        if ($attempt -eq 6) { throw }
        Start-Sleep -Seconds ([Math]::Min(15, 2 * $attempt))
    }
}
$c = $show.counts
$sum = $c.available + $c.held + $c.confirmed
Write-Host "`n--- Reconciliation ---"
Write-Host "available=$($c.available) held=$($c.held) confirmed=$($c.confirmed) total=$($c.total_seats) sum=$sum"

$failed = $false
if ($sum -ne $c.total_seats) { Write-Host "INVARIANT FAILED"; $failed = $true }
if ($c5xx -gt 0 -or $c0 -gt 0) { Write-Host "5xx/network errors detected"; $failed = $true }

# Hot seat B1: at most one 201 (others 409/200)
$b1Confirmed = ($show.seats | Where-Object { $_.seat_number -eq "B1" -and $_.status -eq "confirmed" }).Count
if ($b1Confirmed -gt 1) {
    Write-Host "B1 double-sell detected: confirmed count=$b1Confirmed"
    $failed = $true
}

if ($failed) { exit 1 }
Write-Host "`nBurst PASSED (total_requests=$($bag.Count), hot_seat_201=$c201)"
exit 0
