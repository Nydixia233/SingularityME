$ErrorActionPreference = 'Stop'

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$deployScript = Join-Path $scriptDir 'deploy-built-mod.ps1'
if (-not (Test-Path -LiteralPath $deployScript)) {
    throw "Missing deploy script: $deployScript"
}

$tempRoot = Join-Path ([System.IO.Path]::GetTempPath()) ("singularityme-deploy-test-" + [guid]::NewGuid())
$buildLibs = Join-Path $tempRoot 'build\libs'
$modsDir = Join-Path $tempRoot 'mods'
$prismInstanceRoot = Join-Path $tempRoot 'PrismInstance'
$prismModsDir = Join-Path $prismInstanceRoot '.minecraft\mods'
$serverRoot = Join-Path $tempRoot 'ServerRoot'
$serverModsDir = Join-Path $serverRoot 'mods'
$targetsFile = Join-Path $tempRoot 'deploy-targets.local.json'

try {
    New-Item -ItemType Directory -Force -Path $buildLibs, $modsDir, $prismModsDir, $serverModsDir | Out-Null

    Set-Content -LiteralPath (Join-Path $modsDir 'singularityme-old.jar') -Value 'old'
    Set-Content -LiteralPath (Join-Path $modsDir 'othermod.jar') -Value 'other'
    Set-Content -LiteralPath (Join-Path $prismModsDir 'singularityme-old-client.jar') -Value 'old-client'
    Set-Content -LiteralPath (Join-Path $prismModsDir 'othermod.jar') -Value 'other-client'
    Set-Content -LiteralPath (Join-Path $serverModsDir 'singularityme-old-server.jar') -Value 'old-server'
    Set-Content -LiteralPath (Join-Path $serverModsDir 'othermod.jar') -Value 'other-server'

    $oldBuild = Join-Path $buildLibs 'singularityme-old-build.jar'
    $newBuild = Join-Path $buildLibs 'singularityme-new-build.jar'
    $devBuild = Join-Path $buildLibs 'singularityme-new-build-dev.jar'
    $sourcesBuild = Join-Path $buildLibs 'singularityme-new-build-sources.jar'

    Set-Content -LiteralPath $oldBuild -Value 'old-build'
    Set-Content -LiteralPath $newBuild -Value 'new-build'
    Set-Content -LiteralPath $devBuild -Value 'dev-build'
    Set-Content -LiteralPath $sourcesBuild -Value 'sources-build'

    (Get-Item -LiteralPath $oldBuild).LastWriteTime = [datetime]'2026-05-27T10:00:00'
    (Get-Item -LiteralPath $newBuild).LastWriteTime = [datetime]'2026-05-27T11:00:00'
    (Get-Item -LiteralPath $devBuild).LastWriteTime = [datetime]'2026-05-27T12:00:00'
    (Get-Item -LiteralPath $sourcesBuild).LastWriteTime = [datetime]'2026-05-27T13:00:00'

    & $deployScript -Once -BuildLibs $buildLibs -ModsDir $modsDir -Quiet

    $singularityJars = @(Get-ChildItem -LiteralPath $modsDir -Filter 'singularityme-*.jar')
    if ($singularityJars.Count -ne 1) {
        throw "Expected exactly one deployed Singularity ME jar, found $($singularityJars.Count)"
    }
    if ($singularityJars[0].Name -ne 'singularityme-new-build.jar') {
        throw "Expected latest release jar to be deployed, found $($singularityJars[0].Name)"
    }
    if ((Get-Content -Raw -LiteralPath $singularityJars[0].FullName).Trim() -ne 'new-build') {
        throw "Deployed jar content did not match the selected build jar"
    }
    if (-not (Test-Path -LiteralPath (Join-Path $modsDir 'othermod.jar'))) {
        throw 'Unrelated mods should not be removed'
    }

    $targetsConfig = [pscustomobject]@{
        targets = @(
            [pscustomobject]@{
                name = 'client-test'
                kind = 'prism-instance'
                path = $prismInstanceRoot
            },
            [pscustomobject]@{
                name = 'server-test'
                kind = 'server-root'
                path = $serverRoot
            }
        )
    }
    $targetsConfig | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath $targetsFile -Encoding UTF8

    & $deployScript -Once -BuildLibs $buildLibs -TargetsFile $targetsFile -Quiet

    foreach ($targetModsDir in @($prismModsDir, $serverModsDir)) {
        $targetJars = @(Get-ChildItem -LiteralPath $targetModsDir -Filter 'singularityme-*.jar')
        if ($targetJars.Count -ne 1) {
            throw "Expected exactly one deployed Singularity ME jar in $targetModsDir, found $($targetJars.Count)"
        }
        if ($targetJars[0].Name -ne 'singularityme-new-build.jar') {
            throw "Expected latest release jar in $targetModsDir, found $($targetJars[0].Name)"
        }
        if ((Get-Content -Raw -LiteralPath $targetJars[0].FullName).Trim() -ne 'new-build') {
            throw "Deployed jar content did not match the selected build jar in $targetModsDir"
        }
        if (-not (Test-Path -LiteralPath (Join-Path $targetModsDir 'othermod.jar'))) {
            throw "Unrelated mods should not be removed from $targetModsDir"
        }
    }

    Write-Host 'deploy-built-mod test passed'
} finally {
    if (Test-Path -LiteralPath $tempRoot) {
        Remove-Item -LiteralPath $tempRoot -Recurse -Force
    }
}
