Add-Type -AssemblyName UIAutomationClient
Add-Type -AssemblyName UIAutomationTypes

$proc = Get-Process -Name '8021x-client' -ErrorAction Stop | Select-Object -First 1
$root = [System.Windows.Automation.AutomationElement]::RootElement
$cond = New-Object System.Windows.Automation.PropertyCondition([System.Windows.Automation.AutomationElement]::ProcessIdProperty, $proc.Id)
$win = $root.FindFirst([System.Windows.Automation.TreeScope]::Children, $cond)
if (-not $win) { Write-Output 'WINDOW-NOT-FOUND'; exit 1 }

$all = $win.FindAll([System.Windows.Automation.TreeScope]::Descendants, [System.Windows.Automation.Condition]::TrueCondition)

# close modal dialogs (OK buttons outside main central widget)
foreach ($e in $all) {
    $c = $e.Current
    if ($c.Name -eq 'OK' -and $c.ControlType.ProgrammaticName -like '*Button*' -and $c.AutomationId -eq '') {
        try { ($e.GetCurrentPattern([System.Windows.Automation.InvokePattern]::Pattern)).Invoke(); Write-Output 'DIALOG-OK-CLICKED'; Start-Sleep -Milliseconds 600 } catch {}
    }
}

$caEdit = $null; $btn = $null
foreach ($e in $all) {
    $c = $e.Current
    if (-not $caEdit -and $c.AutomationId -like '*editCaPath') { $caEdit = $e }
    if (-not $btn -and $c.AutomationId -like '*btnStart') { $btn = $e }
}
if (-not $caEdit) { Write-Output 'CA-EDIT-NOT-FOUND'; exit 1 }
Write-Output ('CA-EDIT value=' + $caEdit.Current.Value)

$vp = $caEdit.GetCurrentPattern([System.Windows.Automation.ValuePattern]::Pattern)
$vp.SetValue('C:\nactemp\peap-ca.pem')
Start-Sleep -Milliseconds 500
Write-Output ('CA-SET ' + $caEdit.Current.Value)

if ($btn) {
    $ip = $btn.GetCurrentPattern([System.Windows.Automation.InvokePattern]::Pattern)
    $ip.Invoke()
    Write-Output ('START-CLICKED name=' + $btn.Current.Name)
} else {
    Write-Output 'START-BUTTON-NOT-FOUND'
}
