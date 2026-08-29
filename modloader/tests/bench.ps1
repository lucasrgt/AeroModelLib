$ErrorActionPreference = "Stop"

$Here = Split-Path -Parent $MyInvocation.MyCommand.Path
$Lib = Resolve-Path (Join-Path $Here "../..")
$Core = Join-Path $Lib "core"
$Tests = Join-Path $Here "aero/modellib"
$Out = Join-Path $Here "bench-out"

Write-Host "=== Compiling core benchmark ==="
if (Test-Path $Out) { Remove-Item -Recurse -Force $Out }
New-Item -ItemType Directory -Force $Out | Out-Null

$CoreFiles = @(Get-ChildItem $Core -Recurse -Filter *.java | ForEach-Object { $_.FullName })
$SupportFiles = @((Join-Path $Tests "Aero_AnimationState.java"))
$BenchmarkFiles = @(Get-ChildItem $Tests -Recurse -Filter *Benchmark*.java | ForEach-Object { $_.FullName })

$CompileArgs = @(
    "-source", "1.8",
    "-target", "1.8",
    "-d", $Out
) + $CoreFiles + $SupportFiles + $BenchmarkFiles

& javac $CompileArgs
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

$Benchmarks = @(
    "aero.modellib.CoreBenchmark",
    "aero.modellib.animation.AnimationOptimizationBenchmark",
    "aero.modellib.model.ModelOptimizationBenchmark",
    "aero.modellib.RuntimeOptimizationBenchmark"
)

foreach ($Benchmark in $Benchmarks) {
    Write-Host "=== Running $Benchmark ==="
    & java @("-Xms1G", "-Xmx1G", "-XX:+UseG1GC", "-cp", $Out, $Benchmark)
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
}

exit 0
