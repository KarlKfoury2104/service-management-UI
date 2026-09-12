$ErrorActionPreference = "Stop"

function Find-Java21Home {
    $candidates = @()

    # Prefer an existing JAVA_HOME when it points to a full JDK 21 installation.
    if ($env:JAVA_HOME) {
        $candidates += $env:JAVA_HOME
    }

    # Try Java/Javac already available on PATH.
    foreach ($commandName in @("javac", "java")) {
        $command = Get-Command $commandName -ErrorAction SilentlyContinue
        if ($command -and $command.Source) {
            $binDir = Split-Path $command.Source -Parent
            $candidates += (Split-Path $binDir -Parent)
        }
    }

    # Search common Windows JDK installation locations. This avoids depending on
    # a specific patch-version folder such as jdk-21.0.11.10-hotspot.
    $searchRoots = @(
        (Join-Path $env:ProgramFiles "Microsoft"),
        (Join-Path $env:ProgramFiles "Eclipse Adoptium"),
        (Join-Path $env:ProgramFiles "Java"),
        (Join-Path $env:LOCALAPPDATA "Programs\Eclipse Adoptium")
    )

    foreach ($searchRoot in $searchRoots) {
        if ($searchRoot -and (Test-Path $searchRoot)) {
            $jdkDirs = Get-ChildItem -Path $searchRoot -Directory -ErrorAction SilentlyContinue |
                Where-Object { $_.Name -like "jdk-21*" } |
                Sort-Object Name -Descending

            foreach ($jdkDir in $jdkDirs) {
                $candidates += $jdkDir.FullName
            }
        }
    }

    foreach ($candidate in ($candidates | Where-Object { $_ } | Select-Object -Unique)) {
        $javaExe = Join-Path $candidate "bin\java.exe"
        $javacExe = Join-Path $candidate "bin\javac.exe"

        if ((Test-Path $javaExe) -and (Test-Path $javacExe)) {
            $versionLine = (& $javacExe -version 2>&1 | Select-Object -First 1).ToString()
            if ($versionLine -match '^javac\s+21(?:\.|$)') {
                return (Resolve-Path $candidate).Path
            }
        }
    }

    return $null
}

$javaPath = Find-Java21Home
if (-not $javaPath) {
    Write-Error "Java 21 JDK was not found. Run .\setup.ps1 first or install JDK 21."
}

Write-Host "Using Java 21 JDK at: $javaPath"
$env:JAVA_HOME = $javaPath
$env:PATH = "$javaPath\bin;" + $env:PATH

$mavenDir = Join-Path $PSScriptRoot "maven"

Write-Host "Starting project compilation..."
& "$mavenDir\bin\mvn.cmd" clean compile
Write-Host "Compilation complete!"
