param(
    [switch]$Force,
    [switch]$Quiet
)

$ErrorActionPreference = "Stop"
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$versionFile = Join-Path $scriptDir "moonwide-version.txt"
$repoOwner = "MooNJIKEY"
$repoName = "MooNWide"
$branch = "main"
$restartCode = 42
$cacheBust = [DateTimeOffset]::UtcNow.ToUnixTimeSeconds()
$headers = @{
    "User-Agent" = "MooNWide-Updater"
    "Cache-Control" = "no-cache"
}
$remoteVersionUrl = "https://raw.githubusercontent.com/$repoOwner/$repoName/$branch/moonwide-version.txt?ts=$cacheBust"
$remoteZipUrl = "https://codeload.github.com/$repoOwner/$repoName/zip/refs/heads/$branch"
$tempRoot = Join-Path ([IO.Path]::GetTempPath()) ("moonwide-update-" + [Guid]::NewGuid().ToString("N"))
$zipPath = Join-Path $tempRoot "moonwide-update.zip"
$extractDir = Join-Path $tempRoot "extract"
$allowedTopLevelDirs = @("doc", "jre")
$blockedTopLevelDirs = @("hafen-client-new", ".git", ".github")

function Write-Status {
    param([string]$Message)
    if(-not $Quiet) {
        Write-Host $Message
    }
}

function Test-AllowedPath {
    param([string]$RelativePath)

    if([string]::IsNullOrWhiteSpace($RelativePath)) {
        return $false
    }

    $first = ($RelativePath -split '[\\/]', 2)[0]
    if([string]::IsNullOrWhiteSpace($first)) {
        return $false
    }
    if($blockedTopLevelDirs -contains $first) {
        return $false
    }
    if($allowedTopLevelDirs -contains $first) {
        return $true
    }

    switch -Regex ($first) {
        '^COPYING$' { return $true }
        '^java-hosts-moonwide$' { return $true }
        '^haven-config\.properties$' { return $true }
        '^login-splash\.png$' { return $true }
        '^moonpackethook\.dll$' { return $true }
        '^steam_appid\.txt$' { return $true }
        '^.+\.(jar|dll|md|bat|ps1|txt)$' { return $true }
        default { return $false }
    }
}

function Copy-PackageContents {
    param(
        [string]$SourceRoot,
        [string]$DestinationRoot
    )

    Get-ChildItem -LiteralPath $SourceRoot -Recurse -Force | ForEach-Object {
        $relative = $_.FullName.Substring($SourceRoot.Length).TrimStart("\")
        if(-not (Test-AllowedPath $relative)) {
            return
        }

        $destination = Join-Path $DestinationRoot $relative
        if($_.PSIsContainer) {
            if(-not (Test-Path -LiteralPath $destination)) {
                New-Item -ItemType Directory -Path $destination -Force | Out-Null
            }
        } else {
            $parent = Split-Path -Parent $destination
            if(-not (Test-Path -LiteralPath $parent)) {
                New-Item -ItemType Directory -Path $parent -Force | Out-Null
            }
            Copy-Item -LiteralPath $_.FullName -Destination $destination -Force
        }
    }
}

try {
    Set-Location $scriptDir
    Write-Status "[MooNWide] Checking GitHub for updates..."
    $remoteVersion = (Invoke-WebRequest -UseBasicParsing -Headers $headers -Uri $remoteVersionUrl -TimeoutSec 15).Content.Trim()
    if([string]::IsNullOrWhiteSpace($remoteVersion)) {
        throw "Remote version file is empty."
    }

    $localVersion = ""
    if(Test-Path -LiteralPath $versionFile) {
        $localVersion = (Get-Content -LiteralPath $versionFile -Raw).Trim()
    }

    if((-not $Force) -and ($localVersion -eq $remoteVersion)) {
        Write-Status "[MooNWide] Up to date: $localVersion"
        exit 0
    }

    if([string]::IsNullOrWhiteSpace($localVersion)) {
        Write-Status "[MooNWide] No local version marker found. Downloading current package..."
    } else {
        Write-Status "[MooNWide] Update found: $localVersion -> $remoteVersion"
    }

    New-Item -ItemType Directory -Path $extractDir -Force | Out-Null
    Invoke-WebRequest -UseBasicParsing -Headers $headers -Uri $remoteZipUrl -OutFile $zipPath -TimeoutSec 120
    Expand-Archive -LiteralPath $zipPath -DestinationPath $extractDir -Force

    $packageRoot = Get-ChildItem -LiteralPath $extractDir -Directory | Select-Object -First 1
    if($null -eq $packageRoot) {
        throw "Downloaded archive does not contain a package directory."
    }

    Copy-PackageContents -SourceRoot $packageRoot.FullName -DestinationRoot $scriptDir
    Write-Status "[MooNWide] Update installed. Restarting launcher..."
    exit $restartCode
} catch {
    Write-Warning "[MooNWide] Update check failed: $($_.Exception.Message)"
    exit 0
} finally {
    if(Test-Path -LiteralPath $tempRoot) {
        Remove-Item -LiteralPath $tempRoot -Recurse -Force -ErrorAction SilentlyContinue
    }
}
