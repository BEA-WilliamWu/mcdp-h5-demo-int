import groovy.json.JsonOutput
import org.apache.jmeter.protocol.http.control.Header
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec

try {
def get={String k, String d='' -> props.getProperty(k,d)}
int page=(vars.get('page_no') as int)+1
vars.put('page_no',page.toString()); vars.put('cursor_in',vars.get('cursor'))
def body=[accountNumber:vars.get('account'),fromDate:vars.get('from_date'),toDate:vars.get('to_date'),pageSize:vars.get('page_size') as int]
['account_type':'accountType','currency':'currency'].each{k,v -> if(vars.get(k)) body[v]=vars.get(k)}
if(vars.get('cursor')) body.cursor=vars.get('cursor')
vars.put('body',JsonOutput.toJson(body))
vars.put('message_id',UUID.randomUUID().toString())
def hm=new org.apache.jmeter.protocol.http.control.HeaderManager()
def add={k,v -> if(v) hm.add(new Header(k,v.toString()))}
add('Content-Type','application/json'); add('Accept','application/json')
add('Channel',get('channel','HTH')); add('Message-Id',vars.get('message_id'))
def token=System.getenv('HTH_TOKEN')?.trim()
assert token: 'Set HTH_TOKEN before starting JMeter'
def prefix=get('auth_prefix','Bearer').trim()
add('Authorization',prefix ? prefix+' '+token : token)
def extra=get('headers_file')
if(extra){ def h=new Properties(); new File(extra).withInputStream{h.load(it)}; h.each{k,v -> add(k,v)} }
def mode=get('auth_mode','signed')
assert mode in ['signed','token']: 'auth_mode must be signed or token'
if(mode=='signed'){
 def client=get('client_id',System.getenv('HTH_CLIENT_ID') ?: '')
 assert client: 'client_id is required for BFF signing'
 def key=vars.getObject('signingKey')
 if(!key){
  def file=get('private_key_file',System.getenv('HTH_PRIVATE_KEY_FILE') ?: '')
  assert file: 'private_key_file is required for signed mode'
  def pem=new File(file).getText('UTF-8')
  assert pem.contains('BEGIN PRIVATE KEY'): 'Use PKCS8 PEM RSA private key'
  def der=Base64.decoder.decode(pem.replaceAll(/-----[^-]+-----/,'').replaceAll(/\s/,''))
  key=KeyFactory.getInstance('RSA').generatePrivate(new PKCS8EncodedKeySpec(der)); vars.putObject('signingKey',key)
 }
 def time=String.valueOf(System.currentTimeMillis()-(get('clock_skew_ms','1000') as long))
 def path=vars.get('signing_path') ?: vars.get('endpoint')
 def content='POST\n'+path+'\n\n'+time+'\n'+vars.get('body')
 def signer=Signature.getInstance('SHA256withRSA'); signer.initSign(key); signer.update(content.getBytes('UTF-8'))
 add('Client-Id',client); add('Request-Time',time); add('Request-Sign',Base64.encoder.encodeToString(signer.sign()))
}
sampler.setProperty(new org.apache.jmeter.testelement.property.TestElementProperty(org.apache.jmeter.protocol.http.sampler.HTTPSamplerBase.HEADER_MANAGER,hm))
// Optional dynamic gateway/DPoP hook: receives sampler, vars, props; never log credentials.
if(get('auth_hook')) new GroovyShell(this.binding).evaluate(new File(get('auth_hook')))
sampler.setName(vars.get('scenario')+' '+vars.get('phase')+' page')

} catch(Throwable e) {
 vars.put("journey_error","Request preparation failed: "+e.class.simpleName)
 throw new org.apache.jorphan.util.JMeterStopThreadException("Request preparation failed; check local credentials/configuration", e)
}
