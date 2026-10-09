#!/usr/bin/env python3
"""Exercise the actual Nginx config, including proxy header inheritance."""
import json
import os
from pathlib import Path
import shutil
import ssl
import subprocess
import tempfile
import time
import unittest
import urllib.request
import urllib.error
import uuid

SOURCE=Path(__file__).resolve().parents[2]
@unittest.skipUnless(os.environ.get('EASYOA_TEST_DOCKER_PROXY')=='1','Run explicitly with an available Docker daemon')
class ProxyTests(unittest.TestCase):
    def test_edge_overrides_spoofed_headers_and_forwards_a_valid_host(self):
        def docker(*args):return subprocess.check_output(['docker',*map(str,args)],stderr=subprocess.STDOUT,text=True).strip()
        identifier='easyoa-proxy-test-'+uuid.uuid4().hex[:10];backend=identifier+'-backend';edge=identifier+'-edge'
        with tempfile.TemporaryDirectory(prefix='easyoa-proxy-') as directory:
            root=Path(directory);certs=root/'certs';certs.mkdir()
            subprocess.run(['openssl','req','-x509','-nodes','-newkey','rsa:2048','-days','1','-subj','/CN=localhost','-keyout',str(certs/'easyoa.key'),'-out',str(certs/'easyoa.crt')],check=True,capture_output=True)
            (root/'upstream.py').write_text('''import json,threading
from http.server import BaseHTTPRequestHandler,HTTPServer
class Handler(BaseHTTPRequestHandler):
 def do_GET(self):
  body=json.dumps({k:self.headers.get(k) for k in ['Host','X-Forwarded-For','X-Real-IP','X-Forwarded-Proto','X-Forwarded-Host','X-Request-Id']}).encode()
  self.send_response(200);self.send_header('Content-Type','application/json');self.send_header('Content-Length',str(len(body)));self.end_headers();self.wfile.write(body)
threading.Thread(target=HTTPServer(('0.0.0.0',80),Handler).serve_forever,daemon=True).start()
HTTPServer(('0.0.0.0',8080),Handler).serve_forever()
''')
            docker('network','create',identifier)
            try:
                docker('run','-d','--name',backend,'--network',identifier,'--network-alias','easyoa-api','--network-alias','easyoa-web','-v',str(root/'upstream.py')+':/upstream.py:ro','python:3.12-alpine','python','/upstream.py')
                docker('run','-d','--name',edge,'--network',identifier,'-p','127.0.0.1::443','-v',str(SOURCE/'infra/nginx/nginx.conf')+':/etc/nginx/nginx.conf:ro','-v',str(SOURCE/'infra/nginx/conf.d')+':/etc/nginx/conf.d:ro','-v',str(certs)+':/etc/nginx/certs:ro','nginx:1.27-alpine')
                address=docker('port',edge,'443/tcp');url='https://'+address+'/api/system/about'
                opener=urllib.request.build_opener(urllib.request.ProxyHandler({}),urllib.request.HTTPSHandler(context=ssl._create_unverified_context()))
                request=urllib.request.Request(url,headers={'X-Forwarded-For':'203.0.113.23','X-Real-IP':'203.0.113.23','X-Forwarded-Proto':'http','X-Forwarded-Host':'attacker.example','X-Request-Id':'forged-request-id'})
                for _ in range(30):
                    try:
                        with opener.open(request,timeout=2) as response:info=json.load(response)
                        break
                    except urllib.error.HTTPError as error:
                        error.close();time.sleep(1)
                    except OSError:time.sleep(1)
                else:self.fail('Nginx proxy did not become ready')
                self.assertEqual(info['Host'],'127.0.0.1')
                self.assertEqual(info['X-Forwarded-Host'],'127.0.0.1')
                self.assertEqual(info['X-Forwarded-Proto'],'https')
                self.assertNotEqual(info['X-Forwarded-For'],'203.0.113.23')
                self.assertEqual(info['X-Forwarded-For'],info['X-Real-IP'])
                self.assertRegex(info['X-Request-Id'],r'^[0-9a-f]{32}$')
                self.assertNotEqual(info['X-Request-Id'],'forged-request-id')
                docker('exec',edge,'nginx','-t')
            finally:
                for name in [edge,backend]:subprocess.run(['docker','rm','-f',name],capture_output=True)
                docker('network','rm',identifier)

if __name__=='__main__':unittest.main()
