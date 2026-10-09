#!/usr/bin/env python3
"""Release boundaries, real cryptography, publisher API protocol doubles."""
import io
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tarfile
import tempfile
import unittest
from unittest.mock import patch

SOURCE=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(SOURCE/'scripts/release'))
import archive
import publish
import version
SSL=shutil.which('openssl')
BASH=shutil.which('bash') if os.name!='nt' else None

class FakeGitHub:
    def __init__(self,exists=False,fail_upload=False,draft_exists=False):
        self.calls=[];self.exists=exists;self.fail_upload=fail_upload;self.assets=[];self.draft=True;self.draft_exists=draft_exists
    def request(self,method,path,data=None,missing_ok=False,file=None):
        self.calls.append((method,path,data))
        if '/releases/tags/' in path:return {'id':1} if self.exists else None
        if method=='POST' and path.endswith('/releases'):return {'id':123,'upload_url':'https://uploads.github.com/repos/test/EasyOA/releases/123/assets{?name,label}'}
        if file:
            if self.fail_upload:raise RuntimeError('Upload failed')
            value={'name':Path(file).name,'size':Path(file).stat().st_size,'state':'uploaded'};self.assets.append(value);return value
        if method=='GET' and '/releases?per_page=' in path:return [{'tag_name':'v0.2.0','draft':True}] if self.draft_exists else []
        if method=='GET' and path.endswith('/assets'):return self.assets
        if method=='PATCH':self.draft=False;return {'draft':False,'html_url':'https://github.com/test/EasyOA/releases/tag/v0.2.0'}
        if method=='GET':return {'draft':self.draft}
        if method=='DELETE':return None
        raise AssertionError((method,path))

class VersionTests(unittest.TestCase):
    def test_all_version_sources_and_tag_match(self):
        self.assertEqual(version.check(SOURCE,'v0.2.0'),'0.2.0')
        with self.assertRaises(ValueError):version.check(SOURCE,'v0.2.1')
    def test_semantic_versions_include_prereleases_and_reject_invalid_numbers(self):
        for v in ['0.2.0','1.0.0','0.2.0-beta.1','0.2.0-rc.1']:self.assertEqual(version.validate(v),v)
        for v in ['v0.2.0','0.2','01.2.0','0.2.0-beta.01','0.2.0-','0.2.0;whoami']:
            with self.assertRaises(ValueError):version.validate(v)
    def test_workflow_is_tag_only_and_ci_gates_publication(self):
        release=(SOURCE/'.github/workflows/release.yml').read_text(encoding='utf-8');ci=(SOURCE/'.github/workflows/ci.yml').read_text(encoding='utf-8')
        self.assertIn("- 'v*.*.*'",release);self.assertIn('needs: [validate, ci]',release)
        self.assertIn('secrets.EASYOA_RELEASE_SIGNING_KEY',release);self.assertIn('contents: write',release)
        self.assertNotIn('continue-on-error',release);self.assertNotIn('tags:',ci);self.assertIn('workflow_call:',ci)

    def test_version_sync_preserves_utf8_under_windows_default_encoding(self):
        with tempfile.TemporaryDirectory() as directory:
            root=Path(directory);(root/'backend').mkdir();(root/'frontend').mkdir()
            (root/'VERSION').write_bytes(b'0.2.0\n')
            (root/'backend/pom.xml').write_bytes('<project xmlns="http://maven.apache.org/POM/4.0.0"><!-- 构建配置 --><artifactId>easyoa-api</artifactId><version>0.1.1</version></project>'.encode('utf-8'))
            for name in ['package.json','package-lock.json']:
                value={'name':'示例项目','version':'0.1.1','packages':{'':{'version':'0.1.1'}}}
                (root/'frontend'/name).write_bytes(json.dumps(value,ensure_ascii=False).encode('utf-8'))
            original=Path.open
            def windows_default(path,mode='r',buffering=-1,encoding=None,errors=None,newline=None):
                if 'b' not in mode and encoding in (None,'locale'):encoding='cp1252'
                return original(path,mode,buffering,encoding,errors,newline)
            with patch.object(Path,'open',windows_default):
                self.assertEqual(version.sync(root),'0.2.0')
                self.assertEqual(version.check(root,'v0.2.0'),'0.2.0')
            self.assertIn('构建配置',(root/'backend/pom.xml').read_bytes().decode('utf-8'))
            self.assertEqual(json.loads((root/'frontend/package.json').read_bytes())['name'],'示例项目')

