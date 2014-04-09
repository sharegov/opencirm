/// <reference path="_references.js" />
/**
* Contains a number of useful utiity methods for javascript.
*
* File:         cog.utils.js
* Version:      0.1
* Author:       Lucas Martin
* License:      Creative Commons Attribution 3.0 Unported License. http://creativecommons.org/licenses/by/3.0/ 
* 
* Copyright 2011, All Rights Reserved, Cognitive Shift http://www.cogshift.com  
*/

// ** Global Utilities **
cog.utils = {}

cog.utils.intercept = function (fnToIntercept, fnToExecute) {
    /// <summary>
    /// Intercepts a function with another function.  The original function is passed to the new function
    /// as the last argument of it's parameter list, and must be executed within the new function for the interception
    /// to be complete.
    /// </summary>
    /// <param name="fnToIntercept" type="Function">
    ///     The old function to intercept.
    /// </param>
    /// <param name="fnToExecute" type="Function">
    ///     The new function to be executed.
    /// </param>
    /// <returns>
    ///     A proxy function that performs the interception.  Execute this function like you would execute the fnToExecute function.
    /// </returns>
    fnToIntercept = fnToIntercept || function () { }
    return function () {
        var newArguments = []
        $.each(arguments, function (i, item) { newArguments.push(item); });
        newArguments.push(fnToIntercept);
        return fnToExecute.apply(this, newArguments);
    }
}

// ** String Utilities **
cog.utils.string = {}
cog.utils.string.format = function () {
    /// <summary>
    /// Replaces tokens in a string with the supplied parameters.
    /// </summary>
    /// <param name="string" type="String">
    ///     The string to search for the tokens.
    /// </param>
    /// <param name="params" type="Arguments">
    ///     A parameter list of tokens to replace.
    /// </param>
    /// <returns>
    ///     The string with the tokens replaced.
    /// </returns>
    var s = arguments[0];
    for (var i = 0; i < arguments.length - 1; i++) {
        var reg = new RegExp("\\{" + i + "\\}", "gm");
        s = s.replace(reg, cog.utils.convert.toString(arguments[i + 1]));
    }

    return s;
};

cog.utils.string.endsWith = function (string, suffix) {
    /// <summary>
    /// Searches the end of a string for another string.
    /// </summary>
    /// <param name="string" type="String">
    ///     The string to check.
    /// </param>
    /// <param name="suffix" type="String">
    ///     The suffix to look for.
    /// </param>
    /// <returns>
    ///     Returns true if the string ends with the supplied suffix.
    /// </returns>
    return (string.substr(string.length - suffix.length) === suffix);
};

cog.utils.string.startsWith = function (string, prefix) {
    /// <summary>
    /// Searches the start of a string for another string.
    /// </summary>
    /// <param name="string" type="String">
    ///     The string to check.
    /// </param>
    /// <param name="prefix" type="String">
    ///     The prefix to look for.
    /// </param>
    /// <returns>
    ///     Returns true if the string ends with the supplied prefix.
    /// </returns>
    return (string.substr(0, prefix.length) === prefix);
};

cog.utils.string.trimEnd = function (string, chars) {
    /// <summary>
    /// Trims a list of characters from the end of a string.
    /// </summary>
    /// <param name="string" type="String">
    ///     The string to trim.
    /// </param>
    /// <param name="chars" type="String">
    ///     The characters to trim off the end of the string.
    /// </param>
    /// <returns>
    ///     The string with the characters trimmed off the end.
    /// </returns>
    if (cog.utils.string.endsWith(string, chars)) {
        return string.substring(0, string.length - chars.length);
    }

    return string;
};

cog.utils.string.trimStart = function (string, chars) {
    /// <summary>
    /// Trims a list of characters from the start of a string.
    /// </summary>
    /// <param name="string" type="String">
    ///     The string to trim.
    /// </param>
    /// <param name="chars" type="String">
    ///     The characters to trim off the start of the string.
    /// </param>
    /// <returns>
    ///     The string with the characters trimmed off the start.
    /// </returns>
    if (cog.utils.string.startsWith(string, chars)) {
        return string.substring(chars.length, string.length);
    }

    return string;
};

cog.utils.string.repeat = function (string, count) {
    /// <summary>
    /// Repeats a string the specified amount of times.
    /// </summary>
    /// <param name="string" type="String">
    ///     The string to repeat.
    /// </param>
    /// <param name="chars" type="String">
    ///     The number of times to repeat the string.
    /// </param>
    /// <returns>
    ///     The repeated string sequence.
    /// </returns>
    return new Array(count + 1).join(string);
}