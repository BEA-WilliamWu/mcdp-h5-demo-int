"""Run the minimal 2110 change against the existing 851 and approval regressions.

The production contact-notification method is executed against a BCO baseline,
with real SDK ActivityData snapshots and external-recipient/SMS routing code.
Bank data, configuration and transport are fixtures; no live notifications are sent.
The restored shared SMS/approval classes are also compiled for Java 8.
"""
from pathlib import Path
import subprocess
import sys

here = Path(__file__).resolve().parent
for script in ("verify_hth_851_approver.py", "verify_hth_851_compile.py", "verify_hth_code_approval.py"):
    subprocess.run([sys.executable, str(here / script)], check=True)
print("PASS: minimal 2110 BCO parity, existing single-field/PIN behavior and approval regression")
print("NOTE: Oracle configuration, live approval and actual MNG delivery still require UAT verification")
