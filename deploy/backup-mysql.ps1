param(
    [string]$Database = "critter_codex",
    [string]$OutDir = (Join-Path $PSScriptRoot "backups"),
    [string]$HostName = "127.0.0.1",
    [int]$Port = 3306,
    [string]$User = "root",
    [string]$Password = $(if ($env:CRITTER_DB_PASSWORD) { $env:CRITTER_DB_PASSWORD } else { "1234" })
)

$ErrorActionPreference = "Stop"
$mysqldump = "C:\Program Files\MySQL\MySQL Server 8.4\bin\mysqldump.exe"
if (-not (Test-Path -LiteralPath $mysqldump)) {
    throw "找不到 mysqldump：$mysqldump"
}
New-Item -ItemType Directory -Force -Path $OutDir | Out-Null

$stamp = Get-Date -Format "yyyyMMdd-HHmmss"
$file = Join-Path $OutDir "$Database-$stamp.sql"
$env:MYSQL_PWD = $Password

Write-Host "备份 $Database -> $file"
& $mysqldump `
    --host=$HostName --port=$Port --user=$User `
    --single-transaction --quick --routines --triggers --events `
    --default-character-set=utf8mb4 --hex-blob --set-gtid-purged=OFF `
    --result-file=$file $Database
if ($LASTEXITCODE -ne 0) {
    Remove-Item -LiteralPath $file -ErrorAction SilentlyContinue
    throw "mysqldump 失败，退出码 $LASTEXITCODE"
}

$hash = (Get-FileHash -LiteralPath $file -Algorithm SHA256).Hash.ToLowerInvariant()
"$hash  $([System.IO.Path]::GetFileName($file))" | Set-Content -LiteralPath "$file.sha256" -Encoding ascii
$size = [math]::Round((Get-Item -LiteralPath $file).Length / 1MB, 2)
Write-Host "完成：$file（${size} MB），SHA256=$hash"
