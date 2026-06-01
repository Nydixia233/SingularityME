# PowerShell Set-Content 损坏 UTF-8 中文注释编码

## 错误现象

`NetworkTerminalUI.java` 中 11 行中文注释出现 mojibake（13 个 U+FFFD 替换字符），Java 源码中的中文注释全部变成乱码。

## 触发场景

在 `abe5b07` 提交中，需要用 PowerShell 批量替换文件中的 `Integer.toHexString(...)` → `String.format(...)`。执行了：

```powershell
$content = Get-Content $file -Raw
$content = $content -replace 'pattern', 'replacement'
Set-Content $file -Value $content -NoNewline
```

`Set-Content` 未指定 `-Encoding UTF8`，PowerShell 默认用系统编码（Windows 中文环境为 GBK）重写原本 UTF-8 编码的文件。

## 根本原因

- Minecraft Mod 源码、lang 文件均采用 UTF-8 编码
- Windows PowerShell 的 `Set-Content`、`Out-File` 默认编码为系统 ANSI（中文环境 GBK）
- 读取时 `Get-Content -Raw` 可能正确识别 BOM/UTF-8，但写回时未保编码
- VS Code 编辑工具的 `replace_string_in_file` 方法保证不损编码，但 PowerShell 原生命令不保证

## 修复方案

从干净版本 `6d606ee` 取回原始注释，逐行替换含 U+FFFD 的行，保留 CRLF 行尾。最终验证：U+FFFD 残留数 = 0，diff 确认只动注释、零功能代码改动。

## 预防措施

| 操作 | ❌ 禁止 | ✅ 正确做法 |
|------|--------|------------|
| 单文件替换 | PowerShell `Set-Content` 无编码参数 | 使用 `replace_string_in_file` 工具 |
| 批量文本替换 | PowerShell pipeline | `replace_string_in_file` 逐处替换 |
| 必须用 PS 写文件 | `Set-Content` / `Out-File` | `Out-File -Encoding UTF8` 或 `[IO.File]::WriteAllText(path, content, [Text.Encoding]::UTF8)` |

**铁律**：本项目所有源码为 UTF-8，**绝不**用 PowerShell 默认编码写文件。任何涉及文件读写的 PowerShell 命令必须显式指定 `-Encoding UTF8`。

## 相关提交

- `abe5b07` — 引入编码损坏（第二轮修复）
- `6d606ee` — 上一版，注释干净
- 修复提交 — 手动恢复注释
