import java.time.LocalDate

try {
if (!vars.get('inputs_captured')) {
    ['scenario', 'base_url', 'endpoint', 'account', 'account_type', 'currency',
     'to_date', 'from_date', 'expected_count', 'signing_path',
     'ramp_seconds', 'warmup_journeys', 'measured_journeys'].each { name ->
        vars.put('input_' + name, vars.get(name) ?: '')
    }
    vars.put('inputs_captured', 'true')
}
def get = { String k, String d = '' -> props.getProperty(k)?.trim() ?: vars.get('input_' + k)?.trim() ?: d }
def need = { String k -> def v=get(k); if (!v) throw new IllegalArgumentException('Missing configuration: '+k); v }
def s = get('scenario','S1')
assert s in ['S1','S2','S3','S4','S5','S6']: 'scenario must be S1..S6'
def ramp = get('ramp_seconds', '1')
assert ramp.isInteger() && ramp.toInteger() >= 0: 'ramp_seconds must be a nonnegative integer'
def warmup = get('warmup_journeys', '0')
assert warmup.isInteger() && warmup.toInteger() >= 0: 'warmup_journeys must be a nonnegative integer'
int warmupCount = warmup.toInteger()
def legacyTotal = props.getProperty('journeys')?.trim()
if (legacyTotal) {
    assert legacyTotal.isInteger() && legacyTotal.toInteger() > 0 && legacyTotal.toInteger() >= warmupCount:
        'journeys must be a positive integer and at least warmup_journeys'
} else {
    def measured = get('measured_journeys', '1')
    assert measured.isInteger() && measured.toInteger() >= 1: 'measured_journeys must be a positive integer'
    assert (long) warmupCount + measured.toLong() <= Integer.MAX_VALUE: 'Total journeys exceed the supported limit'
}
def base = new URI(need('base_url'))
assert base.scheme in ['http','https'] && base.host && !base.query && !base.fragment && !base.userInfo: 'base_url must be an HTTP(S) origin'
assert !base.path || base.path == '/': 'Use endpoint for context path'
vars.put('host',base.host); vars.put('protocol',base.scheme)
vars.put('port',String.valueOf(base.port == -1 ? (base.scheme=='https'?443:80):base.port))
vars.put('endpoint',get('endpoint','/account/v1/transaction-inquiry'))
assert vars.get('endpoint').startsWith('/') && !vars.get('endpoint').contains('?'): 'endpoint must be a path without query'
def account=get('account',System.getenv('HTH_ACCOUNT') ?: '')
assert account ==~ /\d{14,15}/: 'account must be 14 or 15 digits'
vars.put('account',account)
['account_type', 'currency', 'signing_path'].each { name ->
    vars.put(name, get(name))
}
def end=LocalDate.parse(need('to_date'))
def start=LocalDate.parse(get('from_date',end.minusDays(s=='S1'?0:s=='S4'?4:1).toString()))
assert !start.isAfter(end) && !end.isAfter(start.plusDays(60)): 'Invalid date range'
if(s=='S1') assert start==end: 'S1 requires one day'
if(s in ['S2','S5','S6']) assert end==start.plusDays(1): 'Use two consecutive dates for S2/S5/S6; document business-day variants separately'
if(s=='S4') assert end==start.plusDays(4): 'S4 requires five calendar dates'
vars.put('from_date',start.toString()); vars.put('to_date',end.toString())
int size=get('page_size','50') as int
assert size>=1 && size<=50: 'page_size must be 1..50'
if(s=='S3') assert size==50: 'S3 verifies maximum page size 50'
int expected=need('expected_count') as int
assert expected>=0: 'expected_count must be independently verified'
if(s=='S1') assert expected>100: 'S1 requires >100 transactions'
if(s in ['S2','S5','S6']) assert expected>200: 'Two-day workload requires >200 transactions'
assert (get('max_pages','1000') as int)>0 && (get('max_journey_ms','300000') as long)>0
vars.put('page_size',size.toString()); vars.put('expected_count',expected.toString())
int iteration=(vars.get('iteration') ?: '0') as int
vars.put('iteration',(iteration+1).toString())
vars.put('phase',iteration < warmupCount ? 'warmup' : 'measure')
vars.put('scenario',s); vars.put('journey_id',UUID.randomUUID().toString())
vars.put('cursor',''); vars.put('more','true'); vars.put('page_no','0'); vars.put('total','0')
vars.put('journey_ms',''); vars.put('journey_error',''); vars.put('complete','false'); vars.put('page_count','0')
vars.putObject('seenCursors',new HashSet()); vars.putObject('seenRows',new HashSet())
vars.putObject('lastDate',null); vars.putObject('started',System.nanoTime())
assert System.getenv('HTH_TOKEN')?.trim(): 'Set HTH_TOKEN before starting JMeter'
SampleResult.setIgnore()
} catch(Throwable e) {
 SampleResult.setStopThread(true)
 throw e
}
