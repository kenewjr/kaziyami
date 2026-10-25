param(
    [string]$Source,
    [switch]$Mock
)
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$cmdArgs = @()
if ($Source) { $cmdArgs += "--source", $Source }
if ($Mock) { $cmdArgs += "--mock" }
python "$scriptDir\install_debug.py" @cmdArgs
