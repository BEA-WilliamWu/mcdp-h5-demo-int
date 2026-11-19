"""Run real JMeter against the bundled fixture; no bank endpoint is contacted."""
import argparse, csv, os, subprocess, tempfile, threading, concurrent.futures
from pathlib import Path
import xml.etree.ElementTree as ET
from http.server import ThreadingHTTPServer
from mock_server import Handler
p=argparse.ArgumentParser();p.add_argument('--jmeter',required=True);args=p.parse_args()
root=Path(__file__).resolve().parent
server=ThreadingHTTPServer(('127.0.0.1',0),Handler)
threading.Thread(target=server.serve_forever,daemon=True).start()
fixtures=[(s,'/ok',False,{}) for s in ['S1','S2','S3','S4','S5','S6']]
fixtures += [('S1',path,True,{}) for path in ['/early','/repeat','/empty','/business','/http-error']]
fixtures += [('S1','/ok',True,{'max_pages':'1'}),('S1','/ok',True,{'max_journey_ms':'1'}),('S1','/ok',False,{'journeys':'2','warmup_journeys':'1'})]
out=Path(tempfile.mkdtemp(prefix='bcoh2h-2436-fixture-'))
key=out/'fixture-key.pem';pub=out/'fixture-public.pem'
subprocess.run(['openssl','genpkey','-algorithm','RSA','-pkeyopt','rsa_keygen_bits:2048','-out',str(key)],check=True,capture_output=True)
subprocess.run(['openssl','pkey','-in',str(key),'-pubout','-out',str(pub)],check=True,capture_output=True)
Handler.public_key=pub
fixtures.append(('S1','/signed',False,{'auth_mode':'signed','private_key_file':str(key),'client_id':'fixture-client'}))
fixtures += [(s,'/signed',False,{'udv_only':True}) for s in ['S1','S5','S6']]
fixtures += [
    (scenario, '/signed', False, {
        'udv_only': True, 'ramp_seconds': '3',
        'warmup_journeys': '1', 'measured_journeys': '2',
    })
    for scenario in ['S5', 'S6']
]
def run(case):
 i,(scenario,path,negative,extra)=case
 name=f'{i:02}-{scenario}-{path[1:]}'
 jtl=out/(name+'.jtl');log=out/(name+'.log')
 report_jtl=out/(name+'-measured.jtl')
 configured=out/(name+'.jmx')
 plan=ET.parse(root/'BCOH2H-2436.jmx')
 report=plan.find(".//ResultCollector[@testname='Measured pages - Aggregate Report']")
 report.find("stringProp[@name='filename']").text=str(report_jtl)
 props={'base_url':f'http://127.0.0.1:{server.server_port}','endpoint':path,'auth_mode':'token','account':'015000000000001','scenario':scenario,'to_date':'2026-11-18','expected_count':'101' if scenario=='S1' else '201','pause_ms':'0','ramp_seconds':'1','jmeterengine.nongui.port':'0',**extra}
 cmd=[args.jmeter,'-n','-t',str(configured),'-q',str(root/'environment.example.properties'),'-l',str(jtl),'-j',str(log)]+[f'-J{k}={v}' for k,v in props.items()]
 env={**os.environ,'HTH_TOKEN':'fixture-only'}
 if props.pop('udv_only',False):
  values={**props,'account_type':'CUR','currency':'HKD','signing_path':'/signed'}
  for arg in plan.findall(".//Arguments[@testname='User Defined Variables']/collectionProp/elementProp"):
   arg.find("stringProp[@name='Argument.value']").text=values.get(arg.get('name'),'')
  env.update(HTH_CLIENT_ID='fixture-client',HTH_PRIVATE_KEY_FILE=str(key))
  cmd=[args.jmeter,'-n','-t',str(configured),'-l',str(jtl),'-j',str(log),
       '-Jjmeterengine.nongui.port=0','-Jsample_variables=scenario,phase,journey_id,page_no,total,complete,journey_ms']
 plan.write(configured,encoding='UTF-8',xml_declaration=True)
 result=subprocess.run(cmd,env=env,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True,timeout=90)
 assert result.returncode==0,(name,result.stdout)
 rows=list(csv.DictReader(jtl.open()))
 summaries=[r for r in rows if r['label'].endswith(' journey')]
 users=5 if scenario=='S5' else 10 if scenario=='S6' else 1
 warmups=int(extra.get('warmup_journeys','0'))
 iterations=int(extra.get('journeys', warmups+int(extra.get('measured_journeys','1'))))
 expected_phases=['warmup']*warmups+['measure']*(iterations-warmups)
 assert len({r['threadName'] for r in summaries})==users,(name,'Wrong thread count')
 for thread in {r['threadName'] for r in summaries}:
  labels=[r['label'] for r in summaries if r['threadName']==thread]
  assert labels==[f'{scenario} {phase} journey' for phase in expected_phases],(name,thread,labels)
 assert all((r['success']=='false')==negative for r in summaries),(name,rows)
 pages=[r for r in rows if r['label'].endswith(' page')]
 with report_jtl.open(newline='') as report_file:
  report_rows=list(csv.DictReader(report_file))
 def sample_keys(samples):
  return sorted(tuple(r[k] for k in ('label','threadName','timeStamp','elapsed','success')) for r in samples)
 measured=[r for r in pages if r['label']==f'{scenario} measure page']
 assert measured and sample_keys(report_rows)==sample_keys(measured),(name,'Unexpected samples in Aggregate Report')
 if not negative:
  assert all(r['success']=='true' for r in pages),(name,rows)
  assert len(pages)==users*iterations*(3 if scenario=='S1' else 5),(name,len(pages))
  assert all(int(r['total'])==int(props['expected_count']) and r['complete']=='true' for r in summaries)
 engine_log=log.read_text()
 assert 'ERROR ' not in engine_log,(name,'Engine/script error; inspect '+str(log))
 assert f"threads={users} ramp-up={props['ramp_seconds']} " in engine_log,(name,'Incorrect ramp-up')
 return name,len(pages),len(summaries),'EXPECTED FAILURE' if negative else 'PASS'
try:
 with concurrent.futures.ThreadPoolExecutor(max_workers=3) as pool:
  for row in pool.map(run,enumerate(fixtures)): print(*row,flush=True)
 print('Evidence:',out)
finally: server.shutdown()
