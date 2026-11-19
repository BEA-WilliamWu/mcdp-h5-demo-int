import groovy.json.JsonSlurper
import groovy.json.JsonOutput
import java.security.MessageDigest

def fail={String message ->
 AssertionResult.setFailure(true); AssertionResult.setFailureMessage(message)
 vars.put('complete','false'); vars.put('journey_error',message); vars.put('more','false')
}
prev.setSampleLabel(vars.get('scenario')+' '+vars.get('phase')+' page')
vars.put('page_count','0')
try {
 if(!prev.isSuccessful()) { fail('HTTP/transport failure: '+prev.getResponseCode()); return }
 def r=new JsonSlurper().parseText(prev.getResponseDataAsString())
 assert r.responseCode?.toString()==props.getProperty('success_code','000'): 'Non-success business response'
 assert r.accountNumber==vars.get('account'): 'Response account mismatch'
 def p=r.pagination
 assert p instanceof Map && p.hasMoreData instanceof Boolean && p.transactions instanceof List: 'Invalid pagination schema'
 def rows=p.transactions
 assert rows.size()<=(vars.get('page_size') as int): 'Page exceeds requested size'
 assert p.pageSize instanceof Number && p.pageSize.intValue()==rows.size(): 'pagination.pageSize differs from returned count'
 vars.put('page_count',rows.size().toString())
 rows.each { row ->
  assert row.date && row.date>=vars.get('from_date') && row.date<=vars.get('to_date'): 'Out-of-range or missing transaction date'
  def last=vars.getObject('lastDate'); assert last==null || row.date<=last: 'Dates not descending'
  vars.putObject('lastDate',row.date)
  if(vars.get('currency')) assert row.currency==vars.get('currency'): 'Currency mismatch'
  // Full-row fingerprints flag exact repeats, not globally unique banking identity.
  def stable=new TreeMap(row)
  def hash=MessageDigest.getInstance('SHA-256').digest(JsonOutput.toJson(stable).getBytes('UTF-8')).encodeHex().toString()
  if(props.getProperty('check_duplicates','true')=='true') assert vars.getObject('seenRows').add(hash): 'Exact duplicate transaction row; reconcile identity'
 }
 int total=(vars.get('total') as int)+rows.size(); vars.put('total',total.toString())
 assert total<=(vars.get('expected_count') as int): 'Returned count exceeds expected count'
 if(p.hasMoreData){
  assert rows: 'Empty page with hasMoreData=true'
  assert p.nextCursor instanceof String && p.nextCursor==~ /\d{9}/: 'Missing or invalid nextCursor (expected 9 digits)'
  assert p.nextCursor!=vars.get('cursor') && vars.getObject('seenCursors').add(p.nextCursor): 'Repeated cursor/no progress'
  assert (vars.get('page_no') as int)<(props.getProperty('max_pages','1000') as int): 'max_pages reached before completion'
  vars.put('cursor',p.nextCursor)
 } else {
  assert total==(vars.get('expected_count') as int): 'Premature final page: total does not equal expected_count'
  vars.put('complete','true'); vars.put('more','false')
 }
 long elapsed=(System.nanoTime()-(vars.getObject('started') as long))/1000000L
 assert elapsed<=(props.getProperty('max_journey_ms','300000') as long): 'Journey deadline exceeded'
} catch(Throwable e){ fail(e.getMessage()?.take(500) ?: 'Response validation failed') }
