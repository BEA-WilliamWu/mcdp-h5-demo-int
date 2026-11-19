from pathlib import Path
import xml.etree.ElementTree as X
rootdir=Path(__file__).resolve().parent
r=X.Element('jmeterTestPlan',version='1.2',properties='5.0',jmeter='5.6.3'); tree=X.SubElement(r,'hashTree')
def el(parent,tag,name,gui,**attrs):
 e=X.SubElement(parent,tag,guiclass=gui,testclass=tag,testname=name,enabled='true',**attrs); h=X.SubElement(parent,'hashTree'); return e,h
def s(e,n,v):X.SubElement(e,'stringProp',name=n).text=str(v)
def b(e,n,v):X.SubElement(e,'boolProp',name=n).text=str(v).lower()
def groovy_function(script):
 return '${__groovy(' + ' '.join(line.strip() for line in script.strip().splitlines()).replace(',', r'\,') + ')}'
def js(parent,tag,name,file):
 e,h=el(parent,tag,name,'TestBeanGUI'); s(e,'scriptLanguage','groovy');s(e,'cacheKey','true');s(e,'filename','');s(e,'parameters','');s(e,'script',(rootdir/'scripts'/file).read_text());return e,h
plan,ph=el(tree,'TestPlan','BCOH2H-2436 S1-S6','TestPlanGui');b(plan,'TestPlan.serialize_threadgroups',True)
udv,_=el(ph,'Arguments','User Defined Variables','ArgumentsPanel')
values=X.SubElement(udv,'collectionProp',name='Arguments.arguments')
for name,value in {
    'scenario':'S1', 'base_url':'', 'endpoint':'/account/v1/transaction-inquiry',
    'account':'', 'account_type':'', 'currency':'',
    'to_date':'', 'from_date':'', 'expected_count':'',
    'ramp_seconds':'1', 'warmup_journeys':'0', 'measured_journeys':'1',
    'signing_path':'',
}.items():
    arg=X.SubElement(values,'elementProp',name=name,elementType='Argument')
    s(arg,'Argument.name',name)
    s(arg,'Argument.value',value)
    s(arg,'Argument.metadata','=')
tg,th=el(ph,'ThreadGroup','Selected scenario','ThreadGroupGui')
s(tg,'ThreadGroup.num_threads',
  "${__groovy(['S5':5\\,'S6':10][props.getProperty('scenario')?.trim() ?: vars.get('scenario')?.trim()] ?: 1)}")
s(tg,'ThreadGroup.ramp_time',groovy_function("""
    def ramp = props.getProperty('ramp_seconds')?.trim() ?: vars.get('ramp_seconds')?.trim() ?: '1';
    ramp.isInteger() && ramp.toInteger() >= 0 ? ramp.toInteger() : 1
"""))
s(tg,'ThreadGroup.on_sample_error','continue');b(tg,'ThreadGroup.scheduler',False)
l=X.SubElement(tg,'elementProp',name='ThreadGroup.main_controller',elementType='LoopController',guiclass='LoopControlPanel',testclass='LoopController',testname='Journeys',enabled='true');b(l,'LoopController.continue_forever',False)
# Fall back to one iteration on invalid input so Initialize journey can report the error.
s(l,'LoopController.loops',groovy_function("""
    def legacyTotal = props.getProperty('journeys')?.trim();
    if (legacyTotal) return legacyTotal.isInteger() && legacyTotal.toInteger() > 0 ? legacyTotal.toInteger() : 1;
    def warmup = props.getProperty('warmup_journeys')?.trim() ?: vars.get('warmup_journeys')?.trim() ?: '0';
    def measured = props.getProperty('measured_journeys')?.trim() ?: vars.get('measured_journeys')?.trim() ?: '1';
    if (!warmup.isInteger() || !measured.isInteger()) return 1;
    long total = warmup.toLong() + measured.toLong();
    total > 0 && total <= Integer.MAX_VALUE ? total : 1
"""))
js(th,'JSR223Sampler','Initialize journey','init.groovy')
w,wh=el(th,'WhileController','Follow server cursor','WhileControllerGui');s(w,'WhileController.condition','${more}')
def add_page_request(parent):
    http,hh=el(parent,'HTTPSamplerProxy','Page request','HttpTestSampleGui')
    args=X.SubElement(http,'elementProp',name='HTTPsampler.Arguments',elementType='Arguments');c=X.SubElement(args,'collectionProp',name='Arguments.arguments');a=X.SubElement(c,'elementProp',name='',elementType='HTTPArgument');b(a,'HTTPArgument.always_encode',False);s(a,'Argument.value','${body}');s(a,'Argument.metadata','=')
    for k,v in {'domain':'${host}','port':'${port}','protocol':'${protocol}','path':'${endpoint}','method':'POST','contentEncoding':'UTF-8','connect_timeout':'${__P(connect_timeout_ms,10000)}','response_timeout':'${__P(response_timeout_ms,60000)}','implementation':'HttpClient4'}.items():s(http,'HTTPSampler.'+k,v)
    b(http,'HTTPSampler.postBodyRaw',True);b(http,'HTTPSampler.follow_redirects',False);b(http,'HTTPSampler.auto_redirects',False);b(http,'HTTPSampler.use_keepalive',True)
    hm,_=el(hh,'HeaderManager','Per-page headers','HeaderPanel');X.SubElement(hm,'collectionProp',name='HeaderManager.headers')
    js(hh,'JSR223PreProcessor','Prepare body and fresh signature','prepare.groovy')
    js(hh,'JSR223Assertion','Validate page and advance cursor','check.groovy')
    return hh

for phase, title in [('warmup', 'Warm-up pages'), ('measure', 'Measured pages')]:
    branch, branch_tree = el(wh, 'IfController', title, 'IfControllerPanel')
    s(branch, 'IfController.condition', groovy_function(f"vars.get('phase') == '{phase}'"))
    b(branch, 'IfController.useExpression', True)
    b(branch, 'IfController.evaluateAll', False)
    page_tree = add_page_request(branch_tree)
    if phase == 'measure':
        report, _ = el(page_tree, 'ResultCollector', 'Measured pages - Aggregate Report', 'StatVisualizer')
        b(report, 'ResultCollector.error_logging', False)
        b(report, 'saveHeaders', True)
        s(report, 'filename', '')

js(th,'JSR223Sampler','Journey summary','finish.groovy')
pause,pa=el(th,'TestAction','Pause between journeys','TestActionGui');s(pause,'ActionProcessor.action','1');s(pause,'ActionProcessor.target','0');s(pause,'ActionProcessor.duration','${__P(pause_ms,1000)}')
X.indent(r);X.ElementTree(r).write(rootdir/'BCOH2H-2436.jmx',encoding='UTF-8',xml_declaration=True)
