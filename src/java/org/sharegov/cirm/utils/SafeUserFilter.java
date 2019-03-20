/*******************************************************************************
 * Copyright 2015 Miami-Dade County
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 ******************************************************************************/
package org.sharegov.cirm.utils;

import mjson.Json;

/**
 * Safe User Filter multiple providers and profile json (Temporary solution).
 */
public class SafeUserFilter {

    private static final String[] ENET_PROPERTIES =
            new String[] {"givenName", "sn", "mdcDepartment", "mdcDivision", "mdcLocation"};
    private static final String[] BLUEBOOK_PROPERTIES = new String[] {"WK_email", "Fname", "Lname"};
    private static final String[] INTRANET_PROPERTIES =
            new String[] {"telephoneNumber", "department", "streetAddress", "mail"};

    private static SafeUserFilter instance;

    public static synchronized SafeUserFilter instance() {
        if (instance == null) {
            instance = new SafeUserFilter();
        }
        return instance;
    }

    private SafeUserFilter() {}

    public synchronized Json makeSafe(Json user) {
        if (user == null)
            return user;
        if (!user.isObject())
            return user;
        // pwd filter root obj enet negative (needed for profile format)
        if (user.has("hasPassword")) {
            user.delAt("hasPassword");
        }
        if (user.has("mdc4ssn")) {
            user.delAt("hasPassword");
        }
        if (user.has("mdcTerminationDate")) {
            user.delAt("mdcTerminationDate");
        }
        if (user.has("mdcBirthDate")) {
            user.delAt("mdcBirthDate");
        }
        if (user.has("mdcEmployeeStatus")) {
            user.delAt("mdcEmployeeStatus");
        }
        if (user.has("objectclass")) {
            user.delAt("objectclass");
        }
        if (user.has("mdcHireDate")) {
            user.delAt("mdcHireDate");
        }
        // User Provider filters
        if (user.has("enet")) {
            user.set("enet", getFiltered(user.at("enet"), ENET_PROPERTIES));
        }
        if (user.has("bluebook")) {
            user.set("bluebook", getFiltered(user.at("bluebook"), BLUEBOOK_PROPERTIES));
        }
        if (user.has("intranet")) {
            user.set("intranet", getFiltered(user.at("intranet"), INTRANET_PROPERTIES));
        }
        //Onto is controlled by us and safe
        return user;
    }

    private Json getFiltered(Json providerResult, String[] safePropertyNames) {
        if (providerResult == null || !providerResult.isObject()) {
            return providerResult;
        }
        Json result = Json.object();
        for (int i = 0; i < safePropertyNames.length; i++) {
            if (providerResult.has(safePropertyNames[i])) {
                result.set(safePropertyNames[i], providerResult.at(safePropertyNames[i]));
            }
        }
        return result;
    }
}
