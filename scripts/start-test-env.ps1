[CmdletBinding(SupportsShouldProcess = $true)]
param(
    [string] $PrismExe,
    [string] $PrismDir,
    [string] $PrismInstance = 'GTNH290test',
    [string] $ServerRoot = $env:SINGULARITYME_SERVER_ROOT,
    [string] $ServerScript = 'startserver-java9.bat',
    [string] $ServerAddress,
    [string] $GradleTask = 'build',
    [int] $ClientDelaySeconds = 12,
    [switch] $DeployFirst,
    [switch] $SkipBuild,
    [switch] $SkipDeploy,
    [switch] $NoLaunch,
    [switch] $ServerOnly,
    [switch] $ClientOnly,
    [switch] $Quiet
)

$ErrorActionPreference = 'Stop'

function Write-StartLog {
    param([string] $Message)
    if (-not $Quiet) {
        Write-Host "[SingularityME start] $Message"
    }
}

function Resolve-ProjectRoot {
    return (Split-Path -Parent $PSScriptRoot)
}

function Invoke-BuildMod {
    $projectRoot = Resolve-ProjectRoot
    $gradleWrapper = Join-Path $projectRoot 'gradlew.bat'
    if (-not (Test-Path -LiteralPath $gradleWrapper -PathType Leaf)) {
        throw "Missing Gradle wrapper: $gradleWrapper"
    }

    if ([string]::IsNullOrWhiteSpace($env:GRADLE_USER_HOME)) {
        $env:GRADLE_USER_HOME = Join-Path $env:USERPROFILE '.gradle'
    }

    Write-StartLog "Building mod with Gradle task '$GradleTask'..."
    if ($PSCmdlet.ShouldProcess($projectRoot, "Run Gradle task $GradleTask")) {
        Push-Location $projectRoot
        try {
            & $gradleWrapper $GradleTask -x spotlessJavaCheck
            if ($LASTEXITCODE -ne 0) {
                throw "Gradle build failed with exit code $LASTEXITCODE."
            }
        } finally {
            Pop-Location
        }
    }
    Write-StartLog 'Build completed.'
}

function Resolve-ExistingDirectory {
    param(
        [string] $Path,
        [string] $Description
    )

    if ([string]::IsNullOrWhiteSpace($Path)) {
        throw "$Description path is empty."
    }

    $resolved = Resolve-Path -LiteralPath $Path -ErrorAction Stop
    if (-not (Test-Path -LiteralPath $resolved.Path -PathType Container)) {
        throw "$Description directory does not exist: $($resolved.Path)"
    }

    return $resolved.Path
}

function Get-DisplayIconPath {
    param([string] $DisplayIcon)

    if ([string]::IsNullOrWhiteSpace($DisplayIcon)) {
        return $null
    }

    return ($DisplayIcon -replace ',\d+$', '').Trim('"')
}

function Get-PrismRegistryCandidates {
    $registryPaths = @(
        'HKCU:\Software\Microsoft\Windows\CurrentVersion\Uninstall\*',
        'HKLM:\Software\Microsoft\Windows\CurrentVersion\Uninstall\*',
        'HKLM:\Software\WOW6432Node\Microsoft\Windows\CurrentVersion\Uninstall\*'
    )

    $candidates = @()
    foreach ($registryPath in $registryPaths) {
        $items = @(Get-ItemProperty -Path $registryPath -ErrorAction SilentlyContinue |
            Where-Object { $_.DisplayName -like '*Prism Launcher*' })
        foreach ($item in $items) {
            if (-not [string]::IsNullOrWhiteSpace($item.InstallLocation)) {
                $candidates += Join-Path $item.InstallLocation 'prismlauncher.exe'
                $candidates += Join-Path $item.InstallLocation 'PrismLauncher.exe'
            }

            $displayIconPath = Get-DisplayIconPath -DisplayIcon $item.DisplayIcon
            if (-not [string]::IsNullOrWhiteSpace($displayIconPath)) {
                $candidates += $displayIconPath
            }
        }
    }

    return $candidates
}

function Resolve-PrismExecutable {
    $pathCommands = @()
    $pathCommands += @(Get-Command 'prismlauncher.exe' -ErrorAction SilentlyContinue)
    $pathCommands += @(Get-Command 'prismlauncher' -ErrorAction SilentlyContinue)
    $pathCommands += @(Get-Command 'PrismLauncher.exe' -ErrorAction SilentlyContinue)
    $pathCommands = $pathCommands | Where-Object { $null -ne $_ } | ForEach-Object { $_.Source }

    $commonCandidates = @(
        $PrismExe,
        $env:SINGULARITYME_PRISM_EXE,
        $env:PRISMLAUNCHER_EXE,
        (Join-Path $env:LOCALAPPDATA 'Programs\PrismLauncher\prismlauncher.exe'),
        (Join-Path $env:LOCALAPPDATA 'Programs\PrismLauncher\PrismLauncher.exe'),
        (Join-Path $env:LOCALAPPDATA 'PrismLauncher\prismlauncher.exe'),
        (Join-Path $env:APPDATA 'PrismLauncher\prismlauncher.exe'),
        (Join-Path $env:ProgramFiles 'PrismLauncher\prismlauncher.exe'),
        (Join-Path $env:ProgramFiles 'PrismLauncher\PrismLauncher.exe'),
        (Join-Path ${env:ProgramFiles(x86)} 'PrismLauncher\prismlauncher.exe'),
        (Join-Path ${env:ProgramFiles(x86)} 'PrismLauncher\PrismLauncher.exe')
    )

    foreach ($candidate in @($commonCandidates + $pathCommands + (Get-PrismRegistryCandidates))) {
        if (-not [string]::IsNullOrWhiteSpace($candidate) -and
            (Test-Path -LiteralPath $candidate -PathType Leaf)) {
            return (Resolve-Path -LiteralPath $candidate).Path
        }
    }

    throw 'Could not find Prism Launcher executable. Pass -PrismExe or set SINGULARITYME_PRISM_EXE.'
}

