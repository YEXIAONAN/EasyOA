#!/usr/bin/env python3
"""Publish validated assets once. Never update an existing GitHub release."""
import argparse
import hashlib
import http.client
import json
import os
from pathlib import Path
import re
import subprocess
from urllib.parse import quote, urlsplit
from version import validate

class GitHub:
    def __init__(self, token):
        if not token: raise ValueError('GITHUB_TOKEN is required.')
        self.token=token

    def request(self, method, path, data=None, missing_ok=False, file=None):
        url = path if path.startswith('https://') else 'https://api.github.com'+path
        parsed=urlsplit(url)
        if parsed.scheme!='https' or parsed.hostname not in {'api.github.com','uploads.github.com'}:
            raise ValueError('Unexpected GitHub API host.')
        headers={'Authorization':'Bearer '+self.token, 'Accept':'application/vnd.github+json',
                 'X-GitHub-Api-Version':'2022-11-28', 'User-Agent':'EasyOA-Release'}
        connection=http.client.HTTPSConnection(parsed.hostname,timeout=300)
        stream=None
        try:
            if file:
                stream=Path(file).open('rb'); body=stream
                headers.update({'Content-Type':'application/octet-stream','Content-Length':str(Path(file).stat().st_size)})
            else:
                body=json.dumps(data).encode() if data is not None else None
                if body: headers['Content-Type']='application/json'
            target=parsed.path+('?' + parsed.query if parsed.query else '')
            connection.request(method,target,body=body,headers=headers)
            response=connection.getresponse(); payload=response.read()
            if response.status==404 and missing_ok: return None
            if not 200<=response.status<300:
                # Do not print request headers, token, private key, or arbitrary server payload.
                raise RuntimeError(f'GitHub {method} {parsed.path} failed (HTTP {response.status}).')
            return json.loads(payload) if payload else None
        finally:
            if stream: stream.close()
            connection.close()

def repo_path(repo):
    if not re.fullmatch(r'[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+',repo): raise ValueError('Invalid owner/repository.')
    return '/repos/'+repo

def tag_version(tag):
    if not tag.startswith('v'): raise ValueError('Release tags must begin with v.')
    return validate(tag[1:])

def check_existing(client,repo,tag):
    tag_version(tag)
    existing=client.request('GET',repo_path(repo)+'/releases/tags/'+quote(tag,safe=''),missing_ok=True)
    if existing is not None: raise ValueError(f'Release {tag} already exists.')
    # The tag endpoint returns published releases; authenticated listing also
    # catches an abandoned draft, so a retry never creates a duplicate.
    page=1
    while True:
        releases=client.request('GET',repo_path(repo)+f'/releases?per_page=100&page={page}')
        if any(item.get('tag_name')==tag for item in releases):
            raise ValueError(f'Release {tag} already exists.')
        if len(releases)<100: break
        page+=1

def validate_assets(directory,tag):
    tag_version(tag); directory=Path(directory)
    archive='EasyOA-'+tag+'.tar.gz'
    names=[archive,archive+'.sha256',archive+'.sha256.sig','manifest.sha256','manifest.sig','release-public-key.pem']
    if set(p.name for p in directory.iterdir())!=set(names): raise ValueError('Release assets must contain exactly the expected package, checksum, signatures, manifest and public key.')
    for name in names:
        path=directory/name
        if path.is_symlink() or not path.is_file() or not path.stat().st_size: raise ValueError('Missing or unsafe release asset: '+name)
    checksum=(directory/(archive+'.sha256')).read_text()
    with (directory/archive).open('rb') as stream:
        digest=hashlib.file_digest(stream,'sha256').hexdigest()
    expected=digest+'  '+archive+'\n'
    if checksum!=expected: raise ValueError('Release archive checksum does not match.')
    for data,sig in [('manifest.sha256','manifest.sig'),(archive+'.sha256',archive+'.sha256.sig')]:
        result=subprocess.run(['openssl','pkeyutl','-verify','-pubin','-inkey',str(directory/'release-public-key.pem'),
            '-rawin','-in',str(directory/data),'-sigfile',str(directory/sig)],capture_output=True)
        if result.returncode: raise ValueError('Invalid release signature: '+sig)
    return names

def publish(client,repo,tag,directory):
    names=validate_assets(directory,tag)
    check_existing(client,repo,tag) # Check again immediately before creation.
    base=repo_path(repo); release=None
    try:
        # Assets are staged privately; only promote after uploads are confirmed.
        release=client.request('POST',base+'/releases',{'tag_name':tag,'name':'EasyOA '+tag,
            'draft':True,'prerelease':'-' in tag[1:],'generate_release_notes':True})
        upload=release['upload_url'].split('{',1)[0]
        for name in names:
            asset=client.request('POST',upload+'?name='+quote(name,safe=''),file=Path(directory)/name)
            if asset['name']!=name or asset['size']!=(Path(directory)/name).stat().st_size or asset['state']!='uploaded':
                raise RuntimeError('Release asset upload verification failed: '+name)
        uploaded=client.request('GET',base+f"/releases/{release['id']}/assets")
        if {a['name'] for a in uploaded}!=set(names): raise RuntimeError('GitHub release asset list is incomplete.')
        result=client.request('PATCH',base+f"/releases/{release['id']}",{'draft':False,'prerelease':'-' in tag[1:]})
        if result.get('draft') is not False: raise RuntimeError('GitHub release was not published.')
        print('Published: '+result['html_url'])
    except Exception:
        # Delete only the incomplete draft created by this invocation; never touch existing releases.
        if release is not None:
            try:
                current=client.request('GET',base+f"/releases/{release['id']}")
                if current.get('draft') is True: client.request('DELETE',base+f"/releases/{release['id']}")
            except Exception:
                print('Could not clean up the incomplete draft; inspect GitHub Releases before retrying.')
        raise

if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('command',choices=['check','publish']); parser.add_argument('--repo',required=True)
    parser.add_argument('--tag',required=True); parser.add_argument('--assets',type=Path)
    args=parser.parse_args()
    try:
        client=GitHub(os.environ.get('GITHUB_TOKEN'))
        if args.command=='check': check_existing(client,args.repo,args.tag); print('No existing release: '+args.tag)
        else:
            if not args.assets: raise ValueError('--assets is required.')
            publish(client,args.repo,args.tag,args.assets)
    except (ValueError,RuntimeError,OSError) as error: parser.exit(1,str(error)+'\n')
