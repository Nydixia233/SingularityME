[CmdletBinding(SupportsShouldProcess = $true)]
param(
    [string] $BuildLibs,
    [string] $ModsDir,
    [string] $JarPattern = 'singularityme-*.jar',
    [switch] $Once,
    [switch] $Watch,
    [int] $PollSeconds = 2,
    [int] $StableMilliseconds = 800,
    [int] $StableTimeoutSeconds = 30,
    [switch] $Quiet
)

$ErrorActionPreference = 'Stop'

function Write-DeployLog {
    param([string] $Message)
    if (-not $Quiet) {
        Write-Host "[SingularityME deploy] $Message"
    }
}

function Resolve-DefaultBuildLibs {
    if (-not [string]::IsNullOrWhiteSpace($BuildLibs)) {
        return (Resolve-Path -LiteralPath $BuildLibs).Path
    }

    $projectRoot = Split-Path -Parent $PSScriptRoot
    return (Join-Path $projectRoot 'build\libs')
}

function Get-LatestReleaseJar {
    param([switch] $AllowMissing)

    if (-not (Test-Path -LiteralPath $script:ResolvedBuildLibs -PathType Container)) {
        if ($AllowMissing) {
            return $null
        }
        throw "Build libs directory does not exist: $script:ResolvedBuildLibs"
    }

    $jars = @(
        Get-ChildItem -LiteralPath $script:ResolvedBuildLibs -Filter $JarPattern |
            Where-Object {
                -not $_.PSIsContainer -and
                $_.Name -notlike '*-dev.jar' -and
                $_.Name -notlike '*-sources.jar'
            } |
            Sort-Object LastWriteTimeUtc, Name -Descending
    )

    if ($jars.Count -eq 0) {
        if ($AllowMissing) {
            return $null
        }
        throw "No deployable jar found in $script:ResolvedBuildLibs matching $JarPattern"
    }

    return $jars[0]
}

function Wait-ForStableFile {
    param([string] $Path)

    $deadline = (Get-Date).AddSeconds($StableTimeoutSeconds)
    $lastLength = -1
    $lastWriteTicks = -1
    $stableSince = $null

    while ((Get-Date) -lt $deadline) {
        try {
            $item = Get-Item -LiteralPath $Path
            $stream = [System.IO.File]::Open($item.FullName, 'Open', 'Read', 'Read')
            $stream.Dispose()

            $length = $item.Length
            $writeTicks = $item.LastWriteTimeUtc.Ticks
            if ($length -eq $lastLength -and $writeTicks -eq $lastWriteTicks) {
                if ($null -eq $stableSince) {
                    $stableSince = Get-Date
                }
                if (((Get-Date) - $stableSince).TotalMilliseconds -ge $StableMilliseconds) {
                    return
                }
            } else {
                $lastLength = $length
                $lastWriteTicks = $writeTicks
                $stableSince = $null
            }
        } catch {
            $stableSince = $null
        }

        Start-Sleep -Milliseconds 200
    }

    throw "Jar did not become stable within $StableTimeoutSeconds seconds: $Path"
}

function Deploy-LatestJar {
    $source = Get-LatestReleaseJar
    Wait-ForStableFile -Path $source.FullName

    if (-not (Test-Path -LiteralPath $script:ResolvedModsDir -PathType Container)) {
        throw "Mods directory does not exist: $script:ResolvedModsDir"
    }

    $oldJars = @(Get-ChildItem -LiteralPath $script:ResolvedModsDir -Filter $JarPattern | Where-Object {
            -not $_.PSIsContainer
        })
    foreach ($oldJar in $oldJars) {
        if ($PSCmdlet.ShouldProcess($oldJar.FullName, 'Remove old Singularity ME jar')) {
            Remove-Item -LiteralPath $oldJar.FullName -Force
        }
    }

    $destination = Join-Path $script:ResolvedModsDir $source.Name
    $copied = $false
    if ($PSCmdlet.ShouldProcess($destination, "Copy $($source.Name)")) {
        Copy-Item -LiteralPath $source.FullName -Destination $destination -Force
        $copied = $true
    }

    if ($copied) {
        Write-DeployLog "Deployed $($source.Name) -> $script:ResolvedModsDir"
    } elseif ($WhatIfPreference) {
        Write-DeployLog "WhatIf: would deploy $($source.Name) -> $script:ResolvedModsDir"
    }
    return $source
}

if ($Once.IsPresent -and $Watch.IsPresent) {
    throw 'Use either -Once or -Watch, not both.'
}

$runWatch = $Watch.IsPresent
$runOnce = $Once.IsPresent -or -not $runWatch
$script:ResolvedBuildLibs = Resolve-DefaultBuildLibs

if ([string]::IsNullOrWhiteSpace($ModsDir)) {
    $defaultInstanceName = 'GTNH290' + [char] 0x914D + [char] 0x65B9
    $ModsDir = Join-Path $env:APPDATA "PrismLauncher\instances\$defaultInstanceName\.minecraft\mods"
}

$script:ResolvedModsDir = if ([System.IO.Path]::IsPathRooted($ModsDir)) {
    $ModsDir
} else {
    (Resolve-Path -LiteralPath $ModsDir).Path
}

if ($runOnce) {
    Deploy-LatestJar | Out-Null
    exit 0
}

Write-DeployLog "Watching $script:ResolvedBuildLibs"
Write-DeployLog "Target mods directory: $script:ResolvedModsDir"
Write-DeployLog 'Press Ctrl+C to stop.'

$lastSignature = ''
while ($true) {
    try {
        $jar = Get-LatestReleaseJar -AllowMissing
        if ($null -ne $jar) {
            $signature = "$($jar.FullName)|$($jar.LastWriteTimeUtc.Ticks)|$($jar.Length)"
            if ($signature -ne $lastSignature) {
                $deployed = Deploy-LatestJar
                $lastSignature = "$($deployed.FullName)|$($deployed.LastWriteTimeUtc.Ticks)|$($deployed.Length)"
            }
        }
    } catch {
        Write-Warning $_.Exception.Message
    }

    Start-Sleep -Seconds $PollSeconds
}
