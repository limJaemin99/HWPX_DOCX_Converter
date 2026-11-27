<#
JDK만으로 빌드·시험 (Maven 없이). Linux·CI는 build.sh

  .\build.ps1                 빌드 + 시험
  .\build.ps1 build           컴파일 → jar → 시험 클래스 컴파일 (JDK 9 이상)
  .\build.ps1 check           jar·src/main에 네트워크 관련 API가 없는지 검사 (jdeps, JDK 9 이상)
  .\build.ps1 test            만들어 둔 jar로 시험 + 공개 시험 문서 회귀 시험 (JDK 8 이상)
  .\build.ps1 regress -Docs <폴더> -Baseline <폴더> [-Out <폴더>]
                              다른 문서 폴더로 회귀 시험 (저장소 밖 문서도 됨)
  .\build.ps1 baseline        공개 시험 문서 기준값을 지금 수치로 다시 씀 (-Docs·-Baseline 으로 다른 폴더도)
  .\build.ps1 clean

  -JavaHome      빌드할 JDK. 없으면 JAVA_HOME, 그것도 없으면 PATH의 javac
  -TestJavaHome  시험을 돌릴 JDK 목록. 없으면 -JavaHome
                 예: .\build.ps1 -TestJavaHome 'C:\Program Files\Java\jdk1.8.0_202','C:\Program Files\Java\jdk-17'

실행 정책 때문에 막히면: powershell -ExecutionPolicy Bypass -File .\build.ps1
결과는 build\ (Maven·이클립스의 target\ 과 따로)
#>
param(
    [ValidateSet('all', 'build', 'check', 'test', 'regress', 'baseline', 'clean')]
    [string]$Task = 'all',
    [string]$JavaHome = $env:JAVA_HOME,
    [string[]]$TestJavaHome = @(),
    [string]$Docs,
    [string]$Baseline,
    [string]$Out
)
$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath $PSScriptRoot

$name = 'hwpx-docx-converter'
$mainClass = 'io.github.limjaemin99.hwpxdocx.cli.Main'
$runner = 'io.github.limjaemin99.hwpxdocx.testkit.TestRunner'
$regression = 'io.github.limjaemin99.hwpxdocx.regress.Regression'
$version = ([xml](Get-Content -LiteralPath 'pom.xml' -Raw -Encoding UTF8)).project.version
$build = 'build'
$jar = "$build/libs/$name-$version.jar"
$utf8 = New-Object Text.UTF8Encoding $false

# -File 로 부르면 배열이 한 문자열로 오므로 쉼표로 나눔
$TestJavaHome = @($TestJavaHome | ForEach-Object { $_ -split ',' } | Where-Object { $_ })

function Get-Tool([string]$jdk, [string]$tool) {
    if ($jdk) { return (Join-Path $jdk "bin\$tool.exe") }
    return $tool
}

function Invoke-Tool([string]$exe, [string[]]$arguments) {
    & $exe @arguments
    if ($LASTEXITCODE -ne 0) { throw "$exe 실패 (종료 코드 $LASTEXITCODE)" }
}

