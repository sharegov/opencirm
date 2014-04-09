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
define(["jquery", "rest"], function($, rest) {  
  var exports = {};

  var fixt = exports.fixt = function() {
    if (arguments.length == 0) 
      return new Fixture();
    var args = Array.prototype.slice.call(arguments);
    var name = "";
    if (typeof args[0] == "string")
      name = args.shift();
    var op = args.length > 0 ? args.shift() : null;
    if (op != null && typeof op != "function")
      throw "Function argument expected to define the fixture as an operation.";
    var inv = args.length == 0 ? null : args.shift();
    if (inv != null && typeof inv != "function")
      throw "Function argument or null expected to undo the fixture.";
    var result = new Fixture(name, op, inv);
    if (name)
      fixt[name] = result;
    console.log('added fixture ' + name, result);
    return result;
  };
  
  var Fixture = exports.Fixture = function(name,f,invf) {
    this.name = name;
    this.op = f;
    this.invf = invf;
    this.dependencies = {};
  };

  Fixture.prototype.do = function(f) {
    if (f) { this.op = f; return this;  } else return this.op;
  };

  Fixture.prototype.undo = function(f) {
    if (f) { this.invf = f; return this;  } else return this.invf;
  };

  Fixture.prototype.applyDo = function(ctx) {
    this.op.apply(ctx);
  };

  Fixture.prototype.applyUndo = function(ctx) {
    if (this.invf)
      this.invf.apply(ctx);
  };

  Fixture.prototype.depends = function() {
    if (arguments.length == 0)
      return this.dependencies;
    for (var i = 0; i < arguments.length; i++) {
      var n = arguments[i];
      this.dependencies[n] = function () { return fixt[n]; };
    }
  };

  // Test fixtures should operate on a test context/environment.
  // Each fixture is a reversible operation. Fixtures can be composed
  // where they are applied in sequence and undone in reverse. Each
  // test context is established by enacting a bunch of fixtures. 
  // Each test can be declared to require a bunch of fixtures. Yet
  // fixtures are different the pre-conditions for a test. Pre-conditions
  // should be considered something external to the test environment 
  // 
  // The test contest should be really the "this" of a test. No reason
  // to ignore that nice JavaScript mechanism. So whenever a test
  // is executed and it refers to "this", that will give it access to all
  // properties established by fixtures. For this to work, fixtures should
  // be invoked with the "this" context of the test. So when a fixture
  // has to modify the test context, it can just work on "this".
  $.extend(fixt, {
    compose:function(f1,f2) {
      return function() { f1(); f2(); }
    },
    make:function(op, inverse) {
      var f = function() { op(); };
      f.inv = function() { inverse(); };
      return f;
    },
    // A bunch of rest fixtures
    rest:{
      withServerResource:function(url, jsonObject) {
        var id = null;
        return fixture.make(function() {
          var R = new rest.Client(url);
          id = R.postObject("", jsonObject);
        },  function() {
          R.del("", id);
        });
      },
      loadResources:function(propname, url, params) {
        return fixture.make(function() {
          var R = new rest.Client(url);
          this[propname] = R.get("", params);
        }, function() {
          delete this[propname];
        })
      }
    }
  });
  
  function collectFixtureRefs(comments) {
    var names = [];
    $.each(comments, function(i,o) {
      var s = o.value.trim();
      if (s[0] != "@") return;
      names.push.apply(
        names, 
        $.map(s.substring(1).split(","), $.trim)
      );
    });
    return names;
  }

  function applyFixture(ctx, F) {
    if (ctx.__fixtures__[F.name]) return;
    $.each(F.depends(), function(n,v) {
      applyFixture(ctx, fixt[n]);
    });
    F.applyDo(self); 
    ctx.__fixtures__[F.name] = true;
  }

  function removeFixture(ctx, F) {
    if (!ctx.__fixtures__[F.name]) return;
    $.each(F.depends(), function(n,v) {
      removeFixture(ctx, fixt[n]);
    });
    F.applyUndo(self); 
    delete ctx.__fixtures__[F.name];
  }

  function fixTest(E) {
    var ctx = this;
    ctx.__fixtures__ = ctx.__fixtures__ || {};
    var fnames = collectFixtureRefs(doctest.esprima.parse(E,{comment:true}).comments);
    $.each(fnames, function(i,n) { 
      if (!fixt[n]) 
        throw "No fixture " + n; 
      applyFixture(ctx, fixt[n]);
    });
  }

  function unfixTest(E) {
    var ctx = this;
    if (!ctx.__fixtures__) return;
    var fnames = collectFixtureRefs(doctest.esprima.parse(E,{comment:true}).comments);
    $.each(fnames, function(i,n) { 
      removeFixture(ctx, fixt[n]);
    });
  }

  doctest.addRunHook(fixTest, unfixTest);

  return window.T = exports;
});