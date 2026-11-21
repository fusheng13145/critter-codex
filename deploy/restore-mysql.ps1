param(
    [Parameter(Mandatory = $true)]
    [string]$BackupFile,
    [string]$Database = "critter_codex_restore",
    [string]$HostName = "127.0.0.1",
    [int]$Port = 3306,
    [string]$User = "root",
    [string]$Password = $(if ($env:CRITTER_DB_PASSWORD) { $env:CRITTER_DB_PASSWORD } else { "1234" }),
    [switch]$Force
)

$ErrorActionPreference = "Stop"
$mysql = "C:\Program Files\MySQL\MySQL Server 8.4\bin\mysql.exe"
if (-not (Test-Path -LiteralPath $mysql)) {
    throw "找不到 mysql：$mysql"
}
if (-not (Test-Path -LiteralPath $BackupFile)) {
    throw "备份文件不存在：$BackupFile"
}
if (-not $Force -and $Database -eq "critter_codex") {
    throw "拒绝直接覆盖主库 critter_codex；如确认请加 -Force"
}

$hashFile = "$BackupFile.sha256"
if (Test-Path -LiteralPath $hashFile) {
    $expected = (Get-Content -LiteralPath $hashFile -TotalCount 1).Split(" ")[0].ToLowerInvariant()
    $actual = (Get-FileHash -LiteralPath $BackupFile -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($expected -ne $actual) {
        throw "备份校验失败：期望 $expected，实际 $actual"
    }
    Write-Host "SHA256 校验通过：$actual"
} else {
    Write-Warning "未找到 .sha256 校验文件，跳过完整性校验"
}

$env:MYSQL_PWD = $Password
$createSql = 'CREATE DATABASE IF NOT EXISTS `' + $Database + '` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci'
& $mysql --host=$HostName --port=$Port --user=$User -e $createSql
if ($LASTEXITCODE -ne 0) { throw "创建数据库失败，退出码 $LASTEXITCODE" }

$sqlPath = (Resolve-Path -LiteralPath $BackupFile).Path
Write-Host "恢复 $BackupFile -> $Database"
$mysqlArgs = @(
    "--host=$HostName", "--port=$Port", "--user=$User",
    "--default-character-set=utf8mb4", $Database
)
$process = Start-Process -FilePath $mysql -ArgumentList $mysqlArgs `
    -RedirectStandardInput $sqlPath -NoNewWindow -Wait -PassThru
if ($process.ExitCode -ne 0) { throw "导入失败，退出码 $($process.ExitCode)" }

Write-Host "恢复完成，核对行数："
& $mysql --host=$HostName --port=$Port --user=$User --default-character-set=utf8mb4 `
    -N -e "SELECT 'animal', COUNT(*) FROM animal UNION ALL SELECT 'project', COUNT(*) FROM project UNION ALL SELECT 'donation', COUNT(*) FROM donation UNION ALL SELECT 'animal_group', COUNT(*) FROM animal_group;" `
    $Database
