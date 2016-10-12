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
define(['jquery', 'rest', 'U','store!'], function($, rest, U, store) {

    var baseurl = document.location.protocol + '//' + document.location.host;
    var users = new rest.Client(baseurl + "/users");
    var user = {
        displayLoggedIn: function() {
            var html = "Logged in as " + user.FirstName + " " + user.LastName + ":&nbsp;";
            $('#loggedInPanel').html(html).append(
                $('<button type="button">Logout</button>').click(user.logout));            
        },
        displayLoggedOut: function() {
            var html = "Not logged in:&nbsp;";
            $('#loggedInPanel').html(html);                        
        },
        initLoginForm: function() {
          $('#iPassword').keydown(function(event) {
            if (event.which == 13)
              user.authenticate('', $('#iUsername').val(), $('#iPassword').val());
          });
          $('#btnLogin').click(function () {
              console.log('authentication...');
              user.authenticate('', $('#iUsername').val(), $('#iPassword').val());
          });
           $('#loginForm')[0].reset();                
        },
    	ensureLoggedin : function() {
            user.username = $.cookie("username");
            if (user.username == null)
                return false;
            var r = users.postObject('/profile', {'username':user.username, 'access':true});
            var profile = null;
            if (r.ok) {
                profile = r.profile;
                user.FirstName = profile.first_name;
                user.LastName = profile.last_name;                                
                var cl = $.extend({}, profile);
                delete cl.access; 
                user.setLocalInfo(user.username, cl);
            }
            else if (r.error == 'unavailable') { // LDAP down
                profile = user.getLocalInfo(user.username);
            }
//            console.log('got profile', profile);
            if (profile == null)
                return false;
            $.extend(user, profile);
            user.displayLoggedIn();  
            // remove when permissions are debugged and complete...this is useful
            // to see groups, access policy and overall user information.
            //console.log('login', user);
            return true;
    	},
        login : function(data) {
            $.cookie("username", data.username, { path: '/' });
            // need to send back to the server to avoid repreated lookups on each REST request
            $.cookie("usergroups", data.groups.join(";"), { path: '/' });
            //
            // Load startup
            //
            window.location.href = "/html/startup.html";            
        },
        logout : function() {
            $.cookie("username", null, { path:'/'});
            $.cookie("usergroups", null, { path:'/'});
            user.deleteLocalInfo(user.username);
            user.username = null;
            user.displayLoggedOut();
            window.location.href = "/html/login.html";
        },
        authenticate : function(realm, username, password) {
            var r = users.postObject('/authenticate', {username:username,
                                                       password:password,
                                                       provider:realm,
                                                       groups:true});
            if (r.ok) {
                user.login(r.user);
            }
            else if (r.error == 'unavailable')  {
                profile = user.getLocalInfo(username);
                if (profile != null)
                    user.login(profile);
                else
                    alert('Failed to access user profile, server maybe temporarily unavailable, please try again later.');
            }
            else {
                alert('Authentication failure:' + r.error);
            }
        },
        isAllowed : function(action, object) {
        	if (user.allClientsExempt === true) 
        	{
        			return true;
        	}
            if (!user.access) return false;
            var actionIRI = "http://www.miamidade.gov/ontology#" + action;
            var objectIRI = "http://www.miamidade.gov/cirm/legacy#" + object;
			var hasMatchingActionAndObject = false;
			var accessArr = U.ensureArray(user.access);
            $.each(accessArr, function(idx, usergroup) {
            	if(usergroup.iri && usergroup.iri == "http://www.miamidade.gov/ontology#CirmAdmin") 
            	{
            		hasMatchingActionAndObject = true;
            		return;
            	}
            	if (!usergroup.hasAccessPolicy) return;
            	var hasAccessPolArr = U.ensureArray(usergroup.hasAccessPolicy);
                $.each(hasAccessPolArr, function(idx2, accesspol) {
            		var hasMatchingAction = false;
            		var hasActionArr = U.ensureArray(accesspol.hasAction);
	            	$.each(hasActionArr, function(idx3, actionInPol) {
	            		if ((actionInPol.iri && actionInPol.iri == actionIRI)
	            				|| (!actionInPol.iri && actionInPol == actionIRI)) {
	            				hasMatchingAction = true;
	            				return false;
	            			}
	            	});
            		if (hasMatchingAction === true) {
            			var hasObjectArr = U.ensureArray(accesspol.hasObject);
	            		$.each(accesspol.hasObject, function(idx4, objectInAP) {
	            			if ((objectInAP.iri && objectInAP.iri == objectIRI)
	            					|| (!objectInAP.iri && objectInAP == objectIRI)){
	            				hasMatchingActionAndObject = true;
	            				return false;
	            			}
	            		});
            		} 
            		if (hasMatchingActionAndObject === true) return false;	
            	});
            	if (hasMatchingActionAndObject === true) return false;
            });
            return hasMatchingActionAndObject;
        },
        isViewAllowed : function(object) {
        	return user.isAllowed("BO_View", object);
        },
        isUpdateAllowed : function(object) {
        	return user.isAllowed("BO_Update", object);
        },
        isNewAllowed : function(object) {
        	return user.isAllowed("BO_New", object);
        },
        /** 
         * Is the current user allowed to modify ontology configuration.
         * 
         * TODO Implement permission check for 
         * configuration changes (CirmAdmin module) here.
         * 311Hub UI will only check for a new version of an SR type, 
         * if the current user has permissions to modify the ontology configuration.
         */
        isConfigAllowed: function() {
        	return true;
        },
        setLocalInfo: function(username, info) {
            if (store.cirmdb()) {
                store.kv().put("localinfo_" + username, JSON.stringify(info));
            }
            else  {
//                console.log(info);
                $.cookie("localinfo_" + username, JSON.stringify(info), { path: '/', expires:9999 });
//                console.log('set profile cookie', "localinfo_" + username,JSON.stringify(info), $.cookie("localinfo_" + username)); 
            }
        },
        // We store information about every user logged in
        getLocalInfo: function (username) {
            var profile = null;
            if (store.cirmdb()) {
                // use local storage for this
                // TODO
            }
            else {
                var cookievalue = $.cookie("localinfo_" + username);
                profile =  (cookievalue) ? JSON.parse(cookievalue) : null;
            }
            if (profile == null) return null;
            if ($.grep(profile.groups, function(x){return x.indexOf("Communications_Department") > -1 }).length == 0)
                profile = null;
            profile.access = users.postObject('/accesspolicies', profile.groups);
            return profile;
        },
        deleteLocalInfo:function (username) {
            if (store.cirmdb()) {
                // TODO
            }
            else
                $.cookie("localinfo_" + username, null, {path:'/', expire:-5});
        },
        username:null,
        FirstName:null,
        FastName:null,
        provider:null,
        email:null,
        allClientsExempt:false,
    };
    return user;
});
