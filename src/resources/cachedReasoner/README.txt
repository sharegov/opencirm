Cached Reasoner Population (aka warmup) usage:

1. To populate reasoner:
A: cirm.top.get('/ontadmin/cachedReasonerQ1populate/'))
or 
B: Set startup conf parameter: 
	.set("cachedReasonerPopulate", true)

2. Create file populateGetInstancesCache.json with content from:
JSON.stringify(cirm.top.get('/ontadmin/cachedReasonerQ1/'));


Thomas Hilpold, 2013.07.01