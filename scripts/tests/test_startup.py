#!/usr/bin/env python3
"""CLI safety tests: isolated fixtures, real signatures, no developer data."""
import hashlib
import json
import os
from pathlib import Path
import shutil
import signal
import socket
import subprocess
import tempfile
import time
import unittest

SOURCE = Path(__file__).resolve().parents[2]
BASH = shutil.which('bash') if os.name != 'nt' else None
PWSH = shutil.which('pwsh')
SSL = shutil.which('openssl')
if not SSL and os.name == 'nt':
    candidate = Path(os.environ.get('ProgramFiles', 'C:/Program Files'))/'Git/usr/bin/openssl.exe'
    if candidate.exists(): SSL = str(candidate)

class StartupTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix='easyoa-cli-test-')
        self.addCleanup(self.temp.cleanup)
        self.base = Path(self.temp.name)
        self.root = self.base/'project with spaces'
        self.root.mkdir()
        for name in ['easyoactl','easyoactl.ps1','VERSION','NOTICE','.env.example','LICENSE','README.md','docker-compose.yml','docker-compose.dev.yml']:
            shutil.copy2(SOURCE/name,self.root/name)
        shutil.copytree(SOURCE/'scripts',self.root/'scripts',ignore=shutil.ignore_patterns('__pycache__'))
        shutil.copytree(SOURCE/'infra/nginx/conf.d',self.root/'infra/nginx/conf.d')
        shutil.copy2(SOURCE/'infra/nginx/nginx.conf',self.root/'infra/nginx/nginx.conf')
        (self.root/'integrity').mkdir()
        shutil.copy2(SOURCE/'integrity/README.md',self.root/'integrity/README.md')
        (self.root/'docs').mkdir()
        shutil.copy2(SOURCE/'docs/deployment.md',self.root/'docs/deployment.md')
        for module in ['backend','frontend']: (self.root/module).mkdir()
        shutil.copy2(SOURCE/'backend/pom.xml',self.root/'backend/pom.xml')
        for name in ['package.json','package-lock.json']: shutil.copy2(SOURCE/'frontend'/name,self.root/'frontend'/name)
        (self.root/'frontend/node_modules').mkdir()
        self.bin=self.base/'bin'; self.bin.mkdir(); self.events=self.base/'events'
        self.env=os.environ.copy()
        for key in list(self.env):
            if key.startswith(('EASYOA_','POSTGRES_')): self.env.pop(key)
        self.env.update(PATH=str(self.bin)+os.pathsep+self.env['PATH'],TEST_EVENTS=str(self.events),TEST_ROOT=str(self.root))
        self.tool('docker',"""
import io,json,os,sys,tarfile,zipfile,hashlib,subprocess
from pathlib import Path
a=sys.argv[1:]
with open(os.environ['TEST_EVENTS'],'a') as f:f.write(json.dumps(a)+'\\n')
if '--project-directory' in a and os.environ.get('TEST_PIN_ENV'):
 assert os.environ.get('POSTGRES_PASSWORD')=='synthetic-database-password-2026'
 assert os.environ.get('EASYOA_PROFILE')=='prod'
if 'info' in a and os.environ.get('TEST_DAEMON_FAIL'):sys.exit(1)
if 'config' in a and os.environ.get('TEST_COMPOSE_FAIL'):sys.exit(1)
if 'version' in a:print('Docker Compose version v2.22.0')
elif 'inspect' in a and '-f' in a:print('unhealthy' if os.environ.get('TEST_UNHEALTHY') else 'healthy')
elif 'ps' in a:print('synthetic-container')
elif '--images' in a:print('easyoa-api:v0.2.0\\neasyoa-web:v0.2.0\\npostgres:16-alpine\\nnginx:1.27-alpine')
elif 'save' in a:sys.stdout.buffer.write(b'synthetic image archive')
elif a and a[0]=='create':print('synthetic-container')
elif a and a[0]=='cp':
 p=Path(a[-1])
 if '/app/app.jar' in a[-2]:
  root=p.parent.parent;version='0.1.1' if os.environ.get('TEST_STALE_IMAGE') else (root/'VERSION').read_text(encoding='utf-8').strip()
  der=subprocess.check_output(['openssl','pkey','-pubin','-in',str(root/'integrity/release-public-key.pem'),'-outform','DER'])
  props='version='+version+'\\nrelease-key-fingerprint='+hashlib.sha256(der).hexdigest()+'\\nsource-ref=v'+version+'\\n'
  with zipfile.ZipFile(p,'w') as jar:jar.writestr('BOOT-INF/classes/easyoa-build.properties',props)
 else:p.mkdir(parents=True,exist_ok=True);(p/'index.html').write_text('synthetic frontend')
elif any('pg_dump' in s for s in a):sys.stdout.buffer.write(b'PGDMPsynthetic-database')
elif any('tar -C' in s for s in a):
 with tarfile.open(fileobj=sys.stdout.buffer,mode='w|gz') as tar:
  data=b'attachment fixture';member=tarfile.TarInfo('attachment.txt');member.size=len(data);tar.addfile(member,io.BytesIO(data))
elif any('tar -xzf' in s or 'pg_restore' in s for s in a):sys.stdin.buffer.read()
""")
        self.tool('curl',"""
import os,sys
from pathlib import Path
if os.environ.get('TEST_DEV') and not (Path(os.environ['TEST_ROOT'])/'api-ready').exists():sys.exit(1)
if any('/api/system/about' in a for a in sys.argv):
 import json
 print(json.dumps({'data':{'version':os.environ.get('TEST_API_VERSION') or (Path(os.environ['TEST_ROOT'])/'VERSION').read_text(encoding='utf-8').strip(),'signatureVerified':not bool(os.environ.get('TEST_API_UNVERIFIED'))}},separators=(',',':')))
""")
        self.tool('sleep','import time;time.sleep(0.01)\n')

    def tool(self,name,body):
        path=self.bin/name;path.write_text('#!'+str(Path(shutil.which('python3')).resolve())+'\n'+body,encoding='utf-8',newline='\n');path.chmod(0o755);return path
    def invoke(self,args,timeout=60):
        return subprocess.run(args,cwd=self.base,env=self.env,text=True,encoding='utf-8',capture_output=True,timeout=timeout)
    def cli(self,*args):return self.invoke([BASH,str(self.root/'easyoactl'),*args])
    def commands(self):return [json.loads(line) for line in self.events.read_text(encoding='utf-8').splitlines()] if self.events.exists() else []
    def tls(self):
        directory=self.root/'infra/nginx/certs';directory.mkdir(parents=True,exist_ok=True)
        for name in ['easyoa.crt','easyoa.key']:(directory/name).write_text('synthetic TLS fixture\n',encoding='utf-8',newline='\n')
    def valid_env(self):
        value=(self.root/'.env.example').read_text(encoding='utf-8').replace('CHANGE_ME_STRONG_DB_PASSWORD','synthetic-database-password-2026').replace('CHANGE_ME_AT_LEAST_32_CHARS_RANDOM_SECRET','synthetic-session-secret-for-local-tests-only-2026')
        (self.root/'.env').write_text(value,encoding='utf-8',newline='\n');self.tls();return value
    def keypair(self):
        private=self.base/'temporary-private.pem';public=self.base/'public.pem'
        r=self.invoke([SSL,'genpkey','-algorithm','ed25519','-out',str(private)])
        if r.returncode:self.skipTest('OpenSSL lacks Ed25519')
        self.assertEqual(self.invoke([SSL,'pkey','-in',str(private),'-pubout','-out',str(public)]).returncode,0)
        return private,public
    def signed_bundle(self):
        private,public=self.keypair();output=self.base/'release'
        r=self.invoke([BASH,str(self.root/'scripts/integrity/package-release.sh'),'--version','v0.2.0','--output',str(output),'--signing-key',str(private),'--public-key',str(public)])
        self.assertEqual(r.returncode,0,r.stdout+r.stderr)
        self.assertFalse((output/'.env').exists());self.assertFalse((output/'infra/nginx/certs/easyoa.key').exists())
        self.assertNotIn('build:',(output/'docker-compose.yml').read_text(encoding='utf-8'))
        self.root=output;self.env['TEST_ROOT']=str(output);self.events.unlink();return private,public

    @unittest.skipUnless(BASH,'requires Bash')
    def test_default_help_and_version_do_not_start_or_create_configuration(self):
        for args in [(),('help',),('version',)]:
            r=self.cli(*args);self.assertEqual(r.returncode,0,r.stderr)
        self.assertFalse((self.root/'.env').exists());self.assertFalse(self.events.exists())

    @unittest.skipUnless(BASH,'requires Bash')
    def test_source_cannot_enter_production(self):
        r=self.cli('start');self.assertNotEqual(r.returncode,0)
        self.assertFalse((self.root/'.env').exists());self.assertFalse(any('up' in a for a in self.commands()))

    @unittest.skipUnless(BASH and SSL,'requires Bash/OpenSSL')
    def test_install_repeated_start_preserves_env_and_loads_verified_images(self):
        self.signed_bundle();original=self.valid_env()
        self.env.update(TEST_PIN_ENV='1',POSTGRES_PASSWORD='ambient-incorrect-password',EASYOA_PROFILE='dev')
        for command in ['install','start']:
            r=self.cli(command);self.assertEqual(r.returncode,0,r.stdout+r.stderr)
        self.assertEqual((self.root/'.env').read_text(encoding='utf-8'),original)
        self.assertEqual(sum('load' in a for a in self.commands()),2)
        self.assertTrue(all('--no-build' in a and '--pull' in a and 'never' in a for a in self.commands() if 'up' in a))
        self.assertNotIn('synthetic-database-password',r.stdout+r.stderr)

    @unittest.skipUnless(BASH and SSL,'requires Bash/OpenSSL')
    def test_stale_api_image_is_not_signed_or_packaged(self):
        self.env['TEST_STALE_IMAGE']='1';private,public=self.keypair();output=self.base/'bad release'
        r=self.invoke([BASH,str(self.root/'scripts/integrity/package-release.sh'),'--version','v0.2.0','--output',str(output),'--signing-key',str(private),'--public-key',str(public)])
        self.assertNotEqual(r.returncode,0);self.assertIn('API image build metadata does not match',r.stdout+r.stderr)
        self.assertFalse((output/'integrity/manifest.sig').exists());self.assertFalse(any('save' in a for a in self.commands()))

    @unittest.skipUnless(BASH and SSL,'requires Bash/OpenSSL')
    def test_fresh_install_generates_secrets_with_seed_disabled(self):
        self.signed_bundle();self.tls();r=self.cli('install');self.assertEqual(r.returncode,0,r.stdout+r.stderr)
        values=dict(line.split('=',1) for line in (self.root/'.env').read_text(encoding='utf-8').splitlines() if '=' in line and not line.startswith('#'))
        self.assertGreaterEqual(len(values['EASYOA_SESSION_SECRET']),32);self.assertNotIn('CHANGE_ME',values['POSTGRES_PASSWORD'])
        self.assertEqual(values['EASYOA_DEV_SEED'],'false');self.assertNotIn(values['EASYOA_SESSION_SECRET'],r.stdout+r.stderr)

    @unittest.skipUnless(BASH and SSL,'requires Bash/OpenSSL')
    def test_unsafe_configuration_and_unhealthy_services_fail(self):
        self.signed_bundle();original=self.valid_env()
        for value in [original.replace('EASYOA_DEV_SEED=false','EASYOA_DEV_SEED=true'),(self.root/'.env.example').read_text(encoding='utf-8')]:
            (self.root/'.env').write_text(value,encoding='utf-8',newline='\n');r=self.cli('start');self.assertNotEqual(r.returncode,0);self.assertFalse(any('up' in a for a in self.commands()))
        (self.root/'.env').write_text(original,encoding='utf-8',newline='\n')
        for failure in ['TEST_DAEMON_FAIL','TEST_COMPOSE_FAIL','TEST_UNHEALTHY','TEST_API_UNVERIFIED']:
            self.env[failure]='1';r=self.cli('start');self.assertNotEqual(r.returncode,0,r.stdout+r.stderr);self.env.pop(failure)

    @unittest.skipUnless(BASH and SSL,'requires Bash/OpenSSL')
    def test_tampering_or_missing_metadata_never_starts_services(self):
        _,public=self.signed_bundle();self.valid_env()
        for name in ['integrity/manifest.sig','integrity/manifest.sha256','backend/app.jar','easyoactl','scripts/bootstrap/common.sh']:
            with self.subTest(name=name):
                path=self.root/name;original=path.read_bytes();path.write_bytes(original+b'\nmodified\n')
                # Call the verifier directly if the entry itself was modified.
                r=self.invoke([BASH,str(self.root/'scripts/integrity/verify-integrity.sh'),'--root',str(self.root)])
                self.assertNotEqual(r.returncode,0);path.write_bytes(original)
        for name in ['integrity/manifest.sig','backend/app.jar']:
            path=self.root/name;original=path.read_bytes();path.unlink();r=self.cli('start');self.assertNotEqual(r.returncode,0);path.write_bytes(original)
        extra=self.root/'infra/nginx/conf.d/extra.conf';extra.write_text('# unsigned\n',encoding='utf-8',newline='\n');r=self.cli('start');self.assertNotEqual(r.returncode,0);extra.unlink()
        self.assertFalse(any('up' in a for a in self.commands()))
        if PWSH:
            r=self.invoke([PWSH,'-NoProfile','-File',str(self.root/'scripts/integrity/verify-integrity.ps1'),'-Root',str(self.root)])
            self.assertEqual(r.returncode,0,r.stdout+r.stderr)
            (self.root/'backend/app.jar').write_bytes(b'tamper');r=self.invoke([PWSH,'-NoProfile','-File',str(self.root/'scripts/integrity/verify-integrity.ps1'),'-Root',str(self.root)])
            self.assertNotEqual(r.returncode,0)

    @unittest.skipUnless(BASH and SSL,'requires Bash/OpenSSL')
    def test_status_logs_doctor_stop_restart_and_confirmed_restore(self):
        self.signed_bundle();self.valid_env()
        for args in [('status',),('logs','-f','easyoa-api'),('doctor',),('stop',),('stop',),('restart',),('backup',)]:
            r=self.cli(*args);self.assertEqual(r.returncode,0,r.stdout+r.stderr)
        backup=next((self.root/'backups').iterdir())
        r=self.cli('restore',str(backup));self.assertNotEqual(r.returncode,0)
        self.assertFalse(any(any('pg_restore' in s for s in a) for a in self.commands()))
        r=self.cli('restore',str(backup),'--yes');self.assertEqual(r.returncode,0,r.stdout+r.stderr)
        count=len(self.commands());(backup/'database.dump').write_bytes(b'PGDMPchanged')
        r=self.cli('restore',str(backup),'--yes');self.assertNotEqual(r.returncode,0);self.assertEqual(len(self.commands()),count)
        self.assertFalse(any('down' in a for a in self.commands()))

    @unittest.skipUnless(BASH and SSL,'requires Bash/OpenSSL')
    def test_upgrade_validates_before_backup_and_preserves_old_deployment(self):
        private,_=self.signed_bundle();original=self.valid_env();target=self.base/'new release'
        shutil.copytree(self.root,target,ignore=shutil.ignore_patterns('.env','backups','certs'))
        (target/'VERSION').write_text('0.2.1\n',encoding='utf-8',newline='\n')
        r=self.cli('upgrade',str(target));self.assertNotEqual(r.returncode,0)
        self.assertFalse((self.root/'backups').exists());self.assertFalse((target/'.env').exists())
        r=self.invoke([BASH,str(target/'scripts/integrity/generate-manifest.sh'),'--root',str(target)])
        self.assertEqual(r.returncode,0,r.stdout+r.stderr)
        r=self.invoke([SSL,'pkeyutl','-sign','-inkey',str(private),'-rawin','-in',str(target/'integrity/manifest.sha256'),'-out',str(target/'integrity/manifest.sig')]);self.assertEqual(r.returncode,0,r.stderr)
        self.env['TEST_API_VERSION']='0.2.1'
        r=self.cli('upgrade',str(target));self.assertEqual(r.returncode,0,r.stdout+r.stderr)
        self.assertEqual((self.root/'.env').read_text(encoding='utf-8'),original);self.assertEqual((target/'.env').read_text(encoding='utf-8'),original)
        self.assertTrue((target/'infra/nginx/certs/easyoa.key').exists());self.assertTrue((self.root/'backups').exists())
        self.assertFalse(any('down' in a for a in self.commands()))

    @unittest.skipUnless(BASH,'requires Bash')
    def test_development_cleans_up_only_owned_processes(self):
        self.valid_env();self.tool('java','import sys;print(\'openjdk version "21.0.1"\',file=sys.stderr)\n')
        self.tool('node','import sys;print("v22.12.0") if "--version" in sys.argv else None\n')
        body="""import os,signal,time
from pathlib import Path
root=Path(os.environ['TEST_ROOT']);(root/'api-ready').touch();(root/('pid-'+str(os.getpid()))).write_text(str(os.getpid()))
signal.signal(signal.SIGTERM,lambda *_:exit(0))
while True:time.sleep(0.1)
"""
        mvnw=self.root/'backend/mvnw';mvnw.write_text('#!'+str(Path(shutil.which('python3')).resolve())+'\n'+body,encoding='utf-8',newline='\n');mvnw.chmod(0o755);self.tool('npm',body)
        with socket.socket() as api,socket.socket() as web:
            api.bind(('127.0.0.1',0));web.bind(('127.0.0.1',0));ports=(api.getsockname()[1],web.getsockname()[1])
        self.env.update(TEST_DEV='1',EASYOA_API_PORT=str(ports[0]),EASYOA_DEV_WEB_PORT=str(ports[1]))
        log=self.base/'launcher.log'
        with log.open('w') as f:
            proc=subprocess.Popen([BASH,str(self.root/'easyoactl'),'dev'],cwd=self.base,env=self.env,stdout=f,stderr=f,start_new_session=True)
            try:
                for _ in range(150):
                    if len(list(self.root.glob('pid-*')))==2 and 'development is ready' in log.read_text(encoding='utf-8'):break
                    if proc.poll() is not None:self.fail(log.read_text(encoding='utf-8'))
                    time.sleep(0.1)
                self.assertEqual(len(list(self.root.glob('pid-*'))),2,log.read_text(encoding='utf-8'));proc.send_signal(signal.SIGTERM);proc.wait(timeout=10)
                for marker in self.root.glob('pid-*'):
                    with self.assertRaises(ProcessLookupError):os.kill(int(marker.read_text(encoding='utf-8')),0)
                self.assertFalse(any('down' in a for a in self.commands()))
            finally:
                if proc.poll() is None:os.killpg(proc.pid,signal.SIGKILL);proc.wait()

    @unittest.skipUnless(PWSH,'PowerShell unavailable')
    def test_powershell_default_help_and_configuration_preservation(self):
        r=self.invoke([PWSH,'-NoProfile','-File',str(self.root/'easyoactl.ps1')]);self.assertEqual(r.returncode,0,r.stdout+r.stderr)
        self.assertFalse((self.root/'.env').exists());original=self.valid_env()
        r=self.invoke([PWSH,'-NoProfile','-Command',f". '{self.root}/scripts/bootstrap/common.ps1'; . '{self.root}/scripts/bootstrap/deploy-env.ps1'; Ensure-EnvFile '{self.root}'; Validate-EnvProd '{self.root}'"])
        self.assertEqual(r.returncode,0,r.stdout+r.stderr);self.assertEqual((self.root/'.env').read_text(encoding='utf-8'),original)

    @unittest.skipUnless(PWSH and SSL,'PowerShell/OpenSSL unavailable')
    def test_native_powershell_signature_without_bash(self):
        private,public=self.keypair();shutil.copy2(public,self.root/'integrity/release-public-key.pem')
        (self.root/'backend/app.jar').write_bytes(b'synthetic jar');(self.root/'frontend/dist').mkdir();(self.root/'frontend/dist/index.html').write_text('synthetic web',encoding='utf-8',newline='\n');(self.root/'release-images.tar.gz').write_bytes(b'images')
        paths=[line for line in (self.root/'scripts/integrity/protected-files.txt').read_text(encoding='utf-8').splitlines() if line and not line.startswith('#')]
        paths+=['integrity/README.md']
        manifest=self.root/'integrity/manifest.sha256';manifest.write_bytes(''.join(hashlib.sha256((self.root/p).read_bytes()).hexdigest()+'  '+p+'\n' for p in sorted(set(paths))).encode())
        r=self.invoke([SSL,'pkeyutl','-sign','-inkey',str(private),'-rawin','-in',str(manifest),'-out',str(self.root/'integrity/manifest.sig')]);self.assertEqual(r.returncode,0,r.stderr)
        args=[PWSH,'-NoProfile','-File',str(self.root/'scripts/integrity/verify-integrity.ps1'),'-Root',str(self.root)]
        r=self.invoke(args);self.assertEqual(r.returncode,0,r.stdout+r.stderr)
        (self.root/'backend/app.jar').write_bytes(b'tamper');r=self.invoke(args);self.assertNotEqual(r.returncode,0)

if __name__=='__main__':unittest.main()