class ArchiveTests(unittest.TestCase):
    def setUp(self):
        self.temp=tempfile.TemporaryDirectory();self.addCleanup(self.temp.cleanup);self.root=Path(self.temp.name)
    def create(self,name,kind=tarfile.REGTYPE):
        path=self.root/'archive.tar.gz'
        with tarfile.open(path,'w:gz') as tar:
            member=tarfile.TarInfo(name);member.type=kind;member.size=1 if kind==tarfile.REGTYPE else 0;member.linkname='/tmp/escape'
            tar.addfile(member,io.BytesIO(b'x') if member.size else None)
        return path
    def test_unsafe_paths_and_links_rejected_before_extraction(self):
        for name,kind in [('../escape',tarfile.REGTYPE),('/absolute',tarfile.REGTYPE),('root/../escape',tarfile.REGTYPE),('C:/drive',tarfile.REGTYPE),('root\\escape',tarfile.REGTYPE),('root/link',tarfile.SYMTYPE),('root/link',tarfile.LNKTYPE)]:
            with self.subTest(name=name,kind=kind):
                path=self.create(name,kind);destination=self.root/'extract'
                with self.assertRaises(ValueError):archive.extract(path,destination,'root')
                self.assertFalse(destination.exists())
    @unittest.skipIf(os.name=='nt','Unix permission semantics')
    def test_private_download_umask_does_not_hide_release_files_from_containers(self):
        path=self.create('root/integrity/manifest.sha256');destination=self.root/'extract'
        previous=os.umask(0o077)
        try:archive.extract(path,destination,'root')
        finally:os.umask(previous)
        self.assertEqual((destination/'root').stat().st_mode&0o777,0o755)
        self.assertEqual((destination/'root/integrity').stat().st_mode&0o777,0o755)
        self.assertEqual((destination/'root/integrity/manifest.sha256').stat().st_mode&0o777,0o644)
    def test_authenticated_archive_extracts_and_existing_destination_is_preserved(self):
        path=self.create('root/safe.txt');destination=self.root/'extract';archive.extract(path,destination,'root')
        self.assertEqual((destination/'root/safe.txt').read_bytes(),b'x')
        with self.assertRaises(ValueError):archive.extract(path,destination,'root')
        self.assertEqual((destination/'root/safe.txt').read_bytes(),b'x')

    def test_portable_release_archive_has_only_payload_and_is_installable(self):
        source=self.root/'EasyOA-v0.2.0';source.mkdir();payload=source/'VERSION';payload.write_text('0.2.0\n',encoding='utf-8',newline='\n')
        if hasattr(os,'setxattr'):
            try:os.setxattr(payload,'user.easyoa-validation',b'local metadata')
            except OSError:pass
        path=self.root/'portable.tar.gz';archive.create(path,source)
        with tarfile.open(path,'r:gz') as tar:
            self.assertEqual(tar.getnames(),['EasyOA-v0.2.0','EasyOA-v0.2.0/VERSION'])
            self.assertTrue(all(m.uid==0 and m.gid==0 for m in tar))
        archive.extract(path,self.root/'installed',source.name)
        self.assertEqual((self.root/'installed'/source.name/'VERSION').read_text(encoding='utf-8'),'0.2.0\n')

