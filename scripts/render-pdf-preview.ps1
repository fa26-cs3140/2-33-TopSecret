param([Parameter(Mandatory = $true)][string]$Pdf, [string]$Output = 'build/pdf-preview')

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Runtime.WindowsRuntime
$null = [Windows.Storage.StorageFile, Windows.Storage, ContentType = WindowsRuntime]
$null = [Windows.Data.Pdf.PdfDocument, Windows.Data.Pdf, ContentType = WindowsRuntime]
$null = [Windows.Storage.Streams.InMemoryRandomAccessStream, Windows.Storage.Streams, ContentType = WindowsRuntime]
$null = [Windows.Data.Pdf.PdfPageRenderOptions, Windows.Data.Pdf, ContentType = WindowsRuntime]
$operationMethod = [System.WindowsRuntimeSystemExtensions].GetMethods() | Where-Object {
    $_.Name -eq 'AsTask' -and $_.IsGenericMethod -and $_.GetParameters().Count -eq 1 -and
    $_.GetParameters()[0].ParameterType.Name -eq 'IAsyncOperation`1'
} | Select-Object -First 1
$actionMethod = [System.WindowsRuntimeSystemExtensions].GetMethods() | Where-Object {
    $_.Name -eq 'AsTask' -and -not $_.IsGenericMethod -and $_.GetParameters().Count -eq 1 -and
    $_.GetParameters()[0].ParameterType.Name -eq 'IAsyncAction'
} | Select-Object -First 1

function Wait-Operation($Operation, [Type]$ResultType) {
    $task = $operationMethod.MakeGenericMethod($ResultType).Invoke($null, @($Operation))
    $task.Wait()
    return $task.Result
}

$pdfPath = (Resolve-Path $Pdf).Path
$storage = Wait-Operation ([Windows.Storage.StorageFile]::GetFileFromPathAsync($pdfPath)) ([Windows.Storage.StorageFile])
$document = Wait-Operation ([Windows.Data.Pdf.PdfDocument]::LoadFromFileAsync($storage)) ([Windows.Data.Pdf.PdfDocument])
New-Item -ItemType Directory -Path $Output -Force | Out-Null
$name = [IO.Path]::GetFileNameWithoutExtension($pdfPath)
for ($index = 0; $index -lt $document.PageCount; $index++) {
    $page = $document.GetPage($index)
    $stream = New-Object Windows.Storage.Streams.InMemoryRandomAccessStream
    $options = New-Object Windows.Data.Pdf.PdfPageRenderOptions
    $options.DestinationWidth = 1600
    $task = $actionMethod.Invoke($null, @($page.RenderToStreamAsync($stream, $options)))
    $task.Wait()
    $readStream = [System.IO.WindowsRuntimeStreamExtensions]::AsStreamForRead($stream)
    $destination = Join-Path $Output ($name + '-' + ($index + 1) + '.png')
    $file = [IO.File]::Create([IO.Path]::GetFullPath($destination))
    try { $readStream.CopyTo($file) } finally { $file.Dispose(); $readStream.Dispose(); $stream.Dispose(); $page.Dispose() }
    Write-Output $destination
}
