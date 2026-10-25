# scripts/collect-diagnostics.ps1
$ErrorActionPreference = "Stop"

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Definition
$repoRoot = Split-Path -Parent $scriptDir

python "$repoRoot\scripts\collect_diagnostics.py" @args
