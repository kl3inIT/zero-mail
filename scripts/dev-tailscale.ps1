[CmdletBinding()]
param(
    [ValidateSet("serve", "funnel", "status", "reset")]
    [string]$Mode = "serve",

    [string]$Target = "3000",

    [switch]$SkipUp
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Invoke-Tailscale {
    param(
        [Parameter(Mandatory = $true)]
        [string[]]$Arguments
    )

    & tailscale @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "tailscale $($Arguments -join ' ') failed with exit code $LASTEXITCODE"
    }
}

if (-not (Get-Command tailscale -ErrorAction SilentlyContinue)) {
    throw "tailscale CLI was not found on PATH. Install Tailscale or add tailscale.exe to PATH."
}

switch ($Mode) {
    "status" {
        Invoke-Tailscale -Arguments @("status")
        Invoke-Tailscale -Arguments @("serve", "status")
        Invoke-Tailscale -Arguments @("funnel", "status")
        return
    }

    "reset" {
        Invoke-Tailscale -Arguments @("serve", "reset")
        Invoke-Tailscale -Arguments @("funnel", "reset")
        return
    }
}

if (-not $SkipUp) {
    & tailscale status *> $null
    if ($LASTEXITCODE -ne 0) {
        Invoke-Tailscale -Arguments @("up")
    }
}

switch ($Mode) {
    "serve" {
        Invoke-Tailscale -Arguments @("serve", "--bg", "--yes", $Target)
        Invoke-Tailscale -Arguments @("serve", "status")
        return
    }

    "funnel" {
        Invoke-Tailscale -Arguments @("funnel", "--bg", "--yes", $Target)
        Invoke-Tailscale -Arguments @("funnel", "status")
        return
    }
}
