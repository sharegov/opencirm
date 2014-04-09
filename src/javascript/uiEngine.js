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
define(['jquery', 'jquery-ui', 'rest', 'cirm', 'U'], function($, jqueryui, rest, cirm, U) {

// global function for rendering an arbitrary object with its template
function toDOM(obj) 
{
            if (U.isDOMElement(obj)) {
                return obj;
            }
            // first, check if it's a UI component to be rendered
            var renderer = uiEngine.getRenderer(obj.head || obj.type);
            if (renderer) {
                return renderer(obj);
            }
            // otherwise, try to find a template to display whatever object
            // it is
            var iri, text;
            if (obj.iri)
                text = cirm.ui.get("/template/" + encodeURIComponent(obj.iri), {type:obj.type});
            else if (obj.type)
                text = cirm.ui.get("/template/" + encodeURIComponent(obj.type));
            else
                text = cirm.ui.get("/template/Thing");
            var div = document.createElement("div");
            $(div).append($.tmpl(text.hasContents, obj));
            return div;            
};

function toHTML(obj) 
{
    var div = this.toDOM(obj);
    return div.innerHTML;            
}

var uiEngine =  (function(self)
{
    self.setId = function(element, localName)  {
        element.setAttribute('id', this.id || 
            (this.scopeId ? (this.scopeId + "." + localName) : localName));
    }
    
    self.collectFormInput = function(parent) {
        parent = parent || (this.id ? document.getElementById(this.id) : document.body);
        
    }
    
    self.editMappers = {
        'boolean' : function(value, dom) {
            if ( (dom.attr('type') == "checkbox" || dom.attr('type') == "radio") && 
                  (value === true || value == "true")) // how exactly do we test for true here?
                dom.attr('checked', true);
            // May recognize and handle a yes/no dropdown here?
            else
                dom.val(value);
        },
        'string' : function(value, dom) {
            dom.val(value);
        },
        'int' :  function(value, dom) {
            dom.val(value);
        },
        'object' : function(value, dom) {
            if (dom.is('select')) {
                dom.val(value.iri);
            }
             else
                 populateObjectEditor(value, dom, $(dom).attr('name'));
        }
    };
    
    /*
    Populate all fields of a form editor from the values of an object. This is used to initialize a form
    with an existing object for an edit operation. For each field in the object, we try to find a 
    corresponding form field and if we do we initialize with the value. We can have complex values which are
    nested objects and we can have sub-forms (collections of HTML form fields). In general, a complex value 
    may be translated into a single HTML field (e.g. an input that is parsed to populate the complex value) and
    similarly a simple value (e.g. date) may be translated into an HTML sub-form.  
    */
    function populateObjectEditor(object, editor, prefix) {
        console.log('populate ', object, editor);
        $.each(object, function(name, value) {
                if (typeof value !== "object")
                    return true;
                if (prefix)
                    name = prefix + "." + name;
                if (!value.type) {
                    console.warn('No type for value', value);
                    return true; // continue with the rest of the fields
                }
                var dom = $('[name="' + name + '"]', $(editor));
                if (!dom) {
                    console.warn('No dom with name ' + name + ' in editor.', editor);
                    return true;
                }
                var mapper = self.editMappers[value.type];
                if (!mapper) {
                    mapper = self.editMappers['object'];
//                    console.warn('No type mapper for type', value.type);
//                    return true;
                }
                mapper.call(editor, value.value, dom);
        });
    }
    
    function formRenderer(formSpec)
    {
        var form = this.parentForm ? document.createElement('div')  : document.createElement('form');
        form.setAttribute('name', this.id ? this.id : formSpec.iri ? formSpec.iri : '');
        for (x in formSpec.attributes)
            form[x] = formSpec.attributes[x];
        var fieldSequence = formSpec.fieldSequence;
        if (fieldSequence === undefined) fieldSequence = formSpec.fieldSet;
        if (fieldSequence !== undefined)  {            
            for (field_idx  in fieldSequence)  {
                var field = fieldSequence[field_idx];
                if (field.head === undefined) {
                     continue;
                }
                if (field.label !== undefined) {
                    var label = document.createElement("label");
                    label['for'] = field.iri;
                    label['innerHTML'] = field.label;
                    form.appendChild(label);
                    if (field.head != 'UICheckBox' && field.head != 'UIRadioButton')
                        form.appendChild(document.createElement('br'));
                }
                var domTree = self.recurse({}, self.makeDomTree, [field]);
                if (domTree === undefined || domTree == null) {
                    console.log("no dom tree for " + field);
                    continue;
                }
                else if (domTree.nodeName === 'FORM') {
                    // We replace the form by a nested DIV
                    var nestedForm = document.createElement('div');
                    var sub = domTree.name;
                    nestedForm.setAttribute('name', sub); 
                    for (x in domTree.childNodes) {
                        x = domTree.childNodes[x];
                        var name = x.nodeName;
                        if (name === undefined || name == 'FORM' || name == '#document') {
                            continue;
                        }
                        if ($(x).attr('name'))
                            $(x).attr('name', sub + "." + $(x).attr('name'));
                            //x.id = sub + "." + x.id; 
                        nestedForm.appendChild(x);
                        nestedForm.appendChild(document.createElement('br'));
                    }
                    form.appendChild(nestedForm);
                 }
                else {
                    form.appendChild(domTree);
                    form.appendChild(document.createElement('br'));
                }
            }
        }
        return form;
    }

    function inputBoxRenderer(inputBox)
    {
        var input = document.createElement('input');
        input.type = 'text';
        for (x in inputBox.attributes)
            input[x] = inputBox.attributes[x];
        //this.setId(input, inputBox.attributes.name);
        $(input).attr('name', inputBox.attributes.name);
        return input;
    }

    function checkBoxRenderer(checkBox)
    {
        var input = document.createElement('input');
        input.type = 'checkbox';
        for (x in checkBox.attributes)
            input[x] = checkBox.attributes[x];
        //this.setId(input, checkBox.attributes.name);
        $(input).attr('name', checkBox.attributes.name);        
        return input;
    }
    
    function dropDownListRenderer(dropdown)
    {
    	var input = document.createElement('select');
    	input.multiple = 'multiple';
    	//input.size = '1';
    	if(dropdown.options) {
    		for(x in dropdown.options) {
    			x = dropdown.options[x];
    			var o = document.createElement("option");
    			o.innerHTML = x.label;
    			o.value = x.value;
    			if(x.selected)
    				o.selected = x.selected;
    			input.add(o);
    		};
    	}
        for (x in dropdown.attributes)
            input[x] = dropdown.attributes[x];
        //this.setId(input, dropdown.attributes ? dropdown.attributes.name : null);
        $(input).attr('name', dropdown.attributes ? dropdown.attributes.name : '');
        return input;
    }
    
    function comboBoxRenderer(combobox)
    {
    	var input = document.createElement('select');
    	if(combobox.options) {
			for(x in combobox.options) {
				x = combobox.options[x];
				var o = document.createElement("option");
				o.innerHTML = x.label;
				o.value = x.value;
				if(x.selected)
					o.selected = x.selected;
				input.add(o);
			};
    	}
    	for(x in combobox.attributes)
    		input[x] = combobox.attributes[x];
        $(input).attr('name', combobox.attributes ? combobox.attributes.name : '');

        //Make it a combobox
        $(input).jec();

        return input;
    }
    
    function dateRenderer(datebox)
    {
    	var input = document.createElement('input');
    	input.type = 'text';
    	$(input).datepicker();
        $(input).attr('name', datebox.attributes.name);
        return input;
    }

    function dropDownRenderer(dropdown)
    {
        var input = document.createElement('select');        
        var fillOptions = function(data) {
            $.each(dropdown.data, function(i, x) {
                var o = document.createElement("option");
                o.innerHTML = x.label;
                o.value = x.iri;
                input.add(o);                
            });            
        };
        if (dropdown.options) {
            for (x in dropdown.options)  {
                x = dropdown.options[x];
                var o = document.createElement("option");
                o.innerHTML = x.label;
                o.value = x.value;
                if(x.selected)
                	o.selected = x.selected;
                input.add(o);
            };
        }
        else if (dropdown.data) {
            fillOptions(dropdown.data);
        }
        else if (dropdown.hasDataSource) {
            dropdown.data = cirm.fetchNow(dropdown.hasDataSource);
            fillOptions(dropdown.data);
        }
        for (x in dropdown.attributes)
            input[x] = dropdown.attributes[x];
        //this.setId(input, dropdown.attributes ? dropdown.attributes.name : null);
        $(input).attr('name', dropdown.attributes ? dropdown.attributes.name : '');
        return input;
    }
    
    function actionButtonRenderer(actionButton)
    {
        var input = document.createElement('button');        
        //input.setAttribute('id', 'mybutton');
        for (var x in actionButton.attributes) {
            input.setAttribute(x, actionButton.attributes[x]);
        }
        input.innerHTML = actionButton.content;
        $(input).click(function () {
                var continuation = $(this).closest('[has-continuation]').first().get(0);
                continuation.continuation.call(continuation.continuationScope, actionButton.postTo);
        });
        //this.setId(input, actionButton.attributes.name);
        $(input).attr('name', actionButton.attributes.name);
        return input;
    }
/*    
    function submitButtonRenderer(actionButton)
    {
        var input = document.createElement('button');        
        for (var x in actionButton.attributes) {
            input.setAttribute(x, actionButton.attributes[x]);
        }
        input.innerHTML = actionButton.content;
        $(input).click(function() { document.getElementById('LegacyEmulatorDiv').actions.submitForm() });
        $(input).attr('type', 'button');
        $(input).attr('name', actionButton.attributes.name);
        return input;
    }
*/
    function tableRenderer(table)
    {
        if (!table.hasDataSource) 
            throw new Error("Table component " + table + ", iri=" + table.iri + " has no data source property.");
        var div = document.createElement("div");
        div.meta = table;
        div.setAttribute('id', table.iri);
        var topContainer = div;
        var self = this;
        var htmlForm = this.parentForm;
        if (!htmlForm) {
            htmlForm = document.createElement("form");
            htmlForm.setAttribute('id', table.iri + "-form");
            div.appendChild(htmlForm);
            topContainer = htmlForm;
        }
        if (! table.hasTemplate) {
            // maybe offer some default in the future
            throw new Error("Data Table " + table.iri + " has no template associated.");
        }
        var ajaxObject = {}; // make multiple AJAX calls at once
        var fieldIdBase = table.iri.split('#')[1] + "-form-";
        var form = {};        
        if (table.hasInputField) {  // filter data form
            var formPlaceholder = {};
            for (var field in table.hasInputField) {               
                var F = table.hasInputField[field];
                var name = F.Name;
                if (F.hasPrototype) 
                    F = F.hasPrototype;
                if (F.hasDataSource) {
                    ajaxObject[fieldIdBase + name] = cirm.delayDataFetch(F.hasDataSource);
                    form[name] = F;
                }
                else
                {
                    if (F.isEditorOf) {
                        ajaxObject[fieldIdBase + name] = cirm.ui.delay("/new/"+encodeURIComponent(F.isEditorOf.iri));
                        form[name] = F;
                    }
                    else {
                        form[name] = self.recurse({id:name, parentForm:htmlForm}, self.makeDomTree, [F]);
                    }
                }                
                formPlaceholder[name] = '<div id="' + fieldIdBase +name + '"/>';                
            }
            // This is what the table template needs: the insert the DIV placeholders where those
            // form fields are referred to.
            table['hasInputField'] = formPlaceholder;
        }
        ajaxObject['table.hasDataSource'] = cirm.delayDataFetch(table.hasDataSource, function(data) {
                //console.log('got table data');
                //console.log(data);
                if (data.error) {
                    console.error('Server error while reading table data:' + data.error)
                    table.data = [];
                }
                else {
                    $.each(data, function(i, value) { if (!value.atAddress) value.atAddress = {}; });
                    table.data = data;
                }
        });
        
        // Define standard UITable component actions:
       table.actions = {
            refresh : function() {                
                var formdata = U.nestify($(div).cirmSerializeObject());
                var currquery = eval('(' + table.hasDataSource.hasTemplate.hasContents + ')')
                $.extend(currquery, formdata);
                table.hasDataSource.hasTemplate.hasContents = JSON.stringify(currquery);
                cirm.delayDataFetch(table.hasDataSource, function(data) {
                        table.data = data;
                        // 2. Apply table template
                        $(topContainer).empty();
                        $.tmpl(table.hasTemplate.hasContents, table).appendTo(topContainer);
                        // 3. Insert form field DOM tree in DIV placeholders
                        for (var name in form) {
                            $('#' + fieldIdBase + name).append(form[name]);
                        }                        
                }).call();                
            },
            onRefresh : "document.getElementById('" + table.iri + "').meta.actions.refresh()",
            view : function(iri) {
                var A = iri.split('#')[0].split('/');
                var boType = A[A.length-2];
                var boId = A[A.length-1];
                var bo = cirm.op.get("/get/" + encodeURIComponent(boType) + "/" + boId);
                var viewer = toDOM(bo);
                var dialog = document.createElement("div");
                dialog.setAttribute("title", bo.type + ' ' + bo.iri.split('/').pop().split('#')[0]);
                $(dialog).append(viewer).dialog();                
            },
            onView: function (iri) {
                return "document.getElementById('" + table.iri + "').meta.actions.view('" + iri + "')"
            },
            edit : function(iri) {
                var A = iri.split('#')[0].split('/');
                var boType = A[A.length-2];
                var boId = A[A.length-1];    
                var bo = cirm.op.get("/get/" + encodeURIComponent(boType) + "/" + boId);
//                console.log(bo);
                var editor = cirm.ui.get('/editor', {individual:iri});
//                console.log(editor);
                var actionList = cirm.op.get('/actions/' + boType + '/' + boId);
                //console.log(actionList);
                var dialog = document.createElement("div");
                dialog.setAttribute("title", "Edit " + boType + " - " + boId);
                dialog.setAttribute("businessObject", iri);
                var editorDom = self.makeDomTree(editor);
                $(dialog).append(editorDom);
    //            console.log(editorDom);
                $.each(actionList, function(i, a) {
                        $(dialog).append($('<button type="button">' + a.label + '</button>').click(
                            function() { actionManager.run.call(dialog, a.hasImplementation); }));                            
                });
                populateObjectEditor(bo, editorDom)
                $(dialog).dialog();
            },
            onEdit: function (iri) {
                return "document.getElementById('" + table.iri + "').meta.actions.edit('" + iri + "')"
            }
        };
        
        console.log('fetching table info', ajaxObject);
        
       rest.onObjectReady(ajaxObject, function () {
                // 1. Convert all input form fields to DOM trees
                $.each(form, function(name, value) {
                        if (value != null && !U.isDOMElement(value) && typeof value != "function") {
                            if (value.hasDataSource)
                                value.data = ajaxObject[fieldIdBase + name];
                            else if (value.isEditorOf)
                                value = ajaxObject[fieldIdBase + name];                                                    
                            form[name] = self.recurse({id:name, parentForm:htmlForm}, self.makeDomTree, [value]); 
                        }
                });
                // 2. Apply table template
                $.tmpl(table.hasTemplate.hasContents, table).appendTo(topContainer);
                // 3. Insert form field DOM tree in DIV placeholders
                for (var name in form) {
                    $('#' + fieldIdBase + name).append(form[name]);
                }
        });
        return div;
    }
     
    function panelRenderer(panel) {
        var domid = panel.iri.split('#')[1];
        var div = document.createElement("div");
        div.meta = panel;
        div.setAttribute('id', domid);
        var contentsId = domid + '-contents';
        var templateData = {
            title: panel.Title,
            content : '<div id="' + contentsId + '"/>'            
        };
        panel.actions = templateData.actions = {
            close : function () {
                var el = document.getElementById(domid);
                el.parentNode.removeChild(el);
            },
            onClose : "document.getElementById('" + domid + "').meta.actions.close()",
            minimize : function () {
            },
            maximize : function () {
            },
            back: function () {
            },
            forward: function() {
            }
        }
        $.tmpl(panel.hasPrototype.hasTemplate.hasContents, templateData).appendTo(div);
        var el = $(div).find('#' + contentsId).get(0); 
        var content = self.makeDomTree(panel.hasContents); 
        $(el).append(content);
        $(div).resizable();
        $(div).draggable();
        return div;        
    }

    function autoCompleteInputRenderer(auto) {
        var input = document.createElement('input');
        for (x in auto.attributes)
           input[x] = auto.attributes[x];
        $(input).attr('name', auto.label ? auto.label : ''); 
        if (auto.hasDataSource) {
            //var suggestData = cirm.fetchNow(auto.hasDataSource);
            //console.log('fetchig auto complete data');
            cirm.delayDataFetch(auto.hasDataSource, function (suggestData) {
                    //console.log('auto complete data', suggestData);
                var transform = null;
                if (auto.hasTransformFunction)
                    transform = eval("(function() { return" + auto.hasTransformFunction + "})()" );
                if (transform)
                    suggestData = $.map(suggestData, transform);
                suggestData.sort( function(a,b) { 
                	if(a.label && b.label) 
                		return a.label.localeCompare(b.label); 
                	else 
                		return -1;
                });
                $(input).autocomplete({
                   source: function(s, back) {
                      var matcher = new RegExp("^" + $.ui.autocomplete.escapeRegex(s.term), "i" );
                      back($.grep( suggestData, function(value) {
                            return matcher.test( value.label || value.value || value );
                      }));
                  }
                });
            }).call();
        }
        return input;
    }
    
    var componentMap = 
    {
        "UIForm" : formRenderer,
        "UIInputBox" : inputBoxRenderer,
        "UICheckBox" : checkBoxRenderer,
        "UIDate" : dateRenderer,
        "UIDropDown" : dropDownRenderer,        
        "UIDropDownList" : dropDownListRenderer,
        "UIComboBox" : comboBoxRenderer,
        "UIActionButton" : actionButtonRenderer,
        "UITable": tableRenderer,
        "UIPanel": panelRenderer,
        "UIAutoCompleteInput":autoCompleteInputRenderer
    }
  
    self.recurse = function(context, method, args)
    { 
        var child = U.beget(this, context);
        if (this.id) {            
            child.scopeId = this.id;
            if (!context.id) // we don't want to inherit the id property from the parent prototype
                child.id = null;
        }
        return method.apply(child, args);
    }
        
    self.makeDomTree = function(uispec)
    {
        var head = uispec.head || uispec.type;
        if (head === undefined) { return null; }
        var renderer = componentMap[head];
        if (renderer == null)
            return null;
        return renderer.apply(this, [uispec]);
    }

    self.getRenderer = function(type) 
    {
        return componentMap[type];
    }
    
    return self;
})({});

var actionManager = (function(self) {
        self.run = function(action) {
            console.log('running action in context:');
            console.log(action);
            switch (U.IRI.name(action.hasKind.iri)) {
                case "RestServiceOperation":
                    self.callRestService.call(this, action);
                    break;
                default: 
                {
                    throw "Uknown action kind '" + action.hasKind + "'";
                }
            };
        },
        self.callRestService = function(action) {
            console.log('call service at ' + action.hasUrl);
            console.log(this);
            var iri = $(this).attr('businessObject');            
            var url = action.hasUrl.replace("{type}", U.IRI.type(iri)).replace("{id}", U.IRI.id(iri));
            var service;
            var relUrl = url.split('/')[1]
            switch (relUrl) {
                case "op": service = cirm.op; break;
                case "meta": service = cirm.meta; break;
                case "ui": service = cirm.ui; break;
                default: service = cirm.top; break;
            };
            url = url.substring(relUrl.length + 1);
            service[action.hasHttpMethod.Name].call(service, url, {data:JSON.stringify(U.nestify($(this).cirmSerializeObject()))});  
        };
        return self; 
})({});

return {
    engine:uiEngine,
    toDOM:toDOM,
    toHTML:toHTML
};

});