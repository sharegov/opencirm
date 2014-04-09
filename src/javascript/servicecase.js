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
define([ 'rest', 'U', 'store!' ],function(rest, U, store) {
      function serviceCaseType(srObj) {
        $.extend(this, srObj);
        return this;
      }
      /** This method is construct a blueprint of new and existing SR Types.
       * @method createBluePrintSR
       * @return {Object} blue(blueprint object).
       */      
      serviceCaseType.prototype.createBluePrintSR = function() {
        var type = this;
        var blue = {
          boid : '',
          type : 'legacy:' + type.hasLegacyCode,
          properties : {}
        };
        var P = blue.properties;
        P.atAddress = {
          Street_Address_City : {
            iri : '',
            label : ''
          },
          Street_Address_State : {
            iri : "http://www.miamidade.gov/ontology#Florida",
            "label" : "FL"
          },
          Street_Direction : {
            iri : "",
            label : ""
          },
          hasStreetType : {
            iri : "",
            label : ""
          },
          Street_Name : '',
          Street_Number : '',
          Street_Unit_Number : '',
          Zip_Code : '',
          type : "Street_Address",
          fullAddress : "",
          folio : ""
        };
        P.hasStatus = type.hasDefaultStatus ? U.clone(type.hasDefaultStatus)
            : {};
        P.hasPriority = type.hasDefaultPriority ? U
            .clone(type.hasDefaultPriority) : {};
        P.hasIntakeMethod = type.hasDefaultIntakeMethod ? U
            .clone(type.hasDefaultIntakeMethod) : {};
        P.hasServiceAnswer = [];
        type.hasServiceField = U.ensureArray(type.hasServiceField);
        $.each(type.hasServiceField, function(i, f) {
         var a = {
            hasServiceField : {
              iri : f.iri,
              label : f.label
            }
          };
          if (f.hasDataType == 'CHARLIST' || f.hasDataType == 'CHARMULT'
              || f.hasDataType == 'CHAROPT')
            a.hasAnswerObject = {
              "iri" : undefined
            };
          else
            a.hasAnswerValue = {
              literal : '',
              type : ''
            };
          if (f.hasOrderBy)
            a.hasOrderBy = f.hasOrderBy;
          if (f.hasDataType)
            a.hasDataType = f.hasDataType;
          if (f.hasBusinessCodes)
            a.hasBusinessCodes = f.hasBusinessCodes;
          if (f.hasAllowableModules)
            a.hasAllowableModules = f.hasAllowableModules;
          P.hasServiceAnswer.push(a);
        });
        P.hasServiceAnswer.sort(function(x, y) {
          var l = x.hasOrderBy ? parseFloat(x.hasOrderBy) : 0;
          var r = y.hasOrderBy ? parseFloat(y.hasOrderBy) : 0;
          return l - r;
        });
        P.hasServiceActivity = [];
        P.hasServiceCaseActor = [];
        type.hasAutoServiceActor = U.ensureArray(type.hasAutoServiceActor);
        $.each(type.hasAutoServiceActor, function(i, a) {
          a = $.grep(type.hasServiceActor, function(x) {
            return x.iri == a.iri;
          })[0];
          P.hasServiceCaseActor.push({
            hasServiceActor : {
              iri : a.iri,
              label : a.label
            }
          });
        });
        P.hasImage = [];
        P.hasRemovedImage = [];
        // P.Comments = '';
        P.hasDetails = '';
        P.hasXCoordinate = "";
        P.hasYCoordinate = "";
        if (type.hasDurationDays) {
          var currd = new Date();
          currd.setTime(currd.getTime()
              + Math.round(parseFloat(type.hasDurationDays) * 1000 * 60 * 60
                  * 24));
          P.hasDueDate = currd.format();
        }
        console.log('created blueprint', blue);
        return blue;
      }
      /** This method is used start a new search for service request type and checks if it is enabled or disabled.
       * @method startNewServiceRequest
       * @param {Object} inputData
       * @return {Object} result. The blueprint object along with the status and error message (if any).
       */
      serviceCaseType.prototype.startNewServiceRequest = function(inputData) {
        var blueprint={};
        var status = false;
        var errorMessage = "";
        var callback = function cb() {
          var serviceCaseTyp = cirm.refs.serviceCases['http://www.miamidade.gov/cirm/legacy#'
              + inputData.type];
          serviceCaseTyp = new serviceCaseType(serviceCaseTyp);
          blueprint = serviceCaseTyp.createBluePrintSR();
          var currentAddress = ko.toJS(inputData.atAddress);
          blueprint.properties.atAddress = currentAddress;
          if (inputData.hasXCoordinate != "")
            blueprint.properties.hasXCoordinate = inputData.hasXCoordinate;
          if (inputData.hasYCoordinate != "")
            blueprint.properties.hasYCoordinate = inputData.hasYCoordinate;
        };
        if (cirm.refs.serviceCases['http://www.miamidade.gov/cirm/legacy#'
            + inputData.type].isDisabledCreate == 'true') {
          var srTypeName = inputData.srTypeName;
          alertDialog("The Creation of a new '" + srTypeName
              + "' Service Request is currently disabled for all users");
        } else if (inputData.hasXCoordinate != ""
            && inputData.hasYCoordinate != "") {
          var validatedResult = validateTypeOnXY(type, x, y);
          if (validatedResult == true) {
            callback();
          } else {
            status = false;
            errorMessage = "Service Request Type is not valid for this location. Please try a different location.";
          }
        } else {
          callback();
        }
       return {
          ok : status,
          error : errorMessage,
          data : blueprint
        }
      };
      
      /** This method is used to validate if a SR Type is available for a given x and y co-ordinates.
       * @method validateTypeOnXY
       * @param {String} type
       * @param {String} x
       * @param {String} y
       * @return {Bool} true
       */
      serviceCaseType.prototype.validateTypeOnXY = function(type, x, y) {
        return (cirm.top.get("/legacy/validate?type=" + type + "&x=" + x
            + "&y=" + y).isAvailable);
      }

     /** This method is used to look up a case from the server based on one of 
      * the following 1) boid 2) Case Number. It first searches by the case number and then searched for the boid in case there is no match.
      * @method lookUpCase
      * @param {String} lookup_boid
      * @return {Object} result. Result consists of the status, error message (if any) and an object of SR Type.
      */
      serviceCaseType.prototype.lookUpCase = function(lookup_boid) {
        var status = false;
        var errorMessage = "";
        var result = {};
        if (lookup_boid.indexOf('-') == -1) {
          var yr = U.getServerDateTime().getFullYear().toString();
          lookup_boid = yr.substr(yr.length - 2) + '-1'
              + U.addLeadingZeroes(lookup_boid, 7);
        }
        var caseNumberPattern = /[0-9]{2}.{1}[\d]{8}/;
        if (lookup_boid.match(caseNumberPattern)) {
            var query = {
            "type" : "legacy:ServiceCase",
            "legacy:hasCaseNumber" : lookup_boid
          };
          result = cirm.top.postObject("/legacy/caseNumberSearch", query);
          if (result.ok == true) {
            status = true;
            error = "";
            returnObj = result.bo;
            } else if (result.ok == false) {
            boid = parseInt(lookup_boid.split("-")[1].substr(1))
            result = cirm.top.get("/legacy/search?id=" + boid);
            if (result.ok == true) {
              status = true;
              error = "";
              returnObj = result.bo;
            } else if (result.ok == false) {
              status = false;
              errorMessage = result.error;
              returnObj = result.bo;
            }
          }
        } else {
          status = false;
          errorMessage = "Unrecognizable Format";
          returnObj = {};
        }
        return {
          ok : status,
          error : errorMessage,
          bo : returnObj
        };
      };
      /**
      * UI will call this function if the input field hasn't been changed by the user since the last reload
      * @method refreshUI
      * @param TBD
      * @return TBD
      */
      serviceCaseType.prototype.refreshUI = function() {
       
      }

      return {
        serviceCaseType : serviceCaseType
      };
    });
