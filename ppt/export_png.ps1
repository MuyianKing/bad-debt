# 用 PowerPoint COM 将 pptx 每页导出为 PNG
$ErrorActionPreference = "Stop"
$base = "E:\project\muyian\muyian-app\ppt"
$pptx = Join-Path $base "慕沿记账-产品功能全览.pptx"
$outdir = Join-Path $base "render"
if (Test-Path $outdir) { Remove-Item $outdir -Recurse -Force }
New-Item -ItemType Directory -Path $outdir | Out-Null

$pp = New-Object -ComObject PowerPoint.Application
try {
  # Open(FileName, ReadOnly:=msoTrue, Untitled:=msoFalse, WithWindow:=msoFalse)
  $pres = $pp.Presentations.Open($pptx, -1, 0, 0)
  $n = $pres.Slides.Count
  for ($i = 1; $i -le $n; $i++) {
    $f = Join-Path $outdir ("page{0:d2}.png" -f $i)
    $pres.Slides.Item($i).Export($f, "PNG", 1778, 1000)
  }
  $pres.Close()
  Write-Output "exported $n slides -> $outdir"
} finally {
  $pp.Quit()
  [System.Runtime.Interopservices.Marshal]::ReleaseComObject($pp) | Out-Null
}
