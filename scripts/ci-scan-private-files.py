"""Scan tracked source/history names without displaying file contents or private material."""
from pathlib import Path
import re
import subprocess
from ci_security import require

FORBIDDEN=re.compile(r"(?:\.(?:jks|keystore|p12|pfx|key)$|(?:^|/)private-signing/|signing-PRIVATE\.tar\.gz$)",re.I)
PEM=re.compile(rb"(?m)^-----BEGIN (?:RSA |EC |DSA |OPENSSH |ENCRYPTED )?PRIVATE KEY-----\r?$")


def scan():
    files=subprocess.run(["git","ls-files","-z"],check=True,capture_output=True).stdout.split(b"\0")
    problems=[]
    for raw in filter(None,files):
        name=raw.decode();p=Path(name)
        if FORBIDDEN.search(name):problems.append(name)
        if p.is_file() and not p.is_symlink():
            content=p.read_bytes()
            if PEM.search(content) or content[:4]==b"\xfe\xed\xfe\xed":problems.append(name)
    history=subprocess.run(["git","rev-list","--objects","--all"],check=True,capture_output=True,text=True).stdout.splitlines()
    names=[line.split(" ",1)[1] for line in history if " " in line]
    problems.extend(name for name in names if FORBIDDEN.search(name))
    require(not problems,"Potential private signing files detected (contents suppressed): "+", ".join(sorted(set(problems))))
    print(f"No tracked private-key paths/PEM/JKS content found; checked {len(files)-1} current files and {len(names)} locally available history paths. Not a GitHub secret-scanning service.")


if __name__=="__main__":scan()
