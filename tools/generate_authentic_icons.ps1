Add-Type -AssemblyName System.Drawing

$rawDir = "E:\project\kaziyami\art\raw_icons"
$artIconsDir = "E:\project\kaziyami\art\icons"
[System.IO.Directory]::CreateDirectory($artIconsDir) | Out-Null

$bgColor = [System.Drawing.Color]::FromArgb(255, 18, 19, 26) # #12131A dark theme

function Create-Icon {
    param(
        [string]$name,
        [System.Drawing.Image]$srcImg,
        [int]$maxBox = 152
    )

    $bmp = New-Object System.Drawing.Bitmap 192, 192, ([System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $g.Clear($bgColor)
    $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::HighQuality
    $g.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality

    $scale = [Math]::Min($maxBox / $srcImg.Width, $maxBox / $srcImg.Height)
    $drawW = [int]($srcImg.Width * $scale)
    $drawH = [int]($srcImg.Height * $scale)
    $x = [int]((192 - $drawW) / 2)
    $y = [int]((192 - $drawH) / 2)
    $g.DrawImage($srcImg, $x, $y, $drawW, $drawH)

    $outPath = Join-Path $artIconsDir "$name.png"
    $bmp.Save($outPath, [System.Drawing.Imaging.ImageFormat]::Png)
    $g.Dispose()
    $bmp.Dispose()
    Write-Output "[+] Created authentic icon: $outPath ($drawW x $drawH on 192x192)"
}

# 1. Nekopoi: Crop authentic cat emblem from nekopoi_logo.png
$nekoLogo = [System.Drawing.Bitmap]::FromFile("$rawDir\nekopoi_logo.png")
$rect = New-Object System.Drawing.Rectangle 0, 0, 52, 50
$nekoCat = $nekoLogo.Clone($rect, $nekoLogo.PixelFormat)
Create-Icon -name "nekopoi" -srcImg $nekoCat -maxBox 152
$nekoCat.Dispose()
$nekoLogo.Dispose()

# 2. IndoHentai: Authentic 48x48 site favicon
$indo = [System.Drawing.Image]::FromFile("$rawDir\indohentai.ico")
Create-Icon -name "indohentai" -srcImg $indo -maxBox 152
$indo.Dispose()

# 3. NontonHentai: Authentic 300x300 site icon
$nonton = [System.Drawing.Image]::FromFile("$rawDir\nontonhentai.webp")
Create-Icon -name "nontonhentai" -srcImg $nonton -maxBox 192
$nonton.Dispose()

# 4. Hentaimama: Authentic site heart/crown mark
$mama = [System.Drawing.Image]::FromFile("$rawDir\hentaimama.png")
Create-Icon -name "hentaimama" -srcImg $mama -maxBox 152
$mama.Dispose()

# 5. JAVSubbed: Authentic 100x100 site icon
$jav = [System.Drawing.Image]::FromFile("$rawDir\javsubbed.png")
Create-Icon -name "javsubbed" -srcImg $jav -maxBox 152
$jav.Dispose()

# 6. Hanime: Authentic Apple touch precomposed icon (152x152)
$hanime = [System.Drawing.Image]::FromFile("$rawDir\hanime_touch_pre.png")
Create-Icon -name "hanime" -srcImg $hanime -maxBox 192
$hanime.Dispose()

