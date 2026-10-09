#!/usr/bin/env python3
"""VERSION is authoritative. Check build manifests and a release tag, or sync them."""
import argparse
import json
from pathlib import Path
import re
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
SEMVER = re.compile(r'(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)(?:-([0-9A-Za-z-]+(?:\.[0-9A-Za-z-]+)*))?')

def validate(value):
    match = SEMVER.fullmatch(value)
    if not match or any(x.isdigit() and len(x)>1 and x[0]=='0' for x in (match[4] or '').split('.')):
        raise ValueError('Invalid semantic version: '+value)
    return value

def check(root=ROOT, tag=None):
    version = validate((root/'VERSION').read_text().strip())
    if tag is not None and tag != 'v'+version:
        raise ValueError(f'Tag {tag} does not match VERSION {version}.')
    ns = {'m':'http://maven.apache.org/POM/4.0.0'}
    backend = ET.parse(root/'backend/pom.xml').getroot().find('m:version',ns).text
    frontend = json.loads((root/'frontend/package.json').read_text())['version']
    lock = json.loads((root/'frontend/package-lock.json').read_text())
    for name,value in [('backend',backend),('frontend',frontend),('lockfile',lock['version']),('lockfile root',lock['packages']['']['version'])]:
        if value != version: raise ValueError(f'{name} version {value} differs from VERSION {version}. Run scripts/release/version.py --sync.')
    return version

def sync(root=ROOT):
    version = validate((root/'VERSION').read_text().strip())
    pom = root/'backend/pom.xml'
    text,count = re.subn(r'(<artifactId>easyoa-api</artifactId>\s*<version>)[^<]+',lambda m:m[1]+version,pom.read_text(),count=1)
    if count!=1: raise ValueError('Cannot locate backend project version')
    pom.write_text(text)
    for name in ['package.json','package-lock.json']:
        path=root/'frontend'/name; data=json.loads(path.read_text()); data['version']=version
        if name=='package-lock.json': data['packages']['']['version']=version
        path.write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n')
    return check(root)

if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--tag'); parser.add_argument('--sync',action='store_true'); args=parser.parse_args()
    try:
        if args.sync: sync()
        print('Version validated: v'+check(tag=args.tag))
    except (ValueError,KeyError,ET.ParseError) as error: parser.exit(1,str(error)+'\n')
