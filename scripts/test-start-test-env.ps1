$ErrorActionPreference = 'Stop'

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$startScript = Join-Path $scriptDir 'start-test-env.ps1'
if (-not (Test-Path -LiteralPath $startScript -PathType Leaf)) {
    throw "Missing start script: $startScript"
}

$tempRoot = Join-Path ([System.IO.Path]::GetTempPath()) ("singularityme-start-test-" + [guid]::NewGuid())
$prismDir = Join-Path $tempRoot 'PrismLauncher'
$instanceDir = Join-Path $prismDir 'instances\GTNH290test'
$serverRoot = Join-Path $tempRoot 'ServerRoot'
$prismExe = Join-Path $tempRoot 'prismlauncher.exe'
$serverScript = Join-Path $serverRoot 'startserver-java9.bat'

try {
    New-Item -ItemType Directory -Force -Path $instanceDir, $serverRoot | Out-Null
    Set-Content -LiteralPath $prismExe -Value 'fake prism executable'
    Set-Content -LiteralPath $serverScript -Value '@echo off'

    & $startScript `
        -PrismExe $prismExe `
        -PrismDir $prismDir `
        -ServerRoot $serverRoot `
        -ClientDelaySeconds 0 `
        -SkipBuild `
        -SkipDeploy `
        -WhatIf `
        -Quiet

    & $startScript `
        -PrismExe $prismExe `
        -PrismDir $prismDir `
        -ServerRoot $serverRoot `
        -ServerOnly `
        -SkipBuild `
        -SkipDeploy `
        -WhatIf `
        -Quiet

    & $startScript `
        -PrismExe $prismExe `
        -PrismDir $prismDir `
        -ServerRoot $serverRoot `
        -ClientOnly `
        -SkipBuild `
        -SkipDeploy `
        -WhatIf `
        -Quiet

    Write-Host 'start-test-env test passed'
} finally {
    if (Test-Path -LiteralPath $tempRoot) {
        Remove-Item -LiteralPath $tempRoot -Recurse -Force
    }
}
