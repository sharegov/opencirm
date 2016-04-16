package gov.miamidade.cirm.test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

import org.sharegov.cirm.StartupUtils;
import org.sharegov.cirm.utils.GenUtils;

import mjson.Json;

/**
 * Calls the load balanced mdcgis and each prod server mdcgis candidates endpoint with each streetaddress in GisCandidatesValidatorAddresses.csv.<br>
 * This is a standalone and long running test (>20 hours for 7000 addresses)<br>
 * Each mdcgis response is evaluated for addresstype, multiples and SufType in the parsedAddress.
 * 
 * @author Thomas Hilpold
 *
 */
public class GisCandidatesValidator {

	public static final String BAD_ADDRESSES_FILENAME = "GisCandidatesValidatorAddresses.csv";
	
	public final static String endpoint[] = {
			"https://311hub.311.miamidade.gov:9196/mdc/mdcgis-0.1/candidates?",
			"https://s2030050.311.miamidade.gov:9196/mdc/mdcgis-0.1/candidates?",
			"https://s2030051.311.miamidade.gov:9196/mdc/mdcgis-0.1/candidates?",
			"https://s2030057.311.miamidade.gov:9196/mdc/mdcgis-0.1/candidates?",
			"https://s2030059.311.miamidade.gov:9196/mdc/mdcgis-0.1/candidates?",
			"https://s2030060.311.miamidade.gov:9196/mdc/mdcgis-0.1/candidates?"
	};
	
	private static List<String[]> badAddresses;
	
	public static void main(String[] arv) throws IOException {
		badAddresses = new ArrayList<>(1001);
		loadBadAdresses();
		StartupUtils.disableCertificateValidation();
		for (int j = 0; j < endpoint.length; j ++) {
			for (int k = 1; k < badAddresses.size(); k ++) {
				String query = "street=" + URLEncoder.encode(badAddresses.get(k)[5], "UTF-8");
				System.out.print(j + " / "+ k + " " + badAddresses.get(k)[5]);
				String endpointStr = endpoint[j] + query + "&cache_bust=" + System.currentTimeMillis();
				//System.out.println(endpointStr);
    			Json result = GenUtils.httpGetJson(endpointStr);
    			//System.out.println(result.toString());
    			if (result.at("candidates").asJsonList().isEmpty()) {
    				System.out.println(" > ZERO cands at "+ k + " endpoint " + endpointStr);
    				continue;
    			}
    			if (result.at("candidates").asJsonList().size() > 1) {
    				System.out.println(" > Multiple ( " + result.at("candidates").asJsonList().size() + ") cands at "+ k + " endpoint " + endpointStr);
    			}
    			result = result.at("candidates").asJsonList().get(0);
    			if (result.has("addressType")) {
    				System.out.println(" > " + result.at("addressType").asString());
    			} else {
    				System.out.println(" > No Address Type");
    			}
    			boolean ok = result.has("parsedAddress") && result.at("parsedAddress").has("SufType") 
    				&& result.at("parsedAddress").at("SufType").asString().length() >= 2;
    			if (ok) {
    				String st = result.at("parsedAddress").at("SufType").asString();
    				if (!st.trim().equals(st)) { 
    					System.out.println("Whitespace error");
    					ok = false;
    				}        				
    			}
    			if (!ok) System.out.println("Failed: " + k + " endpoint " + endpointStr);
			}
		}
	}

	private static void loadBadAdresses() throws IOException {
		InputStream is = GisCandidatesValidator.class.getResourceAsStream(BAD_ADDRESSES_FILENAME);
		BufferedReader r = new BufferedReader(new InputStreamReader(is, "UTF-8"));
		while (r.ready()) {
			String[] row = r.readLine().split("\t");
			if (row.length > 1) {
				badAddresses.add(row);
			}
		}
		System.out.println("GisCandidatesValidator Loaded " + (badAddresses.size() - 1) + " bad addresses");		
	}
}