function Resolve-PrismRoot {
    $candidates = @(
        $PrismDir,
        $env:SINGULARITYME_PRISM_DIR,
        (Join-Path $env:APPDATA 'PrismLauncher')
    )

    foreach ($candidate in $candidates) {
        if (-not [string]::IsNullOrWhiteSpace($candidate) -and
            (Test-Path -LiteralPath $candidate -PathType Container)) {
            return (Resolve-Path -LiteralPath $candidate).Path
        }
    }

    throw 'Could not find Prism Launcher data directory. Pass -PrismDir or set SINGULARITYME_PRISM_DIR.'
}

function Invoke-DeployOnce {
    $deployScript = Join-Path (Resolve-ProjectRoot) 'deploy-mod.bat'
    if (-not (Test-Path -LiteralPath $deployScript -PathType Leaf)) {
        throw "Missing deploy script: $deployScript"
    }

    Write-StartLog 'Deploying latest built mod jar...'
    if ($PSCmdlet.ShouldProcess('Singularity ME deploy targets', 'Deploy latest built mod jar')) {
        & $deployScript -Once
        if ($LASTEXITCODE -ne 0) {
            throw "Deploy failed with exit code $LASTEXITCODE."
        }
    }
    Write-StartLog 'Deploy completed.'
}

function Start-GtnhServer {
    if ([string]::IsNullOrWhiteSpace($ServerRoot)) {
        throw 'Server root is not set. Pass -ServerRoot or set SINGULARITYME_SERVER_ROOT, or use -ClientOnly to skip the server.'
    }
    $resolvedServerRoot = Resolve-ExistingDirectory -Path $ServerRoot -Description 'Server root'
    $serverScriptPath = Join-Path $resolvedServerRoot $ServerScript
    if (-not (Test-Path -LiteralPath $serverScriptPath -PathType Leaf)) {
        throw "Server start script does not exist: $serverScriptPath"
    }

    Write-StartLog "Server root: $resolvedServerRoot"
    if ($PSCmdlet.ShouldProcess($serverScriptPath, 'Start GTNH server in a new console window')) {
        Start-Process `
            -FilePath 'cmd.exe' `
            -ArgumentList @('/k', "`"$serverScriptPath`"") `
            -WorkingDirectory $resolvedServerRoot `
            -WindowStyle Normal | Out-Null
    }
}

function Start-GtnhClient {
    $resolvedPrismDir = Resolve-PrismRoot
    $instanceRoot = Join-Path $resolvedPrismDir (Join-Path 'instances' $PrismInstance)
    if (-not (Test-Path -LiteralPath $instanceRoot -PathType Container)) {
        throw "Prism instance does not exist: $instanceRoot"
    }

    $resolvedPrismExe = Resolve-PrismExecutable
    $args = @('--dir', $resolvedPrismDir, '--launch', $PrismInstance)
    if (-not [string]::IsNullOrWhiteSpace($ServerAddress)) {
        $args += @('--server', $ServerAddress)
    }

    Write-StartLog "Prism instance: $PrismInstance"
    Write-StartLog "Prism executable: $resolvedPrismExe"
    if ($PSCmdlet.ShouldProcess($PrismInstance, 'Start Prism Launcher instance')) {
        Start-Process `
            -FilePath $resolvedPrismExe `
            -ArgumentList $args `
            -WindowStyle Normal | Out-Null
    }
}

if ($ServerOnly.IsPresent -and $ClientOnly.IsPresent) {
    throw 'Use either -ServerOnly or -ClientOnly, not both.'
}

if (-not $SkipBuild.IsPresent) {
    Invoke-BuildMod
} else {
    Write-StartLog 'Skipping build.'
}

if (-not $SkipDeploy.IsPresent) {
    Invoke-DeployOnce
} else {
    Write-StartLog 'Skipping deploy.'
}

if ($NoLaunch.IsPresent) {
    Write-StartLog 'NoLaunch requested; build/deploy phase finished.'
    exit 0
}

if (-not $ClientOnly.IsPresent) {
    Start-GtnhServer
}

if (-not $ServerOnly.IsPresent) {
    if (-not $ClientOnly.IsPresent -and $ClientDelaySeconds -gt 0) {
        if ($WhatIfPreference) {
            Write-StartLog "WhatIf: would wait $ClientDelaySeconds second(s) before starting the client."
        } else {
            Write-StartLog "Waiting $ClientDelaySeconds second(s) before starting the client..."
            Start-Sleep -Seconds $ClientDelaySeconds
        }
    }

    Start-GtnhClient
}

Write-StartLog 'Launch commands issued.'
