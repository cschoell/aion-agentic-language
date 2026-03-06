#!/usr/bin/env pwsh
#
# run-qa-demo.ps1
#
# Builds the Aion language app (if needed) and runs the Q&A quiz
# interactively.
#
# Usage:
#   .\run-qa-demo.ps1                              # bytecode VM (default)
#   .\run-qa-demo.ps1 -Mode interpreter            # tree-walking interpreter
#   .\run-qa-demo.ps1 -File path\to\other.aion     # different .aion file
#
param(
    [string] $File = "aion-lang-app\src\main\resources\qa-demo.aion",
    [ValidateSet("bytecode","interpreter")]
    [string] $Mode = "bytecode"
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$ProjectRoot = $PSScriptRoot
$JAVA_EXE    = (Get-Command java -ErrorAction SilentlyContinue)?.Source
if (-not $JAVA_EXE) { $JAVA_EXE = "$env:JAVA_HOME\bin\java.exe" }
$LIB         = Join-Path $ProjectRoot "aion-lang-app\build\install\aion-lang-app\lib"
$CP          = "$LIB\aion-lang-app.jar;$LIB\antlr4-runtime-4.13.2.jar;$LIB\picocli-4.7.6.jar"
$AionFile    = if ([System.IO.Path]::IsPathRooted($File)) { $File } `
               else { Join-Path $ProjectRoot $File }

# ── 1. Resolve the .aion file ─────────────────────────────────────────────────
if (-not (Test-Path $AionFile)) {
    Write-Error "Cannot find .aion file: $AionFile"
    exit 1
}

# ── 2. Build / install if launcher is missing or stale ────────────────────────
$needsBuild = -not (Test-Path $LIB)
if (-not $needsBuild) {
    $srcRoot     = Join-Path $ProjectRoot "aion-lang-app\src"
    $sourceMTime = (Get-ChildItem -Recurse -File $srcRoot |
                    Sort-Object LastWriteTime -Descending |
                    Select-Object -First 1).LastWriteTime
    $jarMTime    = (Get-Item "$LIB\aion-lang-app.jar").LastWriteTime
    $needsBuild  = $sourceMTime -gt $jarMTime
}

if ($needsBuild) {
    Write-Host ">>> Building Aion (installDist) ..." -ForegroundColor Cyan
    $buildLog = Join-Path $env:TEMP "aion_build_qa.txt"
    & "$ProjectRoot\gradlew.bat" ":aion-lang-app:installDist" --no-configuration-cache `
        > $buildLog 2>&1
    if ($LASTEXITCODE -ne 0) {
        Write-Host (Get-Content $buildLog | Select-Object -Last 40) -ForegroundColor Red
        exit $LASTEXITCODE
    }
    Write-Host ">>> Build successful." -ForegroundColor Green
} else {
    Write-Host ">>> Launcher is up-to-date, skipping build." -ForegroundColor DarkGray
}

# ── 3. Run interactively ──────────────────────────────────────────────────────
$modeLabel = if ($Mode -eq "interpreter") { "Tree-walking Interpreter" } else { "Bytecode VM" }

Write-Host ""
Write-Host "+======================================+" -ForegroundColor Yellow
Write-Host "|          Aion Q&A Demo               |" -ForegroundColor Yellow
Write-Host "|  Backend : $($modeLabel.PadRight(27))|" -ForegroundColor Yellow
Write-Host "+======================================+" -ForegroundColor Yellow
Write-Host "(file: $AionFile)" -ForegroundColor DarkGray
Write-Host ""

# 'run'     → tree-walking interpreter (calls main fn directly)
# 'compile' → bytecode compiler + VM
$subcommand = if ($Mode -eq "interpreter") { "run" } else { "compile" }

& $JAVA_EXE -classpath $CP com.aion.cli.AionCli $subcommand $AionFile
exit $LASTEXITCODE
