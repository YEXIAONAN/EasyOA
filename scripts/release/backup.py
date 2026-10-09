#!/usr/bin/env python3
"""Validate a local backup without executing its configuration or SQL."""
import argparse
import hashlib
from pathlib import Path
from archive import validate as validate_archive
from version import validate as validate_version

REQUIRED={'.env','VERSION','FORMAT','database.dump','storage.tar.gz'}
ALLOWED=REQUIRED|{'easyoa.crt','easyoa.key'}

def env_values(path):
    result={}
    for line in Path(path).read_text().splitlines():
        line=line.strip()
        if not line or line.startswith('#'): continue
        if '=' not in line: raise ValueError('Malformed backup environment configuration.')
        key,value=line.split('=',1)
        if key in result: raise ValueError('Duplicate backup environment variable.')
        result[key]=value.strip().strip('"').strip("'")
    return result

def checksum(path):
    with path.open('rb') as stream: return hashlib.file_digest(stream,'sha256').hexdigest()

def files(root):
    names={p.name for p in root.iterdir()}-{'checksums.sha256'}
    if not REQUIRED<=names<=ALLOWED: raise ValueError('Backup contents are incomplete or unexpected.')
    for name in names:
        p=root/name
        if p.is_symlink() or not p.is_file() or not p.stat().st_size: raise ValueError('Unsafe or empty backup file: '+name)
    return sorted(names)

def write(root):
    text=''.join(checksum(root/name)+'  '+name+'\n' for name in files(root))
    (root/'checksums.sha256').write_text(text); (root/'checksums.sha256').chmod(0o600)

def check(root,current):
    names=files(root)
    expected=''.join(checksum(root/name)+'  '+name+'\n' for name in names)
    if (root/'checksums.sha256').is_symlink() or (root/'checksums.sha256').read_text()!=expected: raise ValueError('Backup checksum mismatch.')
    if (root/'FORMAT').read_text().strip()!='1': raise ValueError('Unsupported backup format.')
    old=validate_version((root/'VERSION').read_text().strip()); new=validate_version((current/'VERSION').read_text().strip())
    if tuple(map(int,old.split('-')[0].split('.')))>tuple(map(int,new.split('-')[0].split('.'))): raise ValueError('A newer database backup cannot be restored into an older application.')
    before=env_values(root/'.env'); target=env_values(current/'.env')
    if any(before.get(k)!=target.get(k) for k in ['POSTGRES_DB','POSTGRES_USER','POSTGRES_PASSWORD']):
        raise ValueError('Backup database identity/credentials differ. Prepare the matching deployment configuration before restore.')
    with (root/'database.dump').open('rb') as stream:
        if stream.read(5)!=b'PGDMP': raise ValueError('Backup is not a PostgreSQL custom-format dump.')
    validate_archive(root/'storage.tar.gz')

if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__); parser.add_argument('command',choices=['write','check'])
    parser.add_argument('backup',type=Path); parser.add_argument('--current',type=Path); args=parser.parse_args()
    try:
        if args.command=='write': write(args.backup)
        else:
            if not args.current: raise ValueError('--current is required.')
            check(args.backup,args.current)
        print('Backup checks passed.')
    except (ValueError,OSError) as error: parser.exit(1,str(error)+'\n')
