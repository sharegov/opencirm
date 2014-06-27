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
define(["jquery", "U", "rest", "cirm", "legacy"], 
   function($, U, rest, cirm, legacy)   {
	/**
	 * StatisticsModel is a view model that represents the cirm
	 * stats endpoint and its associated data fields.
	 */
	function StatisticsModel(){
		var self = this.self;
		self.key = {component:ko.observable("ALL"), action: ko.observable("ALL"), type:ko.observable("ALL")};
		self.servers = ko.observable(["s2030050"]);
		self.currentServer = ko.observable(self.server()[0]);
		self.currentStats = ko.observable({});
		
		self.getAllStats() {
			return cirm.top.get("")
		};
		
		
	}
	
});