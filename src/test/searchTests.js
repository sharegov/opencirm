/*******************************************************************************
 * Copyright 2014 Miami-Dade County
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/
// Initialize/bootstrap environment, calling require 
var initialized = false;
modulesInGlobalNamespace  = true;
require({
  baseUrl: "/javascript",
  paths: {
    "jquery": "jquery-1.7.1.min",
    "jquery-ui":"jquery-ui-1.8.17.custom.min",
    "jquery-jec":"jquery.jec-1.3.3",
    "jquery-upload":"jquery.upload-1.0.2",
    "jquery-tmpl":"jquery.tmpl.min",
    "jquery-autoresize": "jquery.autoresize",
    "jquery-marquee":"jquery.marquee.min",
    "jquery-cookie":"jquery.cookie",
    "jquery-knockout":"knockout-jquery-ui-widget",
    "jquery-datatables":"jquery.dataTables.1.9.0.min",
    "jquery-dt-ko-binding":"knockout.bindings.dataTables",
    "chai":"http://chaijs.com/chai",    
    "rest":"http://sharegov.org/jslib/rest",
    "U":"http://sharegov.org/jslib/U",
    "T":"../test/testfix"
  }, 
  shim : {
    'jquery':{ deps:[], exports:'$'},
    'jquery-ui':['jquery']
  }
  , urlArgs: "cache_bust=" + (new Date()).getTime()
}, [ "jquery", "U", "rest", "T",  "cirm", "legacy", "sreditor", "answerhub", 
     "jquery-ui", "jquery-tmpl", "jquery-autoresize", 
           "jquery-marquee", "jquery-cookie", "jquery-jec",
           "jquery-upload", "jquery-knockout", "jquery-datatables", "jquery-dt-ko-binding", "chai"],
function ($, U, rest, T, cirm) {
  // Define all needed test fixtures
  
    T.fixt("searchFeature").do(function () {
	this.articlePos=-1;
	this.cirmSearchResult;
	this.searchForArticlePosition=function(queryString,kbArticleNameCompareTo)
	{
	
		/*This function returns the position of an article in the search results. 
		 queryString= String to be searched in the knowledge base.
		 kbArticleNameCompareTo= Article name/number used in the test case.
		 */
		cirmSearchResult = cirm.top.postObject('/search/kb', {query:queryString});  
		for (var i=0;i<cirmSearchResult.docs.length;i++)
		{
			//Breaking String to get just the article name.
			var kbArticleIndex = cirmSearchResult['docs'][i]['url'].lastIndexOf("/") + 1;
			var kbArticleName = cirmSearchResult['docs'][i]['url'].substr(kbArticleIndex);
			if (kbArticleName==kbArticleNameCompareTo)
			{	
				articlePos=i;
				console.log('Match Found at position: '+articlePos);
				break;		
			}
			else
			{
				//console.log('Test Case ID failed: Match Not Found');
			}
		}
	return articlePos;
}

this.searchForArticlePositionWithAddress=function(queryString,kbArticleNameCompareTo,streetAddress,cityName,zip)
	{
	
	/*This function returns the position of an article in the search results based on query string and address. 
	 queryString= String to be searched in the knowledge base.
	 kbArticleNameCompareTo= Article name/number used in the test case.
	 address=
	 city=
	 */
	
 cirmSearchResult = cirm.top.postObject('/search/kb',
  												{
  													"query":queryString,
  													"meta":
  														{
  															"rows":"15",
  															"address":
  																	{
  																		"recycleList":
  																				{"1A":"http://gisims2.miamidade.gov/Cservices/RecyclingCalendars/calendar_A_1.pdf","2A":"http://gisims2.miamidade.gov/Cservices/RecyclingCalendars/calendar_A_2.pdf","3A":"http://gisims2.miamidade.gov/Cservices/RecyclingCalendars/calendar_A_3.pdf",
  																					"4A":"http://gisims2.miamidade.gov/Cservices/RecyclingCalendars/calendar_A_4.pdf","5A":"http://gisims2.miamidade.gov/Cservices/RecyclingCalendars/calendar_A_5.pdf","1B":"http://gisims2.miamidade.gov/Cservices/RecyclingCalendars/calendar_B_6.pdf","2B":"http://gisims2.miamidade.gov/Cservices/RecyclingCalendars/calendar_B_7.pdf",
  																					"3B":"http://gisims2.miamidade.gov/Cservices/RecyclingCalendars/calendar_B_8.pdf","4B":"http://gisims2.miamidade.gov/Cservices/RecyclingCalendars/calendar_B_9.pdf","5B":"http://gisims2.miamidade.gov/Cservices/RecyclingCalendars/calendar_B_10.pdf"},
  																		"fullAddress":"",
  																		"zip":zip,
  																		"folio":"",
  																		"unit":"",
  																		"municipality":cityName,
  																		"municipalityId":"",
  																		"coordinates":{},
  																		"commonLocation":
  																				{"name":"","layer":"","id":""},
  																		"addressData":streetAddress
  																	}
  														}
  													}
  									);
 
	for (var i=0;i<cirmSearchResult.docs.length;i++)
	{
		//Breaking String to get just the article name.
		var kbArticleIndex = cirmSearchResult['docs'][i]['url'].lastIndexOf("/") + 1;
		var kbArticleName = cirmSearchResult['docs'][i]['url'].substr(kbArticleIndex);
		
		if (kbArticleName==kbArticleNameCompareTo)
		{	
			articlePos=i;
			console.log('Match Found at position: '+articlePos);
			break;		
		}
		else
		{
			//console.log('Test Case ID failed: Match Not Found');
		
		}
	}
	return articlePos;
}

