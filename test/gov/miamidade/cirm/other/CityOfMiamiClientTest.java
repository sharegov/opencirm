package gov.miamidade.cirm.other;


import org.junit.BeforeClass;
import org.junit.Test;
import org.sharegov.cirm.OWL;
import org.sharegov.cirm.StartupUtils;

import mjson.Json;
/**
 * Apply update test.
 * @author Thomas Hilpold
 *
 */
public class CityOfMiamiClientTest {

	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
		StartupUtils.disableCertificateValidation();
	}

	@Test
	public void test() {
		OWL.reasoner();
		CityOfMiamiClient c = new CityOfMiamiClient();
		Json test = Json.object();
		test.set("CaseNumber", "16-10343622");
		test.set("code1", "COMPRIUM");
		test.set("value1", "CAPO");
		Json response = c.applyUpdateFromCity(test);
		System.out.println(response);
	}
	
	@Test
	public void testToFail1() {
		OWL.reasoner();
		CityOfMiamiClient c = new CityOfMiamiClient();
		Json test = Json.object();
		test.set("CaseNumber", "16-10343622");
		test.set("code1", "COMPRIUM");
		test.set("value1", "CAPOXXXXXXXXXXXXXX");
		Json response = c.applyUpdateFromCity(test);
		System.out.println(response);
	}
	
	@Test
	public void testToFail2() {
		OWL.reasoner();
		CityOfMiamiClient c = new CityOfMiamiClient();
		Json test = Json.object();
		test.set("CaseNumber", "16-10343622");
		test.set("code1", "COMPRIUMXXXXXXXXXX");
		test.set("value1", "CAPO");
		Json response = c.applyUpdateFromCity(test);
		System.out.println(response);
	}

}
