"""Validate Start-time logon and the workload using synthetic localhost data."""
import argparse
import base64
import csv
import hashlib
import json
import os
from pathlib import Path
import subprocess
import tempfile
import threading
import time
import xml.etree.ElementTree as ET
from http.server import ThreadingHTTPServer

from mock_server import Handler

ROOT = Path(__file__).resolve().parent
PASSWORD = 'fixture-password'
SALT = 'fixture-salt'


def decode_part(value):
    return base64.urlsafe_b64decode(value + '=' * (-len(value) % 4))


def der_ecdsa(signature):
    # JWS ES256 encodes two fixed-width integers. OpenSSL expects DER.
    assert len(signature) == 64
    def integer(raw):
        value = raw.lstrip(b'\x00') or b'\x00'
        if value[0] & 128:
            value = b'\x00' + value
        return b'\x02' + bytes([len(value)]) + value
    body = integer(signature[:32]) + integer(signature[32:])
    return b'\x30' + bytes([len(body)]) + body


def verify(key, message, signature):
    with tempfile.TemporaryDirectory() as tmp:
        data, sig = Path(tmp)/'message', Path(tmp)/'sig'
        data.write_bytes(message)
        sig.write_bytes(signature)
        subprocess.run(['openssl', 'dgst', '-sha256', '-verify', str(key),
                        '-signature', str(sig), str(data)], check=True, capture_output=True)


class LoginHandler(Handler):
    def do_POST(self):
        if self.path != '/auth/v1/token':
            self.server.pages += 1
            super().do_POST()
            return

        self.server.logins += 1
        raw = self.rfile.read(int(self.headers['Content-Length']))
        try:
            data = json.loads(raw)
            assert self.headers.get('Client-Id') == 'fixture-client'
            assert self.headers.get('Channel') == 'HTH'
            assert self.headers.get('X-IBM-Client-Id') == 'fixture-apic'
            assert self.headers.get('X-IBM-Client-Secret') == 'fixture-apic-only'
            stamp = self.headers['Request-Time']
            canonical = f'POST\n/auth/v1/token\n\n{stamp}\n'.encode() + raw
            verify(self.public_key, canonical, base64.b64decode(self.headers['Request-Sign']))
            encoded_header, encoded_claims, sig = data['client_assertion'].split('.')
            header = json.loads(decode_part(encoded_header))
            claims = json.loads(decode_part(encoded_claims))
            assert header == {'alg': self.server.algorithm, 'kid': 'fixture-kid'}
            assert claims['iss'] == claims['sub'] == 'fixture-client'
            assert claims['aud'] == 'https://fixture.example/token' and claims['closeid'] == 'fixture-user'
            assert claims['jti'] and claims['iat'] <= time.time() < claims['exp']
            return_hash = hashlib.sha256((PASSWORD + SALT).encode()).hexdigest().encode()
            verify(self.public_key, return_hash, base64.b64decode(data['password_signature']))
            signature = decode_part(sig)
            public_key = self.public_key
            if header['alg'] == 'ES256':
                signature, public_key = der_ecdsa(signature), self.server.ec_public
            verify(public_key, f'{encoded_header}.{encoded_claims}'.encode(), signature)
            status = 401 if self.server.reject else 200
        except Exception as error:
            self.server.validation_error = type(error).__name__
            status = 400
        reply = {'access_token': 'fixture-only', 'token_type': 'Bearer', 'expires_in': 300}
        body = json.dumps(reply if status == 200 else {'error': 'invalid_client'}).encode()
        self.send_response(status)
        self.send_header('Content-Type', 'application/json')
        self.send_header('Content-Length', str(len(body)))
        self.end_headers()
        self.wfile.write(body)


