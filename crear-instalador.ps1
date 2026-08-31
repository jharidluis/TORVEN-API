param(
    [string]$Version
)

$ErrorActionPreference = "Stop"

$projectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$jdkHome = "C:\Program Files\Apache NetBeans\jdk"
$maven = "C:\Program Files\Apache NetBeans\java\maven\bin\mvn.cmd"
$jpackage = Join-Path $jdkHome "bin\jpackage.exe"
$localRepository = Join-Path $projectRoot ".m2\repository"
$pom = [xml](Get-Content -LiteralPath (Join-Path $projectRoot "pom.xml") -Raw)
$projectVersion = $pom.project.version
if ([string]::IsNullOrWhiteSpace($Version)) {
    $Version = $projectVersion
}
$jarName = "sistema-venta-tienda-$projectVersion-all.jar"
$jarPath = Join-Path $projectRoot "target\$jarName"
$inputDir = Join-Path $projectRoot "packaging\input-$Version"
$portableDir = Join-Path $projectRoot "salida\portable-$Version"
$installerDir = Join-Path $projectRoot "salida\instalador"
$installerPath = Join-Path $installerDir "Torven Sistema de Ventas-$Version.exe"
$portableTemp = Join-Path $projectRoot "packaging\temp-app-image-$Version"
$installerTemp = Join-Path $projectRoot "packaging\temp-installer-$Version"
$wixZip = Join-Path $projectRoot "packaging\wix314-binaries.zip"
$wixDir = Join-Path $projectRoot "packaging\wix314"
$wixUrl = "https://github.com/wixtoolset/wix3/releases/download/wix3141rtm/wix314-binaries.zip"

function Remove-ProjectDirectory {
    param([Parameter(Mandatory = $true)][string]$Path)

    $root = [System.IO.Path]::GetFullPath($projectRoot).TrimEnd('\') + '\'
    $resolved = [System.IO.Path]::GetFullPath($Path)
    if (-not $resolved.StartsWith($root, [System.StringComparison]::OrdinalIgnoreCase)) {
        throw "Se rechazo borrar una ruta fuera del proyecto: $resolved"
    }
    if (Test-Path -LiteralPath $resolved) {
        Remove-Item -LiteralPath $resolved -Recurse -Force
    }
}

if (-not (Test-Path -LiteralPath $jpackage)) {
    throw "No se encontro jpackage en $jpackage. Instala un JDK 17 o posterior, o ajusta jdkHome en este script."
}
if (-not (Test-Path -LiteralPath $maven)) {
    throw "No se encontro Maven en $maven. Ajusta la ruta de Maven en este script."
}

New-Item -ItemType Directory -Path $localRepository -Force | Out-Null
$env:JAVA_HOME = $jdkHome

Write-Host "Compilando Torven Sistema de Ventas..."
& $maven "-Dmaven.repo.local=$localRepository" package
if ($LASTEXITCODE -ne 0) {
    throw "Maven no pudo compilar el proyecto."
}
if (-not (Test-Path -LiteralPath $jarPath)) {
    throw "No se genero el archivo esperado: $jarPath"
}

Remove-ProjectDirectory -Path $inputDir
Remove-ProjectDirectory -Path $portableDir
Remove-ProjectDirectory -Path $portableTemp
Remove-ProjectDirectory -Path $installerTemp

$rootPrefix = [System.IO.Path]::GetFullPath($projectRoot).TrimEnd('\') + '\'
$installerFullPath = [System.IO.Path]::GetFullPath($installerPath)
if (-not $installerFullPath.StartsWith($rootPrefix, [System.StringComparison]::OrdinalIgnoreCase)) {
    throw "Se rechazo borrar un instalador fuera del proyecto: $installerFullPath"
}
if (Test-Path -LiteralPath $installerFullPath) {
    Remove-Item -LiteralPath $installerFullPath -Force
}

New-Item -ItemType Directory -Path $inputDir -Force | Out-Null
New-Item -ItemType Directory -Path (Join-Path $inputDir "vouchers") -Force | Out-Null
Copy-Item -LiteralPath $jarPath -Destination $inputDir -Force
Copy-Item -LiteralPath (Join-Path $projectRoot "config") -Destination $inputDir -Recurse -Force
Copy-Item -LiteralPath (Join-Path $projectRoot "assets") -Destination $inputDir -Recurse -Force

$commonArguments = @(
    "--name", "Torven Sistema de Ventas",
    "--app-version", $Version,
    "--vendor", "Torven",
    "--description", "Sistema de gestion de ventas Torven",
    "--input", $inputDir,
    "--main-jar", $jarName,
    "--main-class", "app.Main",
    "--icon", (Join-Path $projectRoot "assets\app-icon.ico")
)

Write-Host "Creando la version portable..."
& $jpackage --type app-image @commonArguments --dest $portableDir --temp $portableTemp
if ($LASTEXITCODE -ne 0) {
    throw "jpackage no pudo crear la version portable."
}

if (-not (Test-Path -LiteralPath (Join-Path $wixDir "light.exe"))) {
    New-Item -ItemType Directory -Path (Split-Path -Parent $wixZip) -Force | Out-Null
    if (-not (Test-Path -LiteralPath $wixZip)) {
        Write-Host "Descargando WiX Toolset 3.14.1 desde su repositorio oficial..."
        Invoke-WebRequest -Uri $wixUrl -OutFile $wixZip
    }
    Remove-ProjectDirectory -Path $wixDir
    Expand-Archive -LiteralPath $wixZip -DestinationPath $wixDir -Force
}

$previousPath = $env:Path
try {
    $env:Path = $wixDir + ";" + $previousPath
    New-Item -ItemType Directory -Path $installerDir -Force | Out-Null

    Write-Host "Creando el instalador EXE..."
    & $jpackage --type exe @commonArguments `
        --dest $installerDir `
        --temp $installerTemp `
        --win-shortcut `
        --win-menu `
        --win-menu-group "Torven" `
        --win-dir-chooser `
        --win-per-user-install `
        --win-upgrade-uuid "5F1FCE2D-0B8A-4BE9-A8D1-202607270100"
    if ($LASTEXITCODE -ne 0) {
        throw "jpackage no pudo crear el instalador EXE."
    }
}
finally {
    $env:Path = $previousPath
}

if (-not (Test-Path -LiteralPath $installerPath)) {
    throw "No se encontro el instalador esperado: $installerPath"
}
$installer = Get-Item -LiteralPath $installerPath
Write-Host ""
Write-Host "Instalador creado correctamente:"
Write-Host $installer.FullName
Write-Host ("SHA-256: " + (Get-FileHash -LiteralPath $installer.FullName -Algorithm SHA256).Hash)
