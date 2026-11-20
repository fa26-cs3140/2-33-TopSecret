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

function Invoke-WebPost([string]$Route, [hashtable]$Fields) {
    $pairs = foreach ($field in $Fields.GetEnumerator()) {
        [Uri]::EscapeDataString($field.Key) + '=' + [Uri]::EscapeDataString([string]$field.Value)
    }
    $content = New-Object System.Net.Http.StringContent(($pairs -join '&'), [Text.Encoding]::UTF8, 'application/x-www-form-urlencoded')
    try {
        return $client.PostAsync('http://localhost:3002' + $Route, $content).GetAwaiter().GetResult()
    } finally {
        $content.Dispose()
    }
}

function Get-Csrf([string]$Route) {
    $response = $client.GetAsync('http://localhost:3002' + $Route).GetAwaiter().GetResult()
    if ([int]$response.StatusCode -ne 200) { throw ('Form failed: ' + $Route) }
    $html = $response.Content.ReadAsStringAsync().GetAwaiter().GetResult()
    $match = [regex]::Match($html, 'name="csrf" value="([^"]+)"')
    if (-not $match.Success) { throw 'Missing editing CSRF token.' }
    return $match.Groups[1].Value
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
    foreach ($route in @('/menu', '/content?type=briefs', '/content?type=agents', '/content?type=facilities', '/content?type=missions', '/content?type=countries', '/record?type=briefs&id=1', '/audit', '/messages')) {
        $page = $client.GetAsync('http://localhost:3002' + $route).GetAwaiter().GetResult()
        if ([int]$page.StatusCode -ne 200) { throw ('Failed route: ' + $route) }
    }
    $agents = $client.GetStringAsync('http://localhost:3002/content?type=agents').GetAwaiter().GetResult()
    Assert-Contains $agents 'Ghost'
    $writeForm = New-Object System.Net.Http.StringContent('action=add', [Text.Encoding]::UTF8, 'application/x-www-form-urlencoded')
    $write = $client.PostAsync('http://localhost:3002/content?type=agents', $writeForm).GetAwaiter().GetResult()
    if ([int]$write.StatusCode -ne 405) { throw 'Read-only list route accepted a write.' }
    $facilityId = $null
    $agentId = $null
    $countryId = $null
    foreach ($type in @('countries', 'facilities', 'agents', 'briefs', 'missions')) {
        $label = 'Jar smoke ' + $type
        $fields = @{ type = $type; csrf = (Get-Csrf ('/new?type=' + $type)) }
        switch ($type) {
            'countries' { $fields.name = $label }
            'facilities' { $fields.name = $label; $fields.abbreviation = 'JS'; $fields.country_id = $countryId }
            'agents' {
                $fields.name = $label; $fields.date_of_birth = '1990-01-01'
                $fields.date_of_death = ''; $fields.notes = ''; $fields.facility_id = $facilityId
            }
            'briefs' { $fields.title = $label; $fields.date = '2026-11-19'; $fields.text = 'JAR editing test' }
            'missions' {
                $fields.title = $label; $fields.date = '2026-11-19'; $fields.brief = 'JAR editing test'
                $fields.facility_id = $facilityId; $fields.agent_id = $agentId
            }
        }
        $created = Invoke-WebPost '/create' $fields
        if ([int]$created.StatusCode -ne 303) { throw ('Create failed: ' + $type) }
        if ($created.Headers.Location.OriginalString -ne ('/content?type=' + $type)) { throw 'Wrong create redirect.' }
        $listing = $client.GetStringAsync('http://localhost:3002/content?type=' + $type).GetAwaiter().GetResult()
        $record = [regex]::Match($listing, ('href="/record\?type=' + $type + '&amp;id=(\d+)">[^<]*' + [regex]::Escape($label)))
        if (-not $record.Success) { throw ('Created record missing: ' + $type) }
        $recordId = $record.Groups[1].Value
        if ($type -eq 'countries') { $countryId = $recordId }
        if ($type -eq 'facilities') { $facilityId = $recordId }
        if ($type -eq 'agents') { $agentId = $recordId }
        $fields.csrf = Get-Csrf ('/edit?type=' + $type + '&id=' + $recordId)
        $fields.id = $recordId
        if ($fields.ContainsKey('name')) { $fields.name = 'Updated ' + $label } else { $fields.title = 'Updated ' + $label }
        if ($type -eq 'missions') { $fields.Remove('facility_id'); $fields.Remove('agent_id') }
        $updated = Invoke-WebPost '/update' $fields
        if ([int]$updated.StatusCode -ne 303) { throw ('Update failed: ' + $type) }
        $detail = $client.GetStringAsync('http://localhost:3002' + $updated.Headers.Location.OriginalString).GetAwaiter().GetResult()
        Assert-Contains $detail ('Updated ' + $label)
        Assert-Contains $detail '<nav'
        Assert-Contains $detail '<style>'
        Assert-Contains $detail 'action="/logout"'
        if ($type -eq 'facilities') {
            Assert-Contains $detail ('href="/record?type=countries&amp;id=' + $countryId + '"')
            Assert-Contains $detail 'Updated Jar smoke countries'
        }
    }
    $renamedCountry = Invoke-WebPost '/update' @{
        type = 'countries'; id = $countryId; name = 'Renamed JAR country'
        csrf = (Get-Csrf ('/edit?type=countries&id=' + $countryId))
    }
    if ([int]$renamedCountry.StatusCode -ne 303) { throw 'Country rename failed.' }
    $facilityDetail = $client.GetStringAsync('http://localhost:3002/record?type=facilities&id=' + $facilityId).GetAwaiter().GetResult()
    Assert-Contains $facilityDetail 'Renamed JAR country'
    Assert-Contains $facilityDetail ('href="/record?type=agents&amp;id=' + $agentId + '"')
    Assert-Contains $facilityDetail 'Updated Jar smoke missions'
    $agentDetail = $client.GetStringAsync('http://localhost:3002/record?type=agents&id=' + $agentId).GetAwaiter().GetResult()
    Assert-Contains $agentDetail 'Updated Jar smoke missions'
    $countryDetail = $client.GetStringAsync('http://localhost:3002/record?type=countries&id=' + $countryId).GetAwaiter().GetResult()
    Assert-Contains $countryDetail ('href="/record?type=facilities&amp;id=' + $facilityId + '"')
    $csrf = Get-Csrf '/new?type=facilities'
    $invalid = Invoke-WebPost '/create' @{ type = 'facilities'; name = '<Unstored>'; abbreviation = ''; csrf = $csrf }
    if ([int]$invalid.StatusCode -ne 400) { throw 'Invalid form was accepted.' }
    Assert-Contains ($invalid.Content.ReadAsStringAsync().GetAwaiter().GetResult()) '&lt;Unstored&gt;'
    $forged = Invoke-WebPost '/create' @{ type = 'facilities'; name = 'Unstored'; abbreviation = 'NO'; csrf = 'forged' }
    if ([int]$forged.StatusCode -ne 400) { throw 'Forged CSRF token was accepted.' }
    $auditPage = $client.GetStringAsync('http://localhost:3002/audit').GetAwaiter().GetResult()
    Assert-Contains $auditPage 'CREATE'
    Assert-Contains $auditPage 'UPDATE'
    $logoutForm = New-Object System.Net.Http.StringContent('')
    $logout = $client.PostAsync('http://localhost:3002/logout', $logoutForm).GetAwaiter().GetResult()
    if ([int]$logout.StatusCode -ne 303) { throw 'Logout failed.' }
    $afterLogout = $client.GetAsync('http://localhost:3002/menu').GetAwaiter().GetResult()
    if ([int]$afterLogout.StatusCode -ne 303) { throw 'Logout retained access.' }
    $readerLogin = Invoke-WebPost '/login' @{ username = 'Ghost'; password = 'Ghost1' }
    if ([int]$readerLogin.StatusCode -ne 303) { throw 'Reader login failed.' }
    $denied = $client.GetAsync('http://localhost:3002/new?type=facilities').GetAwaiter().GetResult()
    if ([int]$denied.StatusCode -ne 403) { throw 'Reader could access an editing form.' }
    $deniedWrite = Invoke-WebPost '/create' @{ type = 'facilities'; name = 'Denied'; abbreviation = 'NO'; csrf = $csrf }
    if ([int]$deniedWrite.StatusCode -ne 403) { throw 'Reader could submit a write.' }
    $compose = $client.GetStringAsync('http://localhost:3002/messages/new').GetAwaiter().GetResult()
    $recipient = [regex]::Match($compose, '<option value="(\d+)">Ghost</option>')
    if (-not $recipient.Success) { throw 'Ghost missing from web recipient dropdown.' }
    $sent = Invoke-WebPost '/messages/send' @{
        agentId = $recipient.Groups[1].Value; body = 'JAR web message <private>'
        csrf = (Get-Csrf '/messages/new')
    }
    if ([int]$sent.StatusCode -ne 303) { throw 'Standard user could not send web message.' }
    $inbox = $client.GetStringAsync('http://localhost:3002/messages').GetAwaiter().GetResult()
    Assert-Contains $inbox '<td>Unread</td>'
    $message = [regex]::Match($inbox, 'name="id" value="(\d+)"')
    if (-not $message.Success) { throw 'Sent message missing from recipient inbox.' }
    $opened = Invoke-WebPost '/messages/open' @{ id = $message.Groups[1].Value; csrf = (Get-Csrf '/messages') }
    if ([int]$opened.StatusCode -ne 303) { throw 'Message open failed.' }
    $messageDetail = $client.GetStringAsync('http://localhost:3002' + $opened.Headers.Location.OriginalString).GetAwaiter().GetResult()
    Assert-Contains $messageDetail 'JAR web message &lt;private&gt;'
    $readInbox = $client.GetStringAsync('http://localhost:3002/messages').GetAwaiter().GetResult()
    Assert-Contains $readInbox '<td>Read</td>'
    $readRow = '(?s)<tr><td>Read</td>(?:(?!</tr>).)*name="id" value="' + $message.Groups[1].Value + '"'
    if (-not [regex]::IsMatch($readInbox, $readRow)) { throw 'Opened message remained unread.' }
    $null = Invoke-WebPost '/logout' @{}
    $null = Invoke-WebPost '/login' @{ username = 'admin'; password = 'Secret1' }
    $private = $client.GetAsync('http://localhost:3002/messages/view?id=' + $message.Groups[1].Value).GetAwaiter().GetResult()
    if ([int]$private.StatusCode -ne 404) { throw 'Administrator could read another agent message.' }
    Write-Output 'PASS: executable JAR terminal setup, all five types created/edited, country assignment/rename, related links, shared styling/navigation, private web messaging/read state, login/logout, audit, validation, CSRF and reader denial.'
    Write-Output ('Isolated verification data: ' + $temporary)
} finally {
    if (-not $server.HasExited) { $server.Kill(); $server.WaitForExit() }
    $server.Dispose()
    $client.Dispose()
    $handler.Dispose()
}
