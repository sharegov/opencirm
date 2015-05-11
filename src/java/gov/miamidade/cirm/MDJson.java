package gov.miamidade.cirm;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

import mjson.Json;

/**
 * Utils to simplify 
 * @author Thomas Hilpold
 *
 */
public class MDJson {

	private Json json;
    /**
     * Read message into Json.
     *  
     * {talendTypes} String
     * 
     * {Category} User Defined
     * 
     * {param} string("world") input: The string need to be printed.
     * 
     * {example} helloExemple("world") # hello world !.
     */
    public boolean read(String jsonAsString) {
    	try {
    		json = Json.read(jsonAsString);
    		return true;
    	} catch (Exception e) {
    		System.err.println(e);
    		e.printStackTrace();
    		return false;
    	}
    }

    public MDJson(String jStr) {
    	if (!read(jStr)) throw new IllegalArgumentException("Param not valid json");
    }

    public MDJson(Json j) {
    	this.json = j;
    }
   
    /**
     * Read message into Json.
     *  
     * {talendTypes} Json
     * 
     * {Category} User Defined
     * 
     * {param} string("world") input: The string need to be printed.
     * 
     * {example} helloExemple("world") # hello world !.
     */

    public Json readAt(String dottedPath) {
    	String [] paths = dottedPath.split("\\.");
    	Json cur = json;
    	for (int i = 0; i < paths.length; i++) {
    		if (cur.has(paths[i])) {
    			cur = cur.at(paths[i]);
    		} else {
    			return null;
    		}
    	}
    	return cur;    	
    }
    /**
     * 
     * @param dottedPath
     * @return string or label or iri fragment or iri (no fragment)
     */
    public String readAtStr(String dottedPath) {
    	Json r = readAt(dottedPath);
    	try {
        	if (r.isObject()) {
        		if (r.has("label")) {
            		return r.at("label").asString();
        		} else if (r.has("iri")) {    		
        			String iri = r.at("iri").asString();
        			int poundindex = iri.indexOf("#");
        			if (poundindex >= 0) {
        				return iri.substring(poundindex);
        			} else {
        				return iri;
        			}
        		} else {
        			//give up on object
        			return null;
        		}
        	} else {
        		return r.asString();
        	}
    	} catch (Exception e) {
    		return null;
    	}
    }

    public Long readAtLong(String dottedPath) {
    	Json r = readAt(dottedPath);
    	try {
    		return r.asLong();
    	} catch (Exception e) {
    		return null;
    	}
    }

    public Float readAtFloat(String dottedPath) {
    	Json r = readAt(dottedPath);
    	try {
    		return r.asFloat();
    	} catch (Exception e) {
    		return null;
    	}
    }

    public Integer readAtInt(String dottedPath) {
    	Json r = readAt(dottedPath);
    	try {
    		return r.asInteger();
    	} catch (Exception e) {
    		return null;
    	}
    }

    public static final String isoDatePattern = "yyyy-MM-dd'T'HH:mm:ss.SSSZ";

    public Date readAtMDDate(String dottedPath) {
    	Json r = readAt(dottedPath);
    	try {
    		String dateISOStr = r.asString();
    		DateFormat f = new SimpleDateFormat(isoDatePattern);
    		return f.parse(dateISOStr);
    	} catch (Exception e) {
    		return null;
    	}
    }

}
