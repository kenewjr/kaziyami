param()
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
python "$scriptDir\preflight.py" @args
