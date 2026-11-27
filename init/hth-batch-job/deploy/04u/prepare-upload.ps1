param([switch]$JarsOnly)

$ErrorActionPreference = 'Stop'
$batchRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '../..')).Path
$kind = if ($JarsOnly) { 'jars' } else { 'full' }
$timestamp = Get-Date -Format 'yyyyMMdd-HHmmss-fff'
$outputDir = Join-Path $batchRoot "target/04u-upload/$kind-$timestamp"
$manifest = Join-Path $PSScriptRoot 'upload-files.txt'
$utf8 = [Text.UTF8Encoding]::new($false)

$checksums = foreach ($line in Get-Content -LiteralPath $manifest) {
    if ([string]::IsNullOrWhiteSpace($line) -or $line.StartsWith('#')) {
        continue
    }

    $source, $destination = ($line -split '\|', 3)[0, 1]
    if ($JarsOnly -and -not $destination.EndsWith('.jar')) {
        continue
    }

    $inputFile = Join-Path $batchRoot $source
    $outputFile = Join-Path $outputDir $destination
    if (-not (Test-Path -LiteralPath $inputFile -PathType Leaf)) {
        throw "Missing $inputFile. Run Maven verify or install before preparing the upload."
    }
    [IO.Directory]::CreateDirectory((Split-Path -Parent $outputFile)) | Out-Null
    if ($destination.EndsWith('.sh') -or $destination.EndsWith('.awk')) {
        $content = [IO.File]::ReadAllText($inputFile).Replace("`r`n", "`n")
        [IO.File]::WriteAllText($outputFile, $content, $utf8)
    } else {
        Copy-Item -LiteralPath $inputFile -Destination $outputFile
    }
    $hash = (Get-FileHash -Algorithm SHA256 -LiteralPath $outputFile).Hash.ToLowerInvariant()
    "$hash  $destination"
}

$checksumFile = Join-Path $outputDir 'SHA256SUMS'
[IO.File]::WriteAllText($checksumFile, ($checksums -join "`n") + "`n", [Text.Encoding]::ASCII)
Write-Host "04u upload directory: $outputDir"
