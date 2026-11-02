# Build script: compile to JavaScript with TeaVM, test, then package the static site.
#   powershell -ExecutionPolicy Bypass -File build.ps1
#   powershell -ExecutionPolicy Bypass -File build.ps1 -Ezmj D:\path\to\ezmj
#
# Output in dist\:
#   ezmj-web-<version>\       the static site, ready to upload
#   ezmj-web-<version>.zip    the same files zipped
#
# The tile, room and state classes are compiled from the ezmj repository, by default the
# sibling folder ..\ezmj. Maven comes from the wrapper, so only a JDK 17+ has to be installed.
#
# The version comes from pom.xml.
# Stops before touching anything unless CHANGELOG.md has a section for that version
# and dist\ does not already hold it. See README.md.

param([string]$Ezmj = '..\ezmj')

$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot

function Invoke-Tool {
    param([string]$Tool, [string[]]$Arguments)
    $ErrorActionPreference = 'Continue'
    & $Tool @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "$Tool failed with exit code $LASTEXITCODE"
    }
}

if (-not (Test-Path (Join-Path $Ezmj 'src\ezmj\online\OnlineGame.java'))) {
    throw "Cannot find the ezmj repository at '$Ezmj'. Clone it next to this folder, or pass -Ezmj <path>."
}
$engineSource = (Resolve-Path (Join-Path $Ezmj 'src')).Path

$version = ([xml](Get-Content -Raw -Encoding UTF8 'pom.xml')).project.version
Write-Host "== EZMJ Web $version =="

if (-not (Select-String -Path 'CHANGELOG.md' -Pattern "^## $([regex]::Escape($version))( |$)" -Quiet)) {
    throw "CHANGELOG.md has no '## $version' section. Record the changes before packaging."
}
if (Test-Path "dist\ezmj-web-$version") {
    throw "$version is already packaged in dist\. Bump the version in pom.xml, or delete dist\ to rebuild the same version."
}

Write-Host '== Compile =='
Invoke-Tool '.\mvnw.cmd' @('-q', '-B', 'clean', 'test-compile', "-Dezmj.source=$engineSource", '-Dmaven.test.skip.exec=true')

Write-Host '== Test =='
Invoke-Tool 'java' @('-cp', 'target\classes;target\test-classes', 'ezmj.web.AllTests')

Write-Host '== Package =='
$site = "dist\ezmj-web-$version"
New-Item -ItemType Directory -Force $site | Out-Null
Copy-Item -Recurse 'target\site\*' $site
Compress-Archive -Path "$site\*" -DestinationPath "dist\ezmj-web-$version.zip"

Write-Host "Done: $site"
