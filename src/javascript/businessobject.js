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
define(["jquery", "U", "rest", "cirm", "uiEngine"], function($, U, rest, cirm, UI)
{

function BusinessObject(data) {
    // The business object data. At a minimum, it contains a 'type', 'boid' and 'iri' properties
    this.data = data;    
    // The workflow context for this business object.
    //this.context = context;
    
    
    return this;
}

/**
 * Initiate a UI wizard to drive a business object workflow within a DOM parent element..
 * The wizard at the current state of the BO workflow, whatever that may be and attempts
 * to complete it by prompting the user for all unknown properties that will make the BO
 * reach a goal state. Whenever the wizard is complete or reaches an "impass" (workflow must
 * be put in waiting state), it invokes the continuation function argument.
 *  
 */
BusinessObject.prototype.runWorkflowWizard = function (parentElement, continuation) {
    var self = this;
    cirm.op.async().post('/workflow/create', {data:JSON.stringify(this.data)}, function (result) {
        if (result.ok || result.error == 'workflow-exists') {
            self.context = result.context;
            self.data = self.context.bo;
            self.runUiWizard(parentElement, continuation);
        }
        else
            alert('Problem starting workflow ' + result.error);
    });
}

BusinessObject.prototype.applyFormChanges = function(form, subject, propname) {
    var destination = null; // will refer to the part in the nested data structure that needs updating
    var findsubject = function(parent, subject) {
        var result = null;
        if (typeof parent != "object")
            return result;
        $.each(parent, function(name,value) {
                if (value.iri && value.iri == subject) 
                    result = value;
                else if (parent.hasOwnProperty(name)) {
                    result = findsubject(value, subject);
                }
                if (result)
                    return false;
        });
        return result;
    };
    if (subject == this.data.iri)
        destination = this.data.properties;
    else
        destination = findsubject(this.data.properties, subject);
    if (!destination) {
        console.error("Can't find subject " + subject);
        destination = this.data.properties;
    }
    var formdata = U.nestify($(form).cirmSerializeObject());
    var ext = destination;
    if (propname) {
        ext = destination[propname] = destination[propname] || {};        
    }
    // Update latest
    $.extend(true, ext, formdata);
}

BusinessObject.prototype.runUiWizard = function(parentElement, continuation) {
    var self = this;    
    var objectVersions = [];
    var op = cirm.op.async();
    var ui = cirm.ui.async();
    var makeUI = function(laststep, continuation) {
        var subject = laststep.atom.subject.iri;
        if (laststep.variables[subject]) {
            subject = laststep.variables[subject].individual;
        }
        var iri = laststep.atom.predicate.iri;
        // Save current version
        ui.get('/propertyEditor', {property:iri}, function (result) {
                var comp = UI.engine.makeDomTree(result);
                var btnBack = $('<button type="button">Back</button>').click(goBack);
                var btnContinue =  $('<button type="button">Continue</button>').click(function() {
                        objectVersions.push({step:laststep.id, data:U.clone(self.context)});
                        self.applyFormChanges(comp, subject, result.isStructured ? iri.split('#')[1] : null);
                        op.post('/workflow/back',
                            {data:JSON.stringify({context:self.context}), count:1},
                            function(result) {
                                self.context = result.context;
                                self.data = self.context.bo;
                                goForward();
                            });
                });
                $(parentElement).empty();
                $(comp).append(btnBack).append(btnContinue);
                $(comp).appendTo(parentElement);
        });
    };
    var goForward = function() {
        var stopCondition = {variable:'http://www.miamiade.gov/swrl#atomEvalResult',value:'Unknown'};
        op.post('/workflow/forward', 
            {data:JSON.stringify({context:self.context, stopCondition:stopCondition})},
            function(result) {
                if (!result.ok) {
                    // TODO: obviously, need true error handling here
                    alert('Error:' + result.error);
                    return;
                }
                self.context = result.context;
                self.data = self.context.bo;
                if (self.context.currentStep.type == 'end') {
                    alert('Workflow done.');
                    continuation();
                }
                var laststep = self.context.history[self.context.history.length-1];
                switch (laststep.type) {
                    case 'atom-eval-task':
                        makeUI(laststep, continuation);                  
                        break;
                    default:
                        continuation();
                }
        });
    };
    var goBack = function() {        
        var prev = objectVersions.pop();
        if (!prev) {
            alert('Already at beginning.');
            return;
        }
        console.log('go back to', prev);
        op.post('/workflow/backto', {data:JSON.stringify({context:prev.data}), tostep:prev.step}, function(result){
                if (!result.ok) {
                    alert('Error:' + result.error);                    
                }
                self.context = result.context;
                self.context.data = self.data = prev.data;
                goForward();
        });
    };
    var stepUI = function() {
    };
    goForward();
};

return {
    BusinessObject:BusinessObject
}

});
