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
define(["jquery"], 
   function($)   {
	/**
	 * StatisticsModel is a view model that represents the cirm
	 * stats endpoint and its associated data fields.
	 */
	function StatisticsModel() {
		var self = this;
		self.key = {component:"ALL", action: "ALL", type:"ALL"};
		//"s2030050"
		self.servers = ["s2030051","s2030057","s2030059","s2030060"];
		self.stats = {};
		self.errorChart = {};
		
		self.getAllStats = function () {
			for(var i = 0; i < self.servers.length; i++)
			{
				self.getStats(self.servers[i]);
			}
		};
		
		self.getStats = function (server) {
			$.ajax({
		        url: "https://" + server + ".miamidade.gov"+"/statistics/query",
		        data: self.key,
		        dataType: "jsonp",
		        jsonpCallback: server + 'statsCallback',
		        success: function(data) {
		        	self.stats[server] = data;
		        }
		    });
		};
		
		
	}
	
	var statistics = new StatisticsModel();
	
	 if (modulesInGlobalNamespace)
	        window.statistics = statistics;
	    
	 return statistics;
	
	
});