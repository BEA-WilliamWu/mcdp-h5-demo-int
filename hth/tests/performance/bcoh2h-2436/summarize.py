"""Separate HTTP page samples from overlapping journey summaries; stdlib only."""
import csv, sys, math, collections
rows=list(csv.DictReader(open(sys.argv[1],newline='',encoding='utf-8-sig')))
groups=collections.defaultdict(list)
for r in rows: groups[r['label']].append(r)
for label, values in sorted(groups.items()):
    times=sorted(int(v.get('journey_ms') or 0) if label.endswith(' journey') else int(v['elapsed']) for v in values)
    failures=sum(v['success'].lower()!='true' for v in values)
    pct=lambda p: times[max(0,math.ceil(len(times)*p)-1)]
    starts=[int(v['timeStamp'])-int(v.get('journey_ms') or 0) if label.endswith(' journey') else int(v['timeStamp']) for v in values]
    ends=[int(v['timeStamp']) if label.endswith(' journey') else int(v['timeStamp'])+int(v['elapsed']) for v in values]
    span=max(ends)-min(starts)
    rate=len(values)/(span/1000) if span>0 else 0
    print(f'{label}: n={len(times)} failures={failures} p50={pct(.5)}ms p95={pct(.95)}ms max={max(times)}ms attempted_rate={rate:.2f}/s')
page_ids={r.get('journey_id') for r in rows if r['label'].endswith(' page')}
summary_ids={r.get('journey_id') for r in rows if r['label'].endswith(' journey')}
if None in page_ids or '' in page_ids:
    print('Cannot reconcile journeys: use sample_variables from example properties.')
else:
    print('Journeys without summary:',len(page_ids-summary_ids))
for r in rows:
    if r['success'].lower()!='true':
        print('FAIL:',r['label'],r.get('failureMessage','') or r.get('responseMessage',''))
