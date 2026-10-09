#!/usr/bin/env python3
"""Create portable releases and validate before extraction; no escapes or links."""
import argparse
import os
from pathlib import Path, PurePosixPath
import shutil
import tarfile

def checked_members(archive, prefix=None):
    members=[]; seen=set(); total=0
    for member in archive:
        name=member.name
        parts=PurePosixPath(name).parts
        if name.startswith('/') or '\\' in name or any(ord(c)<32 for c in name) or '..' in parts or ':' in name:
            raise ValueError('Unsafe archive path: '+name)
        if member.issym() or member.islnk() or not (member.isfile() or member.isdir()):
            raise ValueError('Archive links and special files are forbidden: '+name)
        if prefix and (not parts or parts[0]!=prefix): raise ValueError('Unexpected release archive root: '+name)
        normalized=str(PurePosixPath(name))
        if normalized in seen: raise ValueError('Duplicate archive member: '+name)
        seen.add(normalized); total+=member.size; members.append(member)
        if len(members)>100000 or total>12*1024**3: raise ValueError('Archive exceeds extraction limits.')
    if not members: raise ValueError('Archive is empty.')
    return members,total

def validate(path,prefix=None):
    with tarfile.open(path,'r:gz') as archive: return checked_members(archive,prefix)[1]

def create(path,source):
    path,source=Path(path),Path(source)
    if path.exists() or path.is_symlink(): raise ValueError('Archive output must not exist.')
    if source.is_symlink() or not source.is_dir(): raise ValueError('Release source must be a directory, not a link.')
    if path.resolve().is_relative_to(source.resolve()): raise ValueError('Archive output must be outside the release directory.')
    try:
        # Python writes only the explicit payload, never macOS AppleDouble,
        # resource forks or extended attributes injected by the system tar.
        with tarfile.open(path,'x:gz',dereference=True) as output:
            for item in [source,*sorted(source.rglob('*'))]:
                if item.is_symlink() or not (item.is_dir() or item.is_file()):
                    raise ValueError('Release links and special files are forbidden: '+str(item))
                name=source.name if item==source else source.name+'/'+item.relative_to(source).as_posix()
                member=output.gettarinfo(str(item),arcname=name)
                member.uid=member.gid=0; member.uname=member.gname='root'; member.pax_headers={}
                member.mode=0o755 if member.isdir() or member.mode&0o111 else 0o644
                if member.isfile():
                    with item.open('rb') as stream: output.addfile(member,stream)
                else: output.addfile(member)
        validate(path,source.name)
    except Exception:
        path.unlink(missing_ok=True)
        raise

def extract(path,destination,prefix=None):
    destination=Path(destination)
    if destination.exists(): raise ValueError('Extraction directory must not exist.')
    with tarfile.open(path,'r:gz') as archive:
        members,total=checked_members(archive,prefix)
        if shutil.disk_usage(destination.parent).free<total+64*1024**2: raise ValueError('Insufficient free space for extraction.')
        destination.mkdir(mode=0o700)
        try:
            for member in members:
                target=destination.joinpath(*PurePosixPath(member.name).parts)
                if member.isdir(): target.mkdir(parents=True,exist_ok=True,mode=0o755)
                else:
                    target.parent.mkdir(parents=True,exist_ok=True,mode=0o755)
                    with archive.extractfile(member) as source, target.open('xb') as output: shutil.copyfileobj(source,output)
                    target.chmod(0o755 if member.mode & 0o111 else 0o644)
            # The installer runs with umask 077; public package directories
            # must remain readable by non-root container users after moving.
            for directory in destination.rglob('*'):
                if directory.is_dir(): directory.chmod(0o755)
            destination.chmod(0o755)
        except Exception:
            shutil.rmtree(destination)
            raise

if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('command',choices=['create','validate','extract']); parser.add_argument('archive',type=Path)
    parser.add_argument('--destination',type=Path); parser.add_argument('--prefix'); parser.add_argument('--source',type=Path)
    args=parser.parse_args()
    try:
        if args.command=='create':
            if not args.source: raise ValueError('--source is required.')
            create(args.archive,args.source)
        elif args.command=='validate': validate(args.archive,args.prefix)
        else:
            if not args.destination: raise ValueError('--destination is required.')
            extract(args.archive,args.destination,args.prefix)
        print('Archive safety checks passed.')
    except (ValueError,OSError,tarfile.TarError) as error: parser.exit(1,str(error)+'\n')
