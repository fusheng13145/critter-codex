<#
一键重置青檐万灵册演示数据。

用法（在 critter-backend 目录）：
    .\scripts\demo\reset-demo.ps1 -Database critter_codex_dev -Force
    .\scripts\demo\reset-demo.ps1 -Database critter_codex -Force

默认只重置联调库 critter_codex_dev；主库必须显式指定。脚本会清空业务表，
保留账号、角色、字典、点位、物种与医院；执行前请确认目标库。

参数：
  -Database   目标库，默认 critter_codex_dev
  -Force      跳过交互确认（CI / 演示前一键重置）
  -Mysql      可选的 mysql.exe 路径
  -User       数据库用户，默认 root
  -Password   数据库密码，默认 1234（仅本地演示）
#>
param(
    [ValidateSet('critter_codex', 'critter_codex_dev')]
    [string]$Database = 'critter_codex_dev',
    [switch]$Force,
    [string]$Mysql = 'C:\Program Files\MySQL\MySQL Server 8.4\bin\mysql.exe',
    [string]$User = 'root',
    [string]$Password = '1234'
)

$ErrorActionPreference = 'Stop'
$backendRoot = Resolve-Path (Join-Path $PSScriptRoot '..\..')
$sqlFile = Join-Path $backendRoot 'demo\qywl-demo.sql'

if (-not (Test-Path $sqlFile)) {
    throw "找不到演示 SQL：$sqlFile；请先运行 python scripts/demo/generate_demo_data.py"
}
if (-not (Test-Path $Mysql)) {
    throw "找不到 mysql 客户端：$Mysql"
}

if (-not $Force) {
    $answer = Read-Host "将清空 $Database 的业务表并导入演示数据。输入 RESET 继续"
    if ($answer -ne 'RESET') {
        Write-Host '已取消。'
        exit 0
    }
}

Write-Host "导入演示数据到 $Database ..."
$env:MYSQL_PWD = $Password
try {
    $sql = Get-Content -Raw -Encoding UTF8 $sqlFile
    $sql | & $Mysql `
        "--user=$User" `
        '--host=127.0.0.1' `
        '--port=3306' `
        '--default-character-set=utf8mb4' `
        "--database=$Database"

    Write-Host '核对演示数据数量 ...'
    & $Mysql "--user=$User" '--host=127.0.0.1' '--port=3306' "--database=$Database" '-N' '-e' @'
SELECT CONCAT('animals=', COUNT(*)) FROM animal;
SELECT CONCAT('projects=', COUNT(*)) FROM project;
SELECT CONCAT('donations=', COUNT(*)) FROM donation;
SELECT CONCAT('groups=', COUNT(*)) FROM animal_group;
SELECT CONCAT('reports=', COUNT(*)) FROM report;
SELECT CONCAT('orders=', COUNT(*)) FROM rescue_order;
SELECT CONCAT('fund_mismatch=', COUNT(*)) FROM project p
WHERE p.raised_amount <> COALESCE((SELECT SUM(d.amount) FROM donation d WHERE d.project_id = p.id AND d.status = 'VERIFIED'), 0)
   OR p.spent_amount <> COALESCE((SELECT SUM(e.amount) FROM expense e WHERE e.project_id = p.id AND e.status = 'APPROVED'), 0);
'@
} finally {
    Remove-Item Env:MYSQL_PWD -ErrorAction SilentlyContinue
}
