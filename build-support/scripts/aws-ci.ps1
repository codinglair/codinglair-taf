[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [ValidateSet('unit-contract', 'localstack', 'mcp-contract-leak', 'consumer-smoke', 'full-reactor')]
    [string] $Mode
)

$ErrorActionPreference = 'Stop'

function Invoke-Maven {
    param([Parameter(ValueFromRemainingArguments = $true)][string[]] $Arguments)

    & .\mvnw.cmd -B -ntp @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "Maven gate failed with exit code $LASTEXITCODE."
    }
}

switch ($Mode) {
    'unit-contract' {
        Invoke-Maven -pl 'codinglair-taf-runtime/taf-messaging-aws' -am spotless:check verify
    }
    'localstack' {
        $localstackImage = if ($env:TAF_LOCALSTACK_IMAGE) { $env:TAF_LOCALSTACK_IMAGE } else { 'localstack/localstack:4.14.0' }
        Invoke-Maven -pl 'codinglair-taf-runtime/taf-messaging-aws' -am spotless:check verify -Pcontainers "-Dlocalstack.image=$localstackImage"
    }
    'mcp-contract-leak' {
        Invoke-Maven -pl 'taf-mcp-server/taf-mcp-contracts,taf-mcp-server/taf-mcp-resources,taf-mcp-server/taf-mcp-tools' -am verify '-Pmcp-gate,security'
    }
    'consumer-smoke' {
        Invoke-Maven clean deploy -Prelease-staging -DskipTests
        Invoke-Maven -N -Pconsumer-smoke verify
    }
    'full-reactor' {
        Invoke-Maven clean verify '-Pdependency-analysis,architecture,api-compatibility,schema-compatibility'
    }
}
