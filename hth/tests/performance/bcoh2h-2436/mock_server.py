"""Local-only functional fixture. Synthetic records; never a performance baseline."""
import json, base64, subprocess, tempfile, time
from pathlib import Path
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

class Handler(BaseHTTPRequestHandler):
    public_key = None
    def log_message(self, *args): pass
    def do_POST(self):
        raw=self.rfile.read(int(self.headers['Content-Length']))
        req=json.loads(raw)
        assert self.headers.get('Authorization')=='Bearer fixture-only'
        assert self.headers.get('Channel')=='HTH' and self.headers.get('Message-Id')
        if self.path=='/signed':
            assert self.public_key
            stamp=self.headers.get('Request-Time')
            assert 0<=int(time.time()*1000)-int(stamp)<300000
            content=b'POST\n/signed\n\n'+stamp.encode()+b'\n'+raw
            with tempfile.TemporaryDirectory() as folder:
                data=Path(folder)/'data';sig=Path(folder)/'sig'
                data.write_bytes(content);sig.write_bytes(base64.b64decode(self.headers['Request-Sign']))
                result=subprocess.run(['openssl','dgst','-sha256','-verify',str(self.public_key),'-signature',str(sig),str(data)],capture_output=True)
                assert result.returncode==0, 'Invalid request signature'

        count=101 if req['fromDate']==req['toDate'] else 201
        offset=int(req.get('cursor','0')); size=req['pageSize']
        rows=[{'date':req['toDate'],'time':'12:00:00','currency':'HKD',
               'numberOfTrans':str(i),'description':'synthetic '+str(i)}
              for i in range(offset,min(offset+size,count))]
        end=offset+len(rows); more=end<count
        if self.path=='/early': more=False
        cursor=f'{end:09d}' if more else None
        if self.path=='/repeat' and offset: cursor=req['cursor']; more=True
        if self.path=='/empty': rows=[]; more=True; cursor='000000001'
        result={'responseCode':'999' if self.path=='/business' else '000',
                'accountNumber':req['accountNumber'],'pagination':{
                'hasMoreData':more,'nextCursor':cursor,'pageSize':len(rows),'transactions':rows}}
        data=json.dumps(result).encode()
        self.send_response(500 if self.path=='/http-error' else 200)
        self.send_header('Content-Type','application/json');self.send_header('Content-Length',str(len(data)))
        self.end_headers();self.wfile.write(data)
if __name__=='__main__':
    import argparse
    p=argparse.ArgumentParser();p.add_argument('--port',type=int,default=18086)
    a=p.parse_args();ThreadingHTTPServer(('127.0.0.1',a.port),Handler).serve_forever()
