long started=vars.getObject('started') as long
long duration=(System.nanoTime()-started)/1000000L
SampleResult.setSampleLabel(vars.get('scenario')+' '+vars.get('phase')+' journey')
boolean ok=vars.get('complete')=='true' && !vars.get('journey_error')
SampleResult.setSuccessful(ok)
SampleResult.setResponseCode(ok?'200':'500')
SampleResult.setResponseMessage(ok?'Complete traversal':vars.get('journey_error'))
SampleResult.setResponseData('pages='+vars.get('page_no')+',records='+vars.get('total')+',duration_ms='+duration,'UTF-8')
vars.put('journey_ms',duration.toString())
// Keep traversal duration separate from the summary sampler execution time.
// JTL elapsed is summary-script time; use journey_ms for the traversal duration.
if(!ok && props.getProperty('stop_on_failure','true')=='true') SampleResult.setStopThread(true)