def run(jmeter, root, scenario, algorithm, rejected):
    rsa, ec, headers = root/'rsa.pem', root/'ec.pem', root/'headers.properties'
    server = ThreadingHTTPServer(('127.0.0.1', 0), LoginHandler)
    server.algorithm, server.reject = algorithm, rejected
    server.ec_public = root/'ec-public.pem'
    server.logins = server.pages = 0
    server.validation_error = None
    threading.Thread(target=server.serve_forever, daemon=True).start()
    try:
        label = f'{scenario}-{algorithm}' + ('-rejected' if rejected else '')
        jmx, jtl, log = (root / (label + suffix) for suffix in ['.jmx', '.jtl', '.log'])
        plan = ET.parse(ROOT/'BCOH2H-2436.jmx')
        config = {
            'base_url': f'http://127.0.0.1:{server.server_port}', 'endpoint': '/signed',
            'scenario': scenario, 'account': '015000000000001', 'to_date': '2026-11-18',
            'expected_count': '101' if scenario == 'S1' else '201',
            'client_id': 'fixture-client', 'private_key_file': str(rsa), 'headers_file': str(headers),
            'login_close_id': 'fixture-user', 'login_audience': 'https://fixture.example/token',
            'login_kid': 'fixture-kid', 'login_salt': SALT, 'login_assertion_alg': algorithm,
            'login_assertion_key_file': str(ec) if algorithm == 'ES256' else '',
        }
        for entry in plan.findall(".//Arguments[@testname='User Defined Variables']/collectionProp/elementProp"):
            if entry.get('name') in config:
                entry.find("stringProp[@name='Argument.value']").text = config[entry.get('name')]
        plan.write(jmx, encoding='UTF-8', xml_declaration=True)
        env = {**os.environ, 'HTH_LOGIN_PASSWORD': PASSWORD}
        env.pop('HTH_TOKEN', None)
        result = subprocess.run([jmeter, '-n', '-t', str(jmx), '-l', str(jtl), '-j', str(log),
                                 '-Jjmeterengine.nongui.port=0', '-Jpause_ms=0'],
                                timeout=90, env=env, capture_output=True, text=True)
        assert result.returncode == 0, result.stdout
        with jtl.open(newline='') as stream:
            rows = list(csv.DictReader(stream))
        assert server.logins == 1 and not server.validation_error, (label, server.logins, server.validation_error, log.read_text())
        if rejected:
            assert server.pages == 0 and len(rows) == 1 and rows[0]['responseCode'] == 'LOGIN_FAILED', rows
        else:
            threads = {'S1': 1, 'S5': 5, 'S6': 10}[scenario]
            pages = threads * (3 if scenario == 'S1' else 5)
            assert server.pages == pages
            assert sum(r['label'] == f'{scenario} measure page' for r in rows) == pages
            assert sum(r['label'] == f'{scenario} measure journey' for r in rows) == threads
            assert len(rows) == pages + threads and all(r['success'] == 'true' for r in rows)
        for file in [log, jtl]:
            for secret in [PASSWORD, 'fixture-only', 'fixture-apic-only']:
                assert secret not in file.read_text()
        print(f'{label}: PASS ({server.logins} logon, {server.pages} workload requests)', flush=True)
    finally:
        server.shutdown()


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--jmeter', required=True)
    args = parser.parse_args()
    with tempfile.TemporaryDirectory(prefix='bcoh2h2436-login-') as temporary:
        root = Path(temporary)
        for name, options in [('rsa', ['-algorithm', 'RSA', '-pkeyopt', 'rsa_keygen_bits:2048']),
                              ('ec', ['-algorithm', 'EC', '-pkeyopt', 'ec_paramgen_curve:P-256', '-pkeyopt', 'ec_param_enc:named_curve'])]:
            key = root/(name + '.pem')
            subprocess.run(['openssl', 'genpkey', *options, '-out', str(key)], check=True, capture_output=True)
            subprocess.run(['openssl', 'pkey', '-in', str(key), '-pubout', '-out', str(root/(name + '-public.pem'))],
                           check=True, capture_output=True)
        LoginHandler.public_key = root/'rsa-public.pem'
        (root/'headers.properties').write_text('X-IBM-Client-Id=fixture-apic\nX-IBM-Client-Secret=fixture-apic-only\n')
        for scenario, algorithm, rejected in [('S1', 'ES256', False), ('S5', 'ES256', False),
                                               ('S6', 'ES256', False), ('S1', 'RS256', False),
                                               ('S5', 'ES256', True)]:
            run(args.jmeter, root, scenario, algorithm, rejected)