this.searchForServiceRequest=function(srNameCompareTo)
	{	
	/*This function is used to search for a particular service request in the search result.
	srNameCompareTo=SR Name that needs to be searched in the search results.
	*/
		var topMostSR;
		var srCounter=0;
		var srSearchResult=false;
		for (var i in cirmSearchResult['docs'])
		{
			for (var j in cirmSearchResult['docs'][i]['ontology'])
			{
				var serviceRequest=cirmSearchResult['docs'][i]['ontology'][j];
				var test =cirm.refs.serviceCases[serviceRequest];
				if (test!=undefined && srCounter==0)
				{
					topMostSR=test['iri'];
					srCounter=srCounter+1;
				}
			}
		}
		if (topMostSR.toUpperCase()==srNameCompareTo.toUpperCase())		
		{
			srSearchResult=true;
		}
		return srSearchResult;
	}

 }).undo(function () {
  });  
  initialized = true;
});

wait(function () { return initialized; }, 30*1000);

// == SECTION SEARCH FUNCTIONALITY ANSWER HUB SCREEN : SEARCH WITHOUT ADDRESS VALIDATION

// @searchFeature

//==@TestId=TC0001
print(searchForArticlePosition("stray dogs", "kbarticle_889431.html")>-1);
// =>  true

//==@TestId=TC0002

//"http://kb.miamidade.gov:8400/pkbi/content/kbarticle_1017136.html"
print(searchForArticlePosition("PSR", "kbarticle_1017136.html")>-1);
// =>  true

//==@TestId=TC0003

//"http://kb.miamidade.gov:8400/pkbi/content/kbarticle_1017136.html"
print(searchForArticlePosition("multiple folio request", "kbarticle_1017136.html")>-1);
// =>  true

//==@TestId=TC0004

//"http://kb.miamidade.gov:8400/pkbi/content/kbarticle_521293.html"
print(searchForArticlePosition("dog tag", "kbarticle_521293.html")>-1);
// =>  true

//==@TestId=TC0005

//"http://kb.miamidade.gov:8400/pkbi/content/kbarticle_915210.html"
print(searchForArticlePosition("lost easy card", "kbarticle_915210.html")>-1);
// =>  true

//==@TestId=TC0006

//"http://kb.miamidade.gov:8400/pkbi/content/kbarticle_2854113.html"
print(searchForArticlePosition("missed garbage", "kbarticle_2854113.html")>-1);
// =>  true

//==@TestId=TC0007

//"http://kb.miamidade.gov:8400/pkbi/content/kbarticle_1239053.html"
print(searchForArticlePosition("pay taxes", "kbarticle_1239053.html")>-1);
// =>  true

//==@TestId=TC0008

//"http://kb.miamidade.gov:8400/pkbi/content/kbarticle_679678.html"
print(searchForArticlePosition("Glasses", "kbarticle_679678.html")>-1);
// =>  true

//==@TestId=TC0009

//"http://kb.miamidade.gov:8400/pkbi/content/kbarticle_1224521.html"
print(searchForArticlePosition("Runner", "kbarticle_1224521.html")>-1);
// =>  true

//==@TestId=TC0010

//"http://kb.miamidade.gov:8400/pkbi/content/kbarticle_1288032.html"
print(searchForArticlePosition("doc stamps", "kbarticle_1288032.html")>-1);
// =>  true

//==@TestId=TC0011

//"http://kb.miamidade.gov:8400/pkbi/content/kbarticle_1339169.html"
print(searchForArticlePosition("Virginia Gardens", "kbarticle_1339169.html")>-1);
// =>  true

//==@TestId=TC0012

//http://kb.miamidade.gov:8400/pkbi/content/kbarticle_1219973.html
print(searchForArticlePosition("Scooter license", "kbarticle_1219973.html")>-1);
// =>  true

//== SECTION SEARCH FUNCTIONALITY ANSWER HUB SCREEN : SEARCH WITH ADDRESS AND SERVICE REQUEST VALIDATION

//==@TestId=TC0013
//http://kb.miamidade.gov:8400/pkbi/content/kbarticle_1337236.html
// Dead Animal Pickup COM
print(searchForArticlePositionWithAddress("dead animal pickup", "kbarticle_1337236.html","1621 SW 30th Avenue","Miami","")==0 && searchForServiceRequest("http://www.miamidade.gov/cirm/legacy#COMDANPU"));
// =>  true

//==@TestId=TC0014
	//http://kb.miamidade.gov:8400/pkbi/content/kbarticle_946669.html
	// Dead Animal Pickup MD
print(searchForArticlePositionWithAddress("dead animal pickup", "kbarticle_946669.html","8216 SW 44th St","UNINCORPORATED MIAMI-DADE","")==0 && searchForServiceRequest("http://www.miamidade.gov/cirm/legacy#ASDEADPU"));
// =>  true

//== SECTION SEARCH FUNCTIONALITY ANSWER HUB SCREEN : SEARCH WITH ADDRESS VALIDATION

//==@TestId=TC0015
	//http://kb.miamidade.gov:8400/pkbi/content/kbarticle_928649.html
print(searchForArticlePositionWithAddress("Street light out", "kbarticle_928649.html","10799 SW 8th Street","","33174")==0);
// =>  true

//==@TestId=TC0016

//http://kb.miamidade.gov:8400/pkbi/content/kbarticle_928649.html
print(searchForArticlePositionWithAddress("Street light out", "kbarticle_928649.html","2930 SW 96th Ave","","33165")==0);
// =>  true

//==@TestId=TC0017

//http://kb.miamidade.gov:8400/pkbi/content/kbarticle_497393.html
print(searchForArticlePositionWithAddress("Curbside recycling", "kbarticle_497393.html","830 Almeria Ave","","33134")==0);
// =>  true
