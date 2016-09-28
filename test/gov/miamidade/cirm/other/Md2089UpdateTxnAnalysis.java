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
		//1 Load case and update message
		URL sr = this.getClass().getResource("Md2089Existing.json");
		URL update = this.getClass().getResource("Md2089PWUpdate.json");
		String existingStr = GenUtils.readAsStringUTF8(sr);
		String updateStr = GenUtils.readAsStringUTF8(update);	
		Json existing = Json.read(existingStr);
		Json newdata = Json.read(updateStr);		
		System.out.println("BEFORE UPDATE: \r\n" + existing);
		Json lockedStatusChangeOutcome = existing.at("properties").at("hasServiceActivity").at(1).at("hasOutcome").dup();
		Json statusOld = existing.at("properties").at("hasStatus").dup();
		//2 Ensure that a certain StatusChangedActivity from Open to Locked is locked prior to update 
		assertTrue("Original activitiy O-LOCKED", lockedStatusChangeOutcome.asString().contains("O-LOCKED"));
		//3 Apply update message to case json
		l.updateExistingCase(existing, newdata, new Date(0));
		//4 Assert that even though case status was changed from Locked to Closed, the O-LOCKED StatusChangeActivity remained untouched.
		//Before 2089 fix, the object containing O-LOCKED was shared and an update would modify all referrers and not just the case status.
		System.out.println("AFTER UPDATE: \r\n" + existing);
		Json lockedStatusChangeOutcomeAfterupdate = existing.at("properties").at("legacy:hasServiceActivity").at(1).at("legacy:hasOutcome");
		Json statusNew = existing.at("properties").at("legacy:hasStatus").dup();
		assertTrue("O-LOCKED activity must not be modified", lockedStatusChangeOutcomeAfterupdate.at("iri").equals(lockedStatusChangeOutcome));
		assertTrue("Status was changed", !statusNew.equals(statusOld));
		assertTrue("New Status Closed", statusNew.at("iri").asString().contains("C-CLOSED"));
	}
}
