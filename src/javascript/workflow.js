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
define(["jquery", "jit"], function($, ignore1)   {        

    function Workflow(data) 
    {
        this.data = data;
        this.graphData = null;
        
        this.getDisplayArgument = function(arg) {
            switch (arg.type) {
                case 'variable' : return arg.iri.split('#')[1]; break;
                case 'literal' : return arg.value; break;
                case 'individual': return arg.iri.split('#')[1]; break;
            }
        };
        
        this.getDisplayAtom = function(atom) {
            switch (atom.type) {
                case 'same':  
                    return "same(" + this.getDisplayArgument(atom.first) + 
                                "," + this.getDisplayArgument(atom.second) + ")"; 
               case 'object-atom':
                   return this.getDisplayArgument(atom.subject) + " " +
                    atom.predicate.iri.split('#')[1] + " " + this.getDisplayArgument(atom.object);
               case 'data-atom':
                   return this.getDisplayArgument(atom.subject) + " " +
                    atom.predicate.iri.split('#')[1] + " " + this.getDisplayArgument(atom.object);
               case 'builtin':
                    var args = "";
                    var self = this;
                    $.each(atom.arguments, function(i, v) { args += self.getDisplayArgument(v) + " "; });
                    return args;
               default: return 'atom'; 
            }
        };
        
        this.followFlow = function(step, nodes) {
            var node = nodes[step.id];
            if (node) return node;
            node =   { id : step.id,            
                            name : step.type,
                            data : {$type:'boxNode'},
                            adjacencies : []};    
            nodes[step.id]  = node;
            switch (step.type) {
                case 'branch-on-boolean':
                    node.name = /* step.variable.split('#')[1] + */ '?';
                    break;
                case 'atom-eval-task':
                    if (step.next != null && step.next.type == 'branch-on-boolean')
                    {
                        node.name = this.getDisplayAtom(step.atom) + "?"; 
                        step = step.next;
                    }
                    else
                        node.name = this.getDisplayAtom(step.atom);
                    break;
                case 'assert-atom':
                    node.name = "=> " + this.getDisplayAtom(step.atom);
                    break;
                case 'prompt-user':
    /*                if (step.next != null && step.next.type == 'branch-on-boolean')
                    {
                        node.name = step.property.split('#')[1] + '<-'; 
                        step.next = step.next.onTrue;
                    }
                    else */              
                        node.name = step.property.split('#')[1] + '<-';
                    break;
                case 'end':
                    node.name = step.outcome ? 'Success' : 'Failure';
                    break;
            };        
            var self = this;
            $.map([step.next, step.onFalse, step.onTrue], function (n) {
                    if (n) {
                        var adj = { nodeFrom:node.id, nodeTo: self.followFlow(n, nodes).id, data:{} };
                        if (n === step.onFalse) adj.data.labeltext = 'No';
                        else if (n === step.onTrue) adj.data.labeltext = 'Yes';
                        adj.data.$direction = [node.id, adj.nodeTo];
                        node.adjacencies.push(adj);
                    }
            });
            return node;
        };
        
        this.getGraphData = function() {
            if (this.graphData)
                return this.graphData;
            // First collect all nodes in an map, so we don't duplicate when
            // constructing the array
            var nodes = {};
            this.followFlow(this.data.start, nodes);
            var asarray = [];
            $.each(nodes, function(key, value) { asarray.push(value); });
            this.graphData = asarray;
            return this.graphData;
        };
        
        this.show = function(parentId) {
            var params = {
                    injectInto: parentId,
                    Navigation: {
                        enable: true,
                        panning: 'avoid nodes',
                        zooming: 10
                    },
                    Node: {
                        overridable: true,
                        autoWidth: true,
                        autoHeight: true
                    },
                    Edge: {
                        overridable: true,
                        type:'labeledEdge',
                        lineWidth: 0.4
                    },
                    Label: {
                        type:'Native',
                        color:'black',
                        size: 10,
                        style: 'bold'
                    },
                    iterations: 200,
                    levelDistance: 130,
                    onCreateLabel: function(domElement, node) {
                        domElement.innerHTML = node.name;
                        var style = domElement.style;
                        style.fontSize = "0.8em";
                        style.color = "black";
                    },
                    // Add node events
                    Events: {
                      enable: true,
                      //Change cursor style when hovering a node
                      onMouseEnter: function() {
                        fd.canvas.getElement().style.cursor = 'move';
                      },
                      onMouseLeave: function() {
                        fd.canvas.getElement().style.cursor = '';
                      },
                      //Update node positions when dragged
                      onDragMove: function(node, eventInfo, e) {
                          var pos = eventInfo.getPos();
                          node.pos.setc(pos.x, pos.y);
                          fd.plot();
                      },
                      //Implement the same handler for touchscreens
                      onTouchMove: function(node, eventInfo, e) {
                        $jit.util.event.stop(e); //stop default touchmove event
                        this.onDragMove(node, eventInfo, e);
                      },
                      //Add also a click handler to nodes
                      onClick: function(node) {
                        if(!node) return;
                        // Build the right column relations list.
                        // This is done by traversing the clicked node connections.
                        var html = "<h4>" + node.name + "</h4><b> connections:</b><ul><li>",
                            list = [];
                        node.eachAdjacency(function(adj){
                          list.push(adj.nodeTo.name);
                        });
                        //append connections information
                        $jit.id('inner-details').innerHTML = html + list.join("</li><li>") + "</li></ul>";
                      }
                    }                
                };
            var fd = new $jit.ForceDirected(params);
            var actualData = this.getGraphData();
    //        console.log('data:');
    //        console.log(actualData);
            fd.loadJSON(actualData);
    /*        fd.computeIncremental({
                    iter: 40,
                    property: 'end',
                    onComplete: function(){
                        console.log('done');
                        fd.animate({
                                modes: ['linear'],
                                transition: $jit.Trans.Elastic.easeOut,
                                duration: 2500
                        });
                    }
            }); */
            fd.refresh({iter:500, property:'end'});
            return fd;        
        }
        
        return this;
    }

    
    $jit.ForceDirected.Plot.EdgeTypes.implement({  
      'labeledEdge': {  
          'render':  function(adj, canvas) {  
            //plot arrow edge 
           this.edgeTypes.arrow.render.call(this, adj, canvas); 
            //get nodes cartesian coordinates 
            var pos = adj.nodeFrom.pos.getc(true); 
            var posChild = adj.nodeTo.pos.getc(true);
            //check for edge label in data 
            var data = adj.data; 
            if(data.labeltext) 
            {            
    //            var radius = this.viz.canvas.getSize(); 
                var x = parseInt((pos.x + posChild.x - (data.labeltext.length * 5)) / 2); 
                var y = parseInt((pos.y + posChild.y ) /2); 
                this.viz.canvas.getCtx().font = "color:black;";
                this.viz.canvas.getCtx().fillText(data.labeltext, x, y); 
            }
          },    
        'contains': function(adj, pos) {  
            return this.edgeTypes.arrow.contains.call(this, adj, pos);
        }
      }
      
    });

    $jit.ForceDirected.Plot.NodeTypes.implement({  
      'boxNode': {  
          'render':  function(node, canvas) {  
            //plot arrow edge 
           //this.nodeTypes.rectangle.render.call(this, node, canvas);
            var pos = node.pos.getc(true), 
                width = node.getData('width'), 
                height = node.getData('height');
            this.nodeHelper.rectangle.render('stroke', pos, width, height, canvas);
           //this.nodeTypes.rectangle.render.call(this, node, canvas);
          },    
        'contains': function(adj, pos) {  
            return this.nodeTypes.rectangle.contains.call(this, adj, pos);
        }
      }   
    });
    
    return {
        Workflow : Workflow
    };
    
});
