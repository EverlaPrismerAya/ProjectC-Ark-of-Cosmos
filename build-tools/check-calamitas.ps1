$ErrorActionPreference = 'Stop'
$workspace = Split-Path $PSScriptRoot -Parent
$output = Join-Path $workspace 'build/calamitas-check'
New-Item -ItemType Directory -Force -Path $output | Out-Null
$sources = @('SupremeCalamitasBarrage', 'SupremeCalamitasEncounter') | ForEach-Object {
    Join-Path $workspace "src/main/java/net/everla/projectc/arkofcosmos/calamity/$_.java"
}
$checks = @('CalamitasBarrageCheck', 'CalamitasEncounterCheck') | ForEach-Object {
    Join-Path $PSScriptRoot "checks/$_.java"
}
& javac -encoding UTF-8 -d $output $sources $checks
if ($LASTEXITCODE -ne 0) { throw 'Calamitas check compilation failed.' }
& java -cp $output CalamitasBarrageCheck
if ($LASTEXITCODE -ne 0) { throw 'Calamitas source-clock check failed.' }
& java -cp $output CalamitasEncounterCheck
if ($LASTEXITCODE -ne 0) { throw 'Calamitas encounter check failed.' }
