[CmdletBinding(SupportsShouldProcess = $true)]
param(
    [string] $BuildLibs,
    [string[]] $ModsDir,
    [string] $TargetsFile,
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

function Get-OptionalStringProperty {
    param(
        [object] $InputObject,
        [string] $PropertyName
    )

    $property = $InputObject.PSObject.Properties[$PropertyName]
    if ($null -eq $property -or $null -eq $property.Value) {
        return $null
    }

    return [string] $property.Value
}

function Get-OptionalBoolProperty {
    param(
        [object] $InputObject,
        [string] $PropertyName,
        [bool] $DefaultValue
    )

    $property = $InputObject.PSObject.Properties[$PropertyName]
    if ($null -eq $property -or $null -eq $property.Value) {
        return $DefaultValue
    }

    return [bool] $property.Value
}

function Resolve-PathOrLiteral {
    param([string] $Path)

    if ([string]::IsNullOrWhiteSpace($Path)) {
        throw 'Deploy target path is empty.'
    }

    if ([System.IO.Path]::IsPathRooted($Path)) {
        return $Path
    }

    return (Resolve-Path -LiteralPath $Path).Path
}

function New-DeployTarget {
    param(
        [string] $Name,
        [string] $ModsDirectory
    )

    $resolvedModsDir = Resolve-PathOrLiteral -Path $ModsDirectory
    if ([string]::IsNullOrWhiteSpace($Name)) {
        $Name = $resolvedModsDir
    }

    return [pscustomobject]@{
        Name = $Name
        ModsDir = $resolvedModsDir
    }
}

function ConvertTo-DeployTarget {
    param(
        [object] $TargetSpec,
        [int] $Index
    )

    $name = Get-OptionalStringProperty -InputObject $TargetSpec -PropertyName 'name'
    if ([string]::IsNullOrWhiteSpace($name)) {
        $name = "target-$Index"
    }

    $kind = Get-OptionalStringProperty -InputObject $TargetSpec -PropertyName 'kind'
    if ([string]::IsNullOrWhiteSpace($kind)) {
        $kind = 'mods-dir'
    }

    $modsPath = Get-OptionalStringProperty -InputObject $TargetSpec -PropertyName 'modsDir'
    $rootPath = Get-OptionalStringProperty -InputObject $TargetSpec -PropertyName 'path'

    switch ($kind.ToLowerInvariant()) {
        'mods-dir' {
            if ([string]::IsNullOrWhiteSpace($modsPath)) {
                $modsPath = $rootPath
            }
        }
        'prism-instance' {
            if ([string]::IsNullOrWhiteSpace($rootPath)) {
                throw "Deploy target '$name' is missing path."
            }
            $modsPath = Join-Path $rootPath '.minecraft\mods'
        }
        'server-root' {
            if ([string]::IsNullOrWhiteSpace($rootPath)) {
                throw "Deploy target '$name' is missing path."
            }
            $modsPath = Join-Path $rootPath 'mods'
        }
        default {
            throw "Unsupported deploy target kind '$kind' for '$name'. Use mods-dir, prism-instance, or server-root."
        }
    }

    return New-DeployTarget -Name $name -ModsDirectory $modsPath
}

function Get-DefaultDeployTargetSpecs {
    return @(
        [pscustomobject]@{
            name = 'GTNH290test'
            kind = 'prism-instance'
            path = Join-Path $env:APPDATA 'PrismLauncher\instances\GTNH290test'
        },
        [pscustomobject]@{
            name = 'GTNH290test2'
            kind = 'prism-instance'
            path = Join-Path $env:APPDATA 'PrismLauncher\instances\GTNH290test2'
        },
        [pscustomobject]@{
            name = 'GTNH daily test server'
            kind = 'server-root'
            path = 'E:\GTNH Test Server\GTNH-daily-2026-06-02+549-server-java17-25'
        }
    )
}

function Resolve-TargetsFromFile {
    param([string] $Path)

    $resolvedTargetsFile = Resolve-PathOrLiteral -Path $Path
    if (-not (Test-Path -LiteralPath $resolvedTargetsFile -PathType Leaf)) {
        throw "Deploy targets file does not exist: $resolvedTargetsFile"
    }

    $config = Get-Content -LiteralPath $resolvedTargetsFile -Raw | ConvertFrom-Json
    if ($null -eq $config.targets) {
        throw "Deploy targets file must contain a targets array: $resolvedTargetsFile"
    }

    return @($config.targets)
}

function Resolve-DeployTargets {
    $targetSpecs = @()

    if ($null -ne $ModsDir -and $ModsDir.Count -gt 0) {
        $targets = @()
        for ($i = 0; $i -lt $ModsDir.Count; $i++) {
            $targets += New-DeployTarget -Name "mods-dir-$($i + 1)" -ModsDirectory $ModsDir[$i]
        }
        return $targets
    }

    if (-not [string]::IsNullOrWhiteSpace($TargetsFile)) {
        $targetSpecs = Resolve-TargetsFromFile -Path $TargetsFile
    } else {
        $targetSpecs = Get-DefaultDeployTargetSpecs
    }

    $targetsFromSpecs = @()
    for ($i = 0; $i -lt $targetSpecs.Count; $i++) {
        if (-not (Get-OptionalBoolProperty -InputObject $targetSpecs[$i] -PropertyName 'enabled' -DefaultValue $true)) {
            continue
        }
        $targetsFromSpecs += ConvertTo-DeployTarget -TargetSpec $targetSpecs[$i] -Index ($i + 1)
    }

    if ($targetsFromSpecs.Count -eq 0) {
        throw 'No deploy targets are enabled.'
    }

    return $targetsFromSpecs
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

function Deploy-JarToTarget {
    param(
        [System.IO.FileInfo] $Source,
        [object] $Target
    )

    if (-not (Test-Path -LiteralPath $Target.ModsDir -PathType Container)) {
        throw "Mods directory does not exist for '$($Target.Name)': $($Target.ModsDir)"
    }

    $oldJars = @(Get-ChildItem -LiteralPath $Target.ModsDir -Filter $JarPattern | Where-Object {
            -not $_.PSIsContainer
        })
    foreach ($oldJar in $oldJars) {
        if ($PSCmdlet.ShouldProcess($oldJar.FullName, 'Remove old Singularity ME jar')) {
            Remove-Item -LiteralPath $oldJar.FullName -Force
        }
    }

    $destination = Join-Path $Target.ModsDir $Source.Name
    $copied = $false
    if ($PSCmdlet.ShouldProcess($destination, "Copy $($Source.Name)")) {
        Copy-Item -LiteralPath $Source.FullName -Destination $destination -Force
        $copied = $true
    }

    if ($copied) {
        Write-DeployLog "Deployed $($Source.Name) -> [$($Target.Name)] $($Target.ModsDir)"
    } elseif ($WhatIfPreference) {
        Write-DeployLog "WhatIf: would deploy $($Source.Name) -> [$($Target.Name)] $($Target.ModsDir)"
    }
}

function Deploy-LatestJar {
    $source = Get-LatestReleaseJar
    Wait-ForStableFile -Path $source.FullName

    foreach ($target in $script:ResolvedTargets) {
        Deploy-JarToTarget -Source $source -Target $target
    }

    return $source
}

if ($Once.IsPresent -and $Watch.IsPresent) {
    throw 'Use either -Once or -Watch, not both.'
}

$runWatch = $Watch.IsPresent
$runOnce = $Once.IsPresent -or -not $runWatch
$script:ResolvedBuildLibs = Resolve-DefaultBuildLibs
$script:ResolvedTargets = Resolve-DeployTargets

if ($runOnce) {
    Deploy-LatestJar | Out-Null
    exit 0
}

Write-DeployLog "Watching $script:ResolvedBuildLibs"
foreach ($target in $script:ResolvedTargets) {
    Write-DeployLog "Target [$($target.Name)]: $($target.ModsDir)"
}
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
