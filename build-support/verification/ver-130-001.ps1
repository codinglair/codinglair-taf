#requires -Version 7.0
<#
.SYNOPSIS
Runs the infrastructure-independent VER-130-001 release-candidate gate.
.DESCRIPTION
Stages the current checkout, verifies Apple/Android protocol contracts, validates documentation and
MCP transports, builds external consumers with isolated dependency resolution, and qualifies both
profiles of one locally built MCP image. This command never represents protocol fixtures as live
Apple qualification. Full logs and a provenance manifest are retained below target/ver-130-001.
#>
[CmdletBinding()]
param(
    [string] $EvidenceDirectory = 'target/ver-130-001'
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

function Invoke-RecordedCommand {
    param([string] $Name, [string] $Executable, [string[]] $Arguments)
    $log = Join-Path $script:evidenceRoot "$Name.log"
    Write-Host "# $Name"
    Write-Host ("{0} {1}" -f $Executable, ($Arguments -join ' '))
    & $Executable @Arguments 2>&1 | Tee-Object -FilePath $log
    if ($LASTEXITCODE -ne 0) { throw "$Name failed with exit code $LASTEXITCODE. See $log." }
    $script:results.Add([ordered]@{ name = $Name; status = 'PASSED'; log = $log })
}

function Get-TreeFingerprint {
    $entries = @(& git ls-files --deduplicate --cached --modified --others --exclude-standard)
    if ($LASTEXITCODE -ne 0) { throw 'Unable to enumerate candidate source files.' }
    $lines = foreach ($entry in ($entries | Sort-Object)) {
        $hash = (Get-FileHash -Algorithm SHA256 -LiteralPath $entry).Hash.ToLowerInvariant()
        "$hash  $($entry.Replace('\', '/'))"
    }
    $inventory = Join-Path $script:evidenceRoot 'source-tree-files.sha256'
    $lines | Set-Content -LiteralPath $inventory -Encoding utf8NoBOM
    return (Get-FileHash -Algorithm SHA256 -LiteralPath $inventory).Hash.ToLowerInvariant()
}

function Invoke-McpImageProfiles {
    param([string] $Image)
    $name = 'taf-ver-130-001-' + [guid]::NewGuid().ToString('N').Substring(0, 12)
    try {
        $compatibility = @(& docker run --rm --entrypoint cat $Image /opt/taf/image-compatibility.json)
        if ($LASTEXITCODE -ne 0 -or ($compatibility -join "`n") -notmatch '"tafRelease"\s*:\s*"1\.3\.0"') {
            throw 'MCP image compatibility metadata is missing the Apple release.'
        }
        $catalog = @(& docker run --rm --entrypoint cat $Image /opt/taf/capabilities.json)
        if ($LASTEXITCODE -ne 0 -or ($catalog -join "`n") -notmatch '"id"\s*:\s*"mobile\.apple"') {
            throw 'MCP image capability catalog is missing mobile.apple.'
        }
        $frames = @(
            '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-06-18","capabilities":{},"clientInfo":{"name":"ver-130-001","version":"1"}}}',
            '{"jsonrpc":"2.0","method":"notifications/initialized"}'
        )
        $stdio = @($frames | docker run --rm -i --read-only --cap-drop ALL --security-opt no-new-privileges --network none --tmpfs /tmp:rw,noexec,nosuid,nodev,size=128m $Image stdio)
        if ($LASTEXITCODE -ne 0 -or ($stdio -join "`n") -notmatch '"id"\s*:\s*1') {
            throw 'MCP STDIO initialize fixture failed.'
        }
        $configuration = (Resolve-Path 'containers/mcp/application-smoke.yaml').Path
        # An empty host-port segment asks Docker for an ephemeral loopback port; only the
        # container-side service port is fixed. Resolve the assigned host port below.
        & docker run -d --name $name --read-only --cap-drop ALL --security-opt no-new-privileges `
            --pids-limit 128 --memory 768m --cpus 1 --tmpfs /tmp:rw,noexec,nosuid,nodev,size=128m `
            -p 127.0.0.1::8080 --mount "type=bind,src=$configuration,dst=/etc/taf-mcp/application.yaml,readonly" `
            $Image streamable-http | Out-Null
        if ($LASTEXITCODE -ne 0) { throw 'MCP Streamable HTTP container failed to start.' }
        $port = (& docker port $name 8080/tcp).Split(':')[-1]
        $ready = $false
        foreach ($attempt in 1..30) {
            try {
                Invoke-WebRequest -UseBasicParsing "http://127.0.0.1:$port/actuator/health/readiness" | Out-Null
                $ready = $true
                break
            }
            catch { Start-Sleep -Seconds 2 }
        }
        if (-not $ready) { throw 'MCP Streamable HTTP readiness fixture failed.' }
        & docker stop --time 30 $name | Out-Null
        if ((& docker inspect --format '{{.State.ExitCode}}' $name) -ne '143') {
            throw 'MCP Streamable HTTP profile did not terminate cleanly.'
        }
        $script:results.Add([ordered]@{
            name = 'mcp-image-profiles'; status = 'PASSED'; log = 'manifest.json'
        })
    }
    finally {
        & docker rm --force $name 2>$null | Out-Null
    }
}

$repositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
Push-Location $repositoryRoot
try {
    $script:evidenceRoot = [System.IO.Path]::GetFullPath((Join-Path $repositoryRoot $EvidenceDirectory))
    $stageLog = Join-Path ([System.IO.Path]::GetTempPath()) ("ver-130-001-stage-{0}.log" -f [guid]::NewGuid().ToString('N'))
    try {
        Write-Host '# candidate-stage'
        & .\mvnw.cmd -B -ntp clean deploy -Prelease-staging -DskipTests 2>&1 | Tee-Object -FilePath $stageLog
        if ($LASTEXITCODE -ne 0) { throw "candidate-stage failed with exit code $LASTEXITCODE." }
    }
    catch { throw }
    New-Item -ItemType Directory -Force -Path $script:evidenceRoot | Out-Null
    $script:results = [System.Collections.Generic.List[object]]::new()
    Move-Item -LiteralPath $stageLog -Destination (Join-Path $script:evidenceRoot 'candidate-stage.log')
    $script:results.Add([ordered]@{
        name = 'candidate-stage'; status = 'PASSED'; log = (Join-Path $script:evidenceRoot 'candidate-stage.log')
    })
    $started = [DateTimeOffset]::UtcNow
    $sourceHead = (& git rev-parse HEAD).Trim()
    if ($LASTEXITCODE -ne 0) { throw 'Unable to identify candidate HEAD.' }
    $treeFingerprint = Get-TreeFingerprint

    Invoke-RecordedCommand 'mobile-contracts' '.\mvnw.cmd' @(
        '-B', '-ntp', '-pl', 'codinglair-taf-runtime/taf-mobile-appium', '-am',
        'verify', '-Parchitecture,dependency-analysis')
    Invoke-RecordedCommand 'mcp-contracts' '.\mvnw.cmd' @(
        '-B', '-ntp', '-pl', 'taf-mcp-server/taf-mcp-transport-stdio,taf-mcp-server/taf-mcp-transport-http',
        '-am', 'verify')
    Invoke-RecordedCommand 'compatibility-contracts' '.\mvnw.cmd' @(
        '-B', '-ntp', '-Papi-compatibility,schema-compatibility', 'verify')

    $docClasses = Join-Path $script:evidenceRoot 'documentation-contract'
    New-Item -ItemType Directory -Force -Path $docClasses | Out-Null
    Invoke-RecordedCommand 'documentation-compile' 'javac' @(
        '-d', $docClasses, 'build-support/docs/QuickStartDocumentationTest.java',
        'build-support/docs/AppleDocumentationTest.java')
    Invoke-RecordedCommand 'documentation-contract' 'java' @('-cp', $docClasses, 'AppleDocumentationTest')

    Invoke-RecordedCommand 'external-consumers' '.\mvnw.cmd' @(
        '-B', '-ntp', '-N', '-Pconsumer-smoke', 'verify')

    $revision = ([xml](Get-Content -Raw pom.xml)).project.properties.revision
    $candidateRepository = (Resolve-Path 'target/staging-repository').Path.Replace('\', '/')
    $isolatedRepository = Join-Path $script:evidenceRoot 'apple-consumer-repository'
    Invoke-RecordedCommand 'apple-standalone-consumer' '.\mvnw.cmd' @(
        '-B', '-ntp', '-f', 'examples/apple-appium-consumer/pom.xml', 'verify',
        "-Dtaf.version=$revision", "-Dtaf.candidate.repository=file:///$candidateRepository",
        "-Dmaven.repo.local=$isolatedRepository")

    $image = "codinglair/codinglair-taf-mcp:ver-130-001-$revision"
    $created = [DateTimeOffset]::UtcNow.ToString('yyyy-MM-ddTHH:mm:ssZ')
    Invoke-RecordedCommand 'mcp-image-build' 'docker' @(
        'build', '--file', 'containers/mcp/Dockerfile', '--build-arg', "VERSION=$revision",
        '--build-arg', "REVISION=$sourceHead", '--build-arg', "CREATED=$created", '--tag', $image, '.')
    # PowerShell-native equivalent of containers/mcp/smoke.sh for the supported Windows local gate.
    Invoke-McpImageProfiles $image
    $imageId = (& docker image inspect --format '{{.Id}}' $image).Trim()
    if ($LASTEXITCODE -ne 0) { throw 'Unable to inspect the qualified MCP image.' }

    $artifactHashes = Get-ChildItem -File -Recurse 'target/staging-repository/com/codinglair/taf' |
        Sort-Object FullName | ForEach-Object {
            [ordered]@{
                path = [System.IO.Path]::GetRelativePath($repositoryRoot, $_.FullName).Replace('\', '/')
                sha256 = (Get-FileHash -Algorithm SHA256 -LiteralPath $_.FullName).Hash.ToLowerInvariant()
            }
        }
    $record = [ordered]@{
        assignment = 'VER-130-001'
        classification = 'LOCAL_PROTOCOL_AND_STANDALONE_CONSUMER_ONLY'
        liveAppleQualification = 'UNVERIFIED'
        source = [ordered]@{ head = $sourceHead; treeFingerprintSha256 = $treeFingerprint }
        candidateVersion = $revision
        startedUtc = $started.ToString('o')
        completedUtc = [DateTimeOffset]::UtcNow.ToString('o')
        cases = [ordered]@{
            A01 = 'mobile-contracts'; A02 = 'mobile-contracts'; A03 = 'mobile-contracts'
            A04 = 'mobile-contracts'; A05 = 'mobile-contracts'; A06 = 'mobile-contracts'
            A07 = 'mobile-contracts,mcp-contracts'; A08 = 'external-consumers,apple-standalone-consumer,mcp-image-profiles'
        }
        results = $script:results
        mcpImageId = $imageId
        artifacts = $artifactHashes
    }
    $record | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath (Join-Path $script:evidenceRoot 'manifest.json') -Encoding utf8NoBOM
    Write-Host "VER-130-001 local gate passed. Evidence: $script:evidenceRoot"
}
finally {
    if ($stageLog -and (Test-Path -LiteralPath $stageLog)) { Remove-Item -LiteralPath $stageLog }
    Pop-Location
}
