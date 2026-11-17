# YTM Starcast. Copyright (C) 2026 dash1101. SPDX-License-Identifier: GPL-3.0-or-later
# Additional terms under GPL section 7: see NOTICE.
"""Running other programs without a console window popping up.

On Windows, YTM Starcast and its desktop window have no console of their own, so every console program they start
(tailscale, powershell, taskkill, gh ...) would otherwise open a window of its own for a moment."""
import sys

CREATE_NO_WINDOW = 0x08000000


def no_window(platform=None):
    """Keyword arguments for subprocess.run / Popen that keep a console program's window from showing (Windows only;
    nothing elsewhere)."""
    return {"creationflags": CREATE_NO_WINDOW} if (platform or sys.platform) == "win32" else {}