@unittest.skipUnless(SSL,'OpenSSL unavailable')
class ReleaseTests(unittest.TestCase):
    def setUp(self):
        self.temp=tempfile.TemporaryDirectory();self.addCleanup(self.temp.cleanup);self.root=Path(self.temp.name)
        self.assets=self.root/'assets';self.assets.mkdir();self.private=self.root/'temporary.private.pem'
        self.ssl('genpkey','-algorithm','ed25519','-out',self.private)
        self.ssl('pkey','-in',self.private,'-pubout','-out',self.assets/'release-public-key.pem')
        self.make_assets('v0.2.0')
    def ssl(self,*args):
        r=subprocess.run([SSL,*map(str,args)],capture_output=True)
        if r.returncode:self.fail(r.stderr.decode())
    def make_assets(self,tag):
        for p in self.assets.iterdir():
            if p.name!='release-public-key.pem':p.unlink()
        name='EasyOA-'+tag+'.tar.gz';(self.assets/name).write_bytes(b'synthetic archive')
        (self.assets/(name+'.sha256')).write_text(__import__('hashlib').sha256(b'synthetic archive').hexdigest()+'  '+name+'\n',encoding='utf-8',newline='\n')
        (self.assets/'manifest.sha256').write_text('synthetic manifest\n',encoding='utf-8',newline='\n')
        for data,sig in [(name+'.sha256',name+'.sha256.sig'),('manifest.sha256','manifest.sig')]:
            self.ssl('pkeyutl','-sign','-inkey',self.private,'-rawin','-in',self.assets/data,'-out',self.assets/sig)
    def test_valid_assets_and_normal_release_publish_only_after_all_uploads(self):
        client=FakeGitHub();publish.publish(client,'test/EasyOA','v0.2.0',self.assets)
        posts=[c for c in client.calls if c[0]=='POST' and c[1].endswith('/releases')]
        self.assertEqual(len(posts),1);self.assertEqual(posts[0][2]['name'],'EasyOA v0.2.0')
        self.assertTrue(posts[0][2]['generate_release_notes']);self.assertFalse(posts[0][2]['prerelease'])
        self.assertEqual(len(client.assets),6);self.assertEqual(client.calls[-1][0],'PATCH');self.assertFalse(client.calls[-1][2]['draft'])
    def test_prerelease_is_published_as_prerelease(self):
        self.make_assets('v0.2.0-beta.1');client=FakeGitHub();publish.publish(client,'test/EasyOA','v0.2.0-beta.1',self.assets)
        self.assertTrue(client.calls[-1][2]['prerelease']);self.assertFalse(client.calls[-1][2]['draft'])
    def test_existing_release_never_overwritten(self):
        client=FakeGitHub(exists=True)
        with self.assertRaisesRegex(ValueError,'Release v0.2.0 already exists'):publish.publish(client,'test/EasyOA','v0.2.0',self.assets)
        self.assertTrue(all(c[0]=='GET' for c in client.calls))
    def test_an_existing_draft_also_blocks_duplicate_creation(self):
        client=FakeGitHub(draft_exists=True)
        with self.assertRaisesRegex(ValueError,'Release v0.2.0 already exists'):publish.publish(client,'test/EasyOA','v0.2.0',self.assets)
        self.assertTrue(all(c[0]=='GET' for c in client.calls))
    def test_checksum_or_either_signature_failure_prevents_any_github_creation(self):
        for name in ['EasyOA-v0.2.0.tar.gz','EasyOA-v0.2.0.tar.gz.sha256.sig','manifest.sig']:
            self.make_assets('v0.2.0');(self.assets/name).write_bytes(b'corrupted');client=FakeGitHub()
            with self.assertRaises(ValueError):publish.publish(client,'test/EasyOA','v0.2.0',self.assets)
            self.assertEqual(client.calls,[])
    def test_upload_failure_removes_only_own_incomplete_draft(self):
        client=FakeGitHub(fail_upload=True)
        with self.assertRaises(RuntimeError):publish.publish(client,'test/EasyOA','v0.2.0',self.assets)
        self.assertFalse(any(c[0]=='PATCH' for c in client.calls));self.assertEqual(client.calls[-1][:2],('DELETE','/repos/test/EasyOA/releases/123'))
    def test_missing_token_and_unexpected_upload_host_fail(self):
        with self.assertRaises(ValueError):publish.GitHub('')
        with self.assertRaises(ValueError):publish.GitHub('synthetic-token').request('POST','https://example.org/leak')
    @unittest.skipUnless(BASH,'requires Bash')
    def test_quick_installer_checks_signature_before_extracting_or_executing(self):
        binary=self.root/'bin';binary.mkdir();curl=binary/'curl'
        curl.write_text('#!'+sys.executable+'\nimport os,sys,shutil\nfrom pathlib import Path\na=sys.argv[1:];shutil.copy2(Path(os.environ["TEST_ASSETS"])/a[-3].rsplit("/",1)[-1],a[-1])\n',encoding='utf-8',newline='\n');curl.chmod(0o755)
        env=os.environ.copy();env.update(PATH=str(binary)+os.pathsep+env['PATH'],TEST_ASSETS=str(self.assets))
        for asset in ['manifest.sig','EasyOA-v0.2.0.tar.gz.sha256.sig','EasyOA-v0.2.0.tar.gz']:
            self.make_assets('v0.2.0');(self.assets/asset).write_bytes(b'tampered');destination=self.root/'install'
            r=subprocess.run([BASH,str(SOURCE/'scripts/quick_start.sh'),'--tag','v0.2.0','--public-key',str(self.assets/'release-public-key.pem'),'--destination',str(destination)],env=env,text=True,encoding='utf-8',capture_output=True)
            self.assertNotEqual(r.returncode,0);self.assertFalse(destination.exists());self.assertNotIn('Archive safety checks passed',r.stdout)

if __name__=='__main__':unittest.main()
