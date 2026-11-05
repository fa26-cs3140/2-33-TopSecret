param([string]$Jar = 'build/libs/TopSecret.jar')

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Net.Http
$workspace = Split-Path $PSScriptRoot -Parent
$jarPath = (Resolve-Path (Join-Path $workspace $Jar)).Path
$javaPath = (Get-Command java).Source
$temporary = Join-Path $workspace ('build/jar-smoke-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $temporary | Out-Null
$database = Join-Path $temporary 'missions.db'
$credentials = Join-Path $temporary 'credentials.cip'

function Start-Jar([string]$Mode) {
    $info = New-Object System.Diagnostics.ProcessStartInfo
    $info.FileName = $javaPath
    $info.Arguments = '-Dtopsecret.database="' + $database + '" -Dtopsecret.credentials="' + $credentials + '" -jar "' + $jarPath + '" ' + $Mode
    $info.WorkingDirectory = $workspace
    $info.UseShellExecute = $false
    $info.CreateNoWindow = $true
    $info.RedirectStandardInput = $true
    $info.RedirectStandardOutput = $true
    $info.RedirectStandardError = $true
    $process = New-Object System.Diagnostics.Process
    $process.StartInfo = $info
    $process.Start() | Out-Null
    return $process
}

function Invoke-Terminal([string]$InputText, [string]$Mode = '') {
    $process = Start-Jar $Mode
    try {
        $outputTask = $process.StandardOutput.ReadToEndAsync()
        $errorTask = $process.StandardError.ReadToEndAsync()
        $process.StandardInput.Write($InputText)
        $process.StandardInput.Close()
        if (-not $process.WaitForExit(15000)) {
            throw 'JAR did not finish in 15 seconds.'
        }
        $outputText = $outputTask.GetAwaiter().GetResult()
        $errorText = $errorTask.GetAwaiter().GetResult()
        if ($process.ExitCode -ne 0) {
            throw ('JAR failed: ' + $errorText)
        }
        return $outputText
    } finally {
        if (-not $process.HasExited) {
            $process.Kill()
        }
        $process.Dispose()
    }
}

function Assert-Contains([string]$Actual, [string]$Expected) {
    if (-not $Actual.Contains($Expected)) {
        throw ('Missing expected output: ' + $Expected)
    }
}

$refused = Invoke-Terminal '' '-web'
Assert-Contains $refused 'create an account first'
if (Test-Path $credentials) {
    throw 'Web startup unexpectedly created credentials.'
}
$setup = Invoke-Terminal "admin`nSecret1`n6`nGhost`n9`n2`nJar smoke message`n1`n1`n4`n4`n"
Assert-Contains $setup 'Account created.'
Assert-Contains $setup 'Message sent to Ghost.'
Assert-Contains $setup '1. '
$ghost = Invoke-Terminal "Ghost`nGhost1`nGhost1`n10`n4`n"
Assert-Contains $ghost 'Jar smoke message'
$adminInbox = Invoke-Terminal "admin`nSecret1`n10`n4`n"
Assert-Contains $adminInbox 'No messages.'

$handler = New-Object System.Net.Http.HttpClientHandler
$handler.AllowAutoRedirect = $false
$client = New-Object System.Net.Http.HttpClient($handler)
$client.Timeout = [TimeSpan]::FromSeconds(3)
$server = Start-Jar '-web'
$outputTask = $server.StandardOutput.ReadToEndAsync()
$errorTask = $server.StandardError.ReadToEndAsync()
try {
    $ready = $false
    for ($attempt = 0; $attempt -lt 50; $attempt++) {
        if ($server.HasExited) {
            throw ('Web JAR exited: ' + $outputTask.GetAwaiter().GetResult() + $errorTask.GetAwaiter().GetResult())
        }
        try {
            $response = $client.GetAsync('http://localhost:3002/').GetAwaiter().GetResult()
            $ready = ([int]$response.StatusCode -eq 200)
            if ($ready) { break }
        } catch { }
        Start-Sleep -Milliseconds 100
    }
    if (-not $ready) { throw 'Web JAR did not become ready.' }
    Assert-Contains ($response.Content.ReadAsStringAsync().GetAwaiter().GetResult()) 'name="password"'
    $protected = $client.GetAsync('http://localhost:3002/menu').GetAwaiter().GetResult()
    if ([int]$protected.StatusCode -ne 303) { throw 'Unauthenticated menu was accessible.' }
    $invalidForm = New-Object System.Net.Http.StringContent('username=admin&password=wrong', [Text.Encoding]::UTF8, 'application/x-www-form-urlencoded')
    $invalid = $client.PostAsync('http://localhost:3002/login', $invalidForm).GetAwaiter().GetResult()
    if ([int]$invalid.StatusCode -ne 401) { throw 'Invalid login was accepted.' }
    $form = New-Object System.Net.Http.StringContent('username=admin&password=Secret1', [Text.Encoding]::UTF8, 'application/x-www-form-urlencoded')
    $login = $client.PostAsync('http://localhost:3002/login', $form).GetAwaiter().GetResult()
    if ([int]$login.StatusCode -ne 303) { throw 'Valid login failed.' }
    $cookie = ($login.Headers.GetValues('Set-Cookie') | Select-Object -First 1)
    if ($cookie -match 'Max-Age|Expires=') { throw 'Login cookie is persistent.' }
    foreach ($route in @('/menu', '/content?type=briefs', '/content?type=agents', '/content?type=facilities', '/content?type=missions', '/record?type=briefs&id=1', '/audit')) {
        $page = $client.GetAsync('http://localhost:3002' + $route).GetAwaiter().GetResult()
        if ([int]$page.StatusCode -ne 200) { throw ('Failed route: ' + $route) }
    }
    $agents = $client.GetStringAsync('http://localhost:3002/content?type=agents').GetAwaiter().GetResult()
    Assert-Contains $agents 'Ghost'
    $writeForm = New-Object System.Net.Http.StringContent('action=add', [Text.Encoding]::UTF8, 'application/x-www-form-urlencoded')
    $write = $client.PostAsync('http://localhost:3002/content?type=agents', $writeForm).GetAwaiter().GetResult()
    if ([int]$write.StatusCode -ne 405) { throw 'Web write was accepted.' }
    $logoutForm = New-Object System.Net.Http.StringContent('')
    $logout = $client.PostAsync('http://localhost:3002/logout', $logoutForm).GetAwaiter().GetResult()
    if ([int]$logout.StatusCode -ne 303) { throw 'Logout failed.' }
    $afterLogout = $client.GetAsync('http://localhost:3002/menu').GetAwaiter().GetResult()
    if ([int]$afterLogout.StatusCode -ne 303) { throw 'Logout retained access.' }
    Write-Output 'PASS: executable JAR terminal setup, seeded data, private messaging, web startup guard, port 3002, login cookie, all pages, read-only requests and logout.'
    Write-Output ('Isolated verification data: ' + $temporary)
} finally {
    if (-not $server.HasExited) { $server.Kill(); $server.WaitForExit() }
    $server.Dispose()
    $client.Dispose()
    $handler.Dispose()
}
