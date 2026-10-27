param([string]$Maven = 'mvn')
$ErrorActionPreference = 'Stop'
# 仅清理当前子进程环境，避免部分 Windows JDK 将中文 PATH 转成问号后使 docker-java 解析失败。
$previousPath = $env:PATH
try {
    if ($env:OS -eq 'Windows_NT') {
        $env:PATH = ($env:PATH -split ';' | Where-Object { $_ -and $_ -notmatch '[^\x00-\x7F]' -and $_ -notmatch '\?' }) -join ';'
        if (-not $env:DOCKER_HOST) { $env:DOCKER_HOST = 'npipe:////./pipe/docker_engine' }
    }
    $projectRoot = Split-Path -Parent $PSScriptRoot
    $version = (& java -version 2>&1 | Out-String)
    $arguments = @('-B', '-f', (Join-Path $projectRoot 'backend/pom.xml'), 'verify')
    if ($version -match 'version "(\d+)' -and [int]$Matches[1] -ge 23) { $arguments += '-Dmaven.compiler.proc=full' }
    & $Maven @arguments
    exit $LASTEXITCODE
} finally { $env:PATH = $previousPath }
