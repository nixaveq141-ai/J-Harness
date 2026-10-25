"""Verify an actual unlocked/locked collection before classifying native probe evidence."""
import subprocess
import sys
import time

def check(locked):
    deadline=time.monotonic()+15
    while time.monotonic()<deadline:
        try:
            owner=subprocess.check_output(['dbus-send','--session','--print-reply','--dest=org.freedesktop.DBus','/org/freedesktop/DBus','org.freedesktop.DBus.GetNameOwner','string:org.freedesktop.secrets'],text=True,timeout=2)
            value=subprocess.check_output(['dbus-send','--session','--print-reply','--dest=org.freedesktop.secrets','/org/freedesktop/secrets/collection/login','org.freedesktop.DBus.Properties.Get','string:org.freedesktop.Secret.Collection','string:Locked'],text=True,timeout=2)
            if 'string ":' in owner and ('boolean '+str(locked).lower()) in value:return
        except (OSError,subprocess.SubprocessError):pass
        time.sleep(.1)
    raise RuntimeError('Secret Service expected collection state not ready')

if __name__=='__main__':
    if len(sys.argv)!=2 or sys.argv[1] not in ['ready','locked']:raise ValueError('Expected ready/locked')
    check(sys.argv[1]=='locked')