# javac @파일 은 따옴표 안의 \ 를 이스케이프로 읽으므로 / 로 바꿔 씀
function Write-SourceList([string]$dir, [string]$listFile) {
    $root = $PSScriptRoot.TrimEnd('\') + '\'
    $lines = Get-ChildItem -LiteralPath $dir -Recurse -Filter '*.java' |
        Sort-Object FullName |
        ForEach-Object { '"' + $_.FullName.Substring($root.Length).Replace('\', '/') + '"' }
    [IO.File]::WriteAllLines((Join-Path $PSScriptRoot $listFile), [string[]]$lines, $utf8)
}

function Invoke-Clean {
    if (Test-Path -LiteralPath $build) { Remove-Item -LiteralPath $build -Recurse -Force }
}

function Invoke-Build {
    foreach ($d in "$build/classes", "$build/test-classes", "$build/libs") {
        if (Test-Path -LiteralPath $d) { Remove-Item -LiteralPath $d -Recurse -Force }
    }
    New-Item -ItemType Directory -Force "$build/classes/META-INF", "$build/test-classes", "$build/libs" | Out-Null
    $javac = Get-Tool $JavaHome 'javac'

    Write-SourceList 'src/main/java' "$build/main-sources.txt"
    Invoke-Tool $javac @('--release', '8', '-encoding', 'UTF-8', '-Xlint:all,-options',
        '-d', "$build/classes", "@$build/main-sources.txt")
    if (Test-Path -LiteralPath 'src/main/resources') {
        Copy-Item -Path 'src/main/resources/*' -Destination "$build/classes" -Recurse -Force
    }
    Copy-Item -LiteralPath 'LICENSE', 'NOTICE' -Destination "$build/classes/META-INF"

    $manifest = @(
        "Main-Class: $mainClass",
        "Implementation-Title: $name",
        "Implementation-Version: $version",
        'Automatic-Module-Name: io.github.limjaemin99.hwpxdocx'
    )
    [IO.File]::WriteAllLines((Join-Path $PSScriptRoot "$build/MANIFEST.MF"), [string[]]$manifest, $utf8)
    Invoke-Tool (Get-Tool $JavaHome 'jar') @('cfm', $jar, "$build/MANIFEST.MF", '-C', "$build/classes", '.')

    Write-SourceList 'src/test/java' "$build/test-sources.txt"
    Invoke-Tool $javac @('--release', '8', '-encoding', 'UTF-8', '-Xlint:all,-options',
        '-cp', $jar, '-d', "$build/test-classes", "@$build/test-sources.txt")
    if (Test-Path -LiteralPath 'src/test/resources') {
        Copy-Item -Path 'src/test/resources/*' -Destination "$build/test-classes" -Recurse -Force
    }
    Write-Host "jar: $jar"
}

# build.sh do_check와 같은 검사
$netPackages = '^(java\.net|javax\.net|jdk\.net|com\.sun\.net|sun\.net|java\.rmi|javax\.rmi|javax\.naming)(\.|$)'
$netSource = 'java\.net|javax\.net|jdk\.net|jdk\.httpserver|com\.sun\.net|sun\.net|java\.rmi|javax\.rmi|javax\.naming|URLClassLoader|\bgetResources?\(|\btoURI\(|\btoURL\('

function Invoke-Check {
    if (-not (Test-Path -LiteralPath $jar)) { throw "jar가 없음: $jar (먼저 .\build.ps1 build)" }
    $jdeps = Get-Tool $JavaHome 'jdeps'
    $lines = & $jdeps -verbose:package $jar
    if ($LASTEXITCODE -ne 0) { throw "$jdeps 실패 (종료 코드 $LASTEXITCODE)" }
    $deps = @($lines | ForEach-Object { $f = -split $_; if ($f.Count -ge 3 -and $f[1] -eq '->') { , $f } })
    $badDeps = @($deps | Where-Object { $_[2] -match $netPackages })
    $badSrc = @(Get-ChildItem -LiteralPath 'src/main' -Recurse -File | Select-String -Pattern $netSource -CaseSensitive)
    foreach ($d in $badDeps) { Write-Host "jar가 네트워크 관련 패키지를 씀: $($d[0]) -> $($d[2])" }
    foreach ($s in $badSrc) { Write-Host "src/main에 네트워크 관련 이름이 있음: $($s.Path):$($s.LineNumber): $($s.Line.Trim())" }
    if ($badDeps.Count -gt 0 -or $badSrc.Count -gt 0) { throw '네트워크 검사 실패' }
    $count = @($deps | ForEach-Object { $_[2] } | Sort-Object -Unique).Count
    Write-Host "네트워크 검사 통과: ${count}개 패키지 참조, 금지 패키지 없음"
}

function Invoke-Test {
    if (-not (Test-Path -LiteralPath $jar)) { throw "jar가 없음: $jar (먼저 .\build.ps1 build)" }
    $homes = if ($TestJavaHome.Count -gt 0) { $TestJavaHome } else { @($JavaHome) }
    foreach ($h in $homes) {
        $java = Get-Tool $h 'java'
        Write-Host "== $java"
        Invoke-Tool $java @('-cp', "$jar;$build/test-classes", $runner, "$build/test-classes")
        Invoke-Tool $java @('-cp', "$jar;$build/test-classes", $regression,
            '--docs', "$build/test-classes/docs", '--baseline', "$build/test-classes/baseline")
        Invoke-Tool $java @('-jar', $jar, '--version')
    }
}

# 저장소 밖 문서·기준값도 폴더만 주면 같은 실행기로 돎
function Invoke-Regress([bool]$update) {
    if (-not (Test-Path -LiteralPath "$build/test-classes")) { throw "시험 클래스가 없음 (먼저 .\build.ps1 build)" }
    $d = if ($Docs) { $Docs } else { 'src/test/resources/docs' }
    $b = if ($Baseline) { $Baseline } else { 'src/test/resources/baseline' }
    $arguments = @('-cp', "$jar;$build/test-classes", $regression, '--docs', $d, '--baseline', $b)
    if ($Out) { $arguments += @('--out', $Out) }
    if ($update) { $arguments += '--update' }
    Invoke-Tool (Get-Tool $JavaHome 'java') $arguments
}

switch ($Task) {
    'all' { Invoke-Build; Invoke-Check; Invoke-Test }
    'build' { Invoke-Build }
    'check' { Invoke-Check }
    'test' { Invoke-Test }
    'regress' { Invoke-Regress $false }
    'baseline' { Invoke-Regress $true }
    'clean' { Invoke-Clean }
}
