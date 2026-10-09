$env:SSH_ASKPASS = "c:\Users\king\Documents\trae_projects\nac\askpass.cmd"
$env:SSH_ASKPASS_REQUIRE = "force"

function rk($cmd) {
  ssh -o StrictHostKeyChecking=no -o UserKnownHostsFile=NUL mao@192.168.80.36 $cmd
}
function rksudo($cmd) {
  $inner = $cmd -replace '"','\"'
  ssh -o StrictHostKeyChecking=no -o UserKnownHostsFile=NUL mao@192.168.80.36 "echo 'MAO5201314sm' | sudo -S -p '' bash -c `"$inner`""
}
function up($local, $remote) {
  $item = Get-Item (Resolve-Path $local)
  Push-Location $item.DirectoryName
  try {
    scp -o StrictHostKeyChecking=no -o UserKnownHostsFile=NUL $item.Name "mao@192.168.80.36`:$remote"
  } finally { Pop-Location }
}
