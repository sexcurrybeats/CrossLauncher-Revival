param(
    [Parameter(Mandatory = $true)]
    [string]$Destination
)

$ErrorActionPreference = "Stop"

$sourceRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$destinationRoot = [System.IO.Path]::GetFullPath($Destination)

$includeFiles = @(
    ".gitattributes",
    ".gitignore",
    ".gitmodules",
    "LICENSE",
    "README.md",
    "CHANGELOG.md",
    "ISSUE_REPORTING.md",
    "RELEASE_NOTES_0.12.0-alpha1.md",
    "RELEASE_CHECKLIST.md",
    "ASSET_AUDIT.md",
    "XTF_COMPATIBILITY.md",
    "RELEASE_ASSET_PACKS.md",
    "build.gradle.kts",
    "settings.gradle.kts",
    "gradle.properties",
    "gradlew",
    "gradlew.bat"
)

$includeDirectories = @(
    ".github",
    "buildSrc",
    "customization_example",
    "extensions",
    "gradle",
    "launcher_app",
    "launcher_xlib",
    "readme_asset",
    "tools"
)

$excludeRootDirectories = @(
    ".git",
    ".gradle",
    ".idea",
    "release_snapshots",
    "release_artifacts",
    "rollback_backups",
    "verification_shots",
    "other_asset"
)

$excludeRootFiles = @(
    "keystore.properties",
    "release-keystore.jks",
    "local.properties"
)

function Ensure-Directory([string]$path) {
    if (-not (Test-Path $path)) {
        New-Item -ItemType Directory -Path $path | Out-Null
    }
}

function Copy-FilePreserveRelative([string]$relativePath) {
    $src = Join-Path $sourceRoot $relativePath
    if (-not (Test-Path $src)) {
        Write-Warning "Missing file, skipped: $relativePath"
        return
    }

    $dst = Join-Path $destinationRoot $relativePath
    $dstParent = Split-Path -Parent $dst
    Ensure-Directory $dstParent
    Copy-Item $src $dst -Force
}

function Copy-DirectoryPreserveRelative([string]$relativePath) {
    $src = Join-Path $sourceRoot $relativePath
    if (-not (Test-Path $src)) {
        Write-Warning "Missing directory, skipped: $relativePath"
        return
    }

    $dst = Join-Path $destinationRoot $relativePath
    Ensure-Directory $dst
    robocopy $src $dst /E /NFL /NDL /NJH /NJS /NP /XD ".git" ".gradle" ".idea" "build" ".cxx" | Out-Null
    $code = $LASTEXITCODE
    if ($code -ge 8) {
        throw "robocopy failed for $relativePath with exit code $code"
    }
}

Ensure-Directory $destinationRoot

foreach ($dir in $includeDirectories) {
    Copy-DirectoryPreserveRelative $dir
}

foreach ($file in $includeFiles) {
    Copy-FilePreserveRelative $file
}

foreach ($dir in $excludeRootDirectories) {
    $target = Join-Path $destinationRoot $dir
    if (Test-Path $target) {
        Remove-Item $target -Recurse -Force
    }
}

foreach ($file in $excludeRootFiles) {
    $target = Join-Path $destinationRoot $file
    if (Test-Path $target) {
        Remove-Item $target -Force
    }
}

Get-ChildItem -Path $destinationRoot -Filter "qa_*.png" -File -Recurse | Remove-Item -Force

Write-Host "Public repo export completed:"
Write-Host "  Source      : $sourceRoot"
Write-Host "  Destination : $destinationRoot"
