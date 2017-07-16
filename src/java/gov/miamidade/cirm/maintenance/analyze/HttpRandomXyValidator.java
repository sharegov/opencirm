package gov.miamidade.cirm.maintenance.analyze;

import org.sharegov.cirm.StartupUtils;
import org.sharegov.cirm.utils.GenUtils;
import org.sharegov.cirm.utils.ThreadLocalStopwatch;

import mjson.Json;

/**
 * HttpRandomXyValidator
 *
 * @author Thomas Hilpold
 *
 */
public class HttpRandomXyValidator {

	public double topLeftX = 862500; // Rectangle spans wide populated area, includes airport rougly in center.
	public double bottomRightX = 903000;
	public double topLeftY = 555230;
	public double bottomRightY = 505000;
	
	public String srType = "BULKYTRA";
	
	public final static String endpoint = "https://127.0.0.1:8183/legacy/validate?"; //type=BULKYTRA&x=827830.292&y=413683.609&_=1500173507639"
	
	/**
	 * 
	 */
	public HttpRandomXyValidator() {
		StartupUtils.disableCertificateValidation();
	}
	
	public void run(int nrOfCalls) {
		for (int i = 0; i < nrOfCalls; i++) {
			double[] xy = nextRandomXY();
			ThreadLocalStopwatch.startTop("" + xy[0] + ", " + xy[1]);
			Json result = callValidateEndpoint(xy[0], xy[1]);
			ThreadLocalStopwatch.now(i + "/" + nrOfCalls + " " + result);
		}
	}
	
	double[] nextRandomXY() {
		double x = topLeftX + (int)(Math.random() * (bottomRightX - topLeftX));
		double y = bottomRightY + (int)(Math.random() * (topLeftY - bottomRightY));
		return new double[] { x, y };
	}
	
	Json callValidateEndpoint(double x, double y) {
		return GenUtils.httpGetJson(endpoint + "type=" + srType + "&x=" + x + "&y=" + y);
	}

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		HttpRandomXyValidator v = new HttpRandomXyValidator();
		v.run(1);
	}

}
