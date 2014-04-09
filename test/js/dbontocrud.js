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
require({
  baseUrl: "/js",
  paths: {
    "jquery": "jquery-1.9.1",
    "jquery.ui":"jquery-ui-1.10.3.custom",
    "jquery.iframe": "jquery.iframe-transport",
    "jquery.fileupload": "jquery.fileupload",
    "jquery.cookie":"jquery.cookie",
    "jquery.flexslider":"jquery.flexslider-min",
    "jquery.tipsy":"jquery.tipsy",
    "skeleton.tabs":"tabs",
    "popcorn":"popcorn-complete",
    "ahguser":"../ahguser",
    "vc":"../vc",
    "utils":"../utils",
    "rest":"http://sharegov.org/jslib/rest",
    "U":"http://sharegov.org/jslib/U",
    "T":"testlib"
  }, 
  shim : {
    'angular':[],
    'jquery':{ deps:[], exports:'$'},
    'jquery.flexslider':['jquery'],
    'jquery.tipsy':['jquery'],
    'jquery.cookie':['jquery'],
    'jquery.ui':['jquery'],
    'jquery.iframe':['jquery'],
    'jquery.fileupload':['jquery'],
    'skeleton.tabs':['jquery']
  }
  , urlArgs: "cache_bust=" + (new Date()).getTime()
}, [ "jquery", "rest", "ahguser", "utils", "popcorn", "vc", "T",
     "jquery.ui", "jquery.flexslider", "jquery.tipsy", "skeleton.tabs"],
function ($, rest, ahguser, utils, popcorn, vc, T) {     
  angular.bootstrap(document, ['virtualcoach']);
  // Define all needed test fixtures
  T.fixt("TestUser").do(function () {
    r.postObject('/user/register', 
                 {email:'biordanov@acm.org',
                  firstName:'Testing', 
                  lastName:'Guru', 
                  password:'password'});  
    var u = r.get('/data/list', {pattern:{entity:'user', 
                                          email:'biordanov@acm.org'}});    
    this.user = u[0];
    this.user.should.have.property('hghandle');      
  }).undo(function () {
    r.del('/data/entity/' + this.user.hghandle);
  });
  T.fixt("NewVideos").do(function () {
    var x = r.postObject('/data/entity', {entity:'video', 
                                  user:this.user.hghandle,
                                  timestamp:new Date().getMilliseconds(),
                                  location:'/home/borislav/virtualcoach/src/main/www/videos/shortversion.mp4'});
    if (x.ok)
      this.newvideo = x.entity;
  }).undo(function () {
    r.del('/data/entity/' + this.newvideo.hghandle);
  }).depends("TestUser");
  initialized = true;
});

wait(function () { return initialized; }, 30*1000);

// == SECTION User Registration

// @NewVideos

print(newvideo.location);

// => ...shortversion.mp4
  
// == SECTION Video APIs

// Add video

var L = r.get('/video/list/asdfas');

print(r);
// => ...
