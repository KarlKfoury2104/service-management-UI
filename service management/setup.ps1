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
    Write-Host "Java 21 JDK was not found. Attempting to install Microsoft OpenJDK 21 via winget..."

    if (-not (Get-Command winget -ErrorAction SilentlyContinue)) {
        Write-Error "Java 21 JDK is required, and winget is unavailable. Install JDK 21 manually, then run setup.ps1 again."
    }

    winget install Microsoft.OpenJDK.21 --silent --accept-package-agreements --accept-source-agreements

    # Refresh PATH in this PowerShell session so a newly installed JDK can be found.
    $machinePath = [Environment]::GetEnvironmentVariable("Path", "Machine")
    $userPath = [Environment]::GetEnvironmentVariable("Path", "User")
    $env:PATH = "$machinePath;$userPath"

    $javaPath = Find-Java21Home
    if (-not $javaPath) {
        Write-Error "Java 21 was installed or already present, but its JDK directory could not be located. Restart PowerShell and run setup.ps1 again."
    }
}

Write-Host "Using Java 21 JDK at: $javaPath"
$env:JAVA_HOME = $javaPath
$env:PATH = "$javaPath\bin;" + $env:PATH

$mavenZip = Join-Path $PSScriptRoot "apache-maven-3.9.6-bin.zip"
$mavenDir = Join-Path $PSScriptRoot "maven"

if (-not (Test-Path $mavenDir)) {
    if (-not (Test-Path $mavenZip)) {
        Write-Host "Downloading Apache Maven 3.9.6..."
        $url = "https://archive.apache.org/dist/maven/maven-3/3.9.6/binaries/apache-maven-3.9.6-bin.zip"
        Invoke-WebRequest -Uri $url -OutFile $mavenZip
    }

    Write-Host "Extracting Maven..."
    Expand-Archive -Path $mavenZip -DestinationPath $PSScriptRoot
    Rename-Item -Path (Join-Path $PSScriptRoot "apache-maven-3.9.6") -NewName "maven"

    Write-Host "Removing downloaded zip..."
    Remove-Item -Path $mavenZip
}

Write-Host "Checking environment..."
& "$mavenDir\bin\mvn.cmd" -version

Write-Host "Setup complete!"
