param(
    [switch]$Force,
    [switch]$Quiet
)

$message = "[MooNWide] GitHub auto-update is disabled in the Steam package. Update through Steam or replace the package manually."
if(-not $Quiet) {
    Write-Host $message
}
exit 0
