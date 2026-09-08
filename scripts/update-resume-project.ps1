param(
    [Parameter(Mandatory = $true)]
    [string]$InputPath,

    [Parameter(Mandatory = $true)]
    [string]$OutputPath
)

$ErrorActionPreference = 'Stop'

if (-not (Test-Path -LiteralPath $InputPath)) {
    throw "Resume not found: $InputPath"
}

$outputDirectory = Split-Path -Parent $OutputPath
if (-not (Test-Path -LiteralPath $outputDirectory)) {
    New-Item -ItemType Directory -Path $outputDirectory | Out-Null
}

Copy-Item -LiteralPath $InputPath -Destination $OutputPath -Force

Add-Type -AssemblyName System.IO.Compression.FileSystem
Add-Type -AssemblyName System.IO.Compression
$archive = [System.IO.Compression.ZipFile]::Open($OutputPath, [System.IO.Compression.ZipArchiveMode]::Update)
try {
    $entry = $archive.GetEntry('word/document.xml')
    if ($null -eq $entry) {
        throw 'word/document.xml is missing from the DOCX package.'
    }

    $reader = [System.IO.StreamReader]::new($entry.Open())
    try {
        [xml]$document = $reader.ReadToEnd()
    } finally {
        $reader.Dispose()
    }

    $namespaces = [System.Xml.XmlNamespaceManager]::new($document.NameTable)
    $namespaces.AddNamespace('w', 'http://schemas.openxmlformats.org/wordprocessingml/2006/main')

    function Set-ResumeParagraph {
        param(
            [string]$StartsWith,
            [string]$Heading,
            [string]$Body,
            [int]$BodyNodeIndex
        )

        $matches = @($document.SelectNodes('//w:body/w:p', $namespaces) | Where-Object {
            $text = ($_.SelectNodes('.//w:t', $namespaces) | ForEach-Object { $_.InnerText }) -join ''
            $text.StartsWith($StartsWith, [System.StringComparison]::Ordinal)
        })
        if ($matches.Count -ne 1) {
            throw "Expected exactly one paragraph starting with '$StartsWith', found $($matches.Count)."
        }

        $textNodes = @($matches[0].SelectNodes('.//w:t', $namespaces))
        if ($textNodes.Count -le $BodyNodeIndex) {
            throw "Paragraph '$StartsWith' does not contain the expected run structure."
        }
        foreach ($node in $textNodes) {
            $node.InnerText = ''
        }
        $textNodes[0].InnerText = $Heading
        $textNodes[$BodyNodeIndex].InnerText = $Body
    }

    $replacementPath = Join-Path $PSScriptRoot 'resume-project-replacements.json'
    $replacements = Get-Content -Raw -Encoding UTF8 -LiteralPath $replacementPath | ConvertFrom-Json
    foreach ($item in $replacements) {
        Set-ResumeParagraph -StartsWith $item.startsWith -Heading $item.heading `
            -Body $item.body -BodyNodeIndex $item.bodyNodeIndex
    }

    $settings = [System.Xml.XmlWriterSettings]::new()
    $settings.Encoding = [System.Text.UTF8Encoding]::new($false)
    $settings.Indent = $false
    $memory = [System.IO.MemoryStream]::new()
    $writer = [System.Xml.XmlWriter]::Create($memory, $settings)
    try {
        $document.Save($writer)
        $writer.Flush()
    } finally {
        $writer.Dispose()
    }

    $bytes = $memory.ToArray()
    $memory.Dispose()
    $entry.Delete()
    $replacement = $archive.CreateEntry('word/document.xml', [System.IO.Compression.CompressionLevel]::Optimal)
    $stream = $replacement.Open()
    try {
        $stream.Write($bytes, 0, $bytes.Length)
    } finally {
        $stream.Dispose()
    }
} finally {
    $archive.Dispose()
}

Write-Output $OutputPath
