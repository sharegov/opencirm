package gov.miamidade.cirm.other;

import static org.junit.Assert.*;

import java.net.URL;
import java.util.Date;

import org.junit.Test;
import org.sharegov.cirm.utils.GenUtils;

import mjson.Json;

public class Md2089UpdateTxnAnalysis {

	@Test
	public void testUpdateExistingCase() {
		Json config = Json.object("trace",true);
		LegacyJMSListener l = new LegacyJMSListener(config, System.out);
		URL sr = this.getClass().getResource("Md2089Existing.json");
		URL update = this.getClass().getResource("Md2089PWUpdate.json");
		String existingStr = GenUtils.readAsStringUTF8(sr);
		String updateStr = GenUtils.readAsStringUTF8(update);
		
		Json existing = Json.read(existingStr);
		Json newdata = Json.read(updateStr);		
		l.updateExistingCase(existing, newdata, new Date(0));
		System.out.println(existing);
	}

}
