/*******************************************************************************
 * Copyright 2016 Miami-Dade County
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
package gov.miamidade.cirm.maintenance;

import gov.miamidade.cirm.GisClient;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.StringTokenizer;

import mjson.Json;

import org.sharegov.cirm.CirmTransaction;
import org.sharegov.cirm.OWL;
import org.sharegov.cirm.Refs;
import org.sharegov.cirm.StartupUtils;
import org.sharegov.cirm.gis.GisDAO;
import org.sharegov.cirm.utils.ThreadLocalStopwatch;

/**
 * Updates select columns only in GIS_INFO_TABLE rows specified by CSV file.
 *  
 * @author Thomas Hilpold
 *
 */
public class GisInfoTableCSVUpdate
{

	public final String INPUT_CSV_FILE = "GISINFO_UPDATE.csv";
	public final boolean HAS_HEADER_LINE = true; 
	public final  int MIN_COLUMN_COUNT = 4;
	public final String COLUMN_SEPARATOR = ",";
	public final String[] COLUMNS_TO_UPDATE = {"GIS_NET_AREA_NAME", "GIS_MIAMI_NEIGHBORHOOD", "GIS_PW_MAINT_ZONE", "GIS_FIRE_PREV_BUREAU" };
	
	
	/**
	 * Loads the file into memory.<br>
	 * Validates (basic) the column count and columns 1-3 for each row.
	 * 
	 * @return a list of rows as Strings without header
	 * @throws IllegalStateException on line validation error with cause.
	 * @throws IOException if file read issue.
	 */
	List<String> loadFile(URI fileUri) throws URISyntaxException, IOException {
		ArrayList<String> result = new ArrayList<>();
		@SuppressWarnings("resource")
		BufferedReader reader = new BufferedReader(new FileReader(new File(fileUri)));
		while (reader.ready()) {
			String line = reader.readLine();
			try {
				validateLine(line, HAS_HEADER_LINE && result.isEmpty());
			} catch (Exception e) {
				ThreadLocalStopwatch.error("Validation exception in row " + (result.size() + 1) + " File " + fileUri);
				throw new IllegalStateException("File Validation failed");
			}
			result.add(line);
		}
		reader.close();
		if (HAS_HEADER_LINE) result.remove(0);
		ThreadLocalStopwatch.now("Loaded lines: " + result.size());
		return result;
	};
	
	/**
	 * Basic validation of a line read from the file.
	 * 
	 * @param line from file
	 * @param isHeaderLine only token count is checked for header line.
	 * @throws ParseException if col2 update date parsing fails.
	 * @throws IllegalStateException if col contains whitespace or too few characters.
	 */
	protected void validateLine(String line, boolean isHeaderLine) throws ParseException, NumberFormatException {
		StringTokenizer lineTok = new StringTokenizer(line, COLUMN_SEPARATOR);
		if (lineTok.countTokens() < MIN_COLUMN_COUNT) throw new IllegalStateException("Less columns than required: " + line);
		if (!isHeaderLine) {
			String col1Str = lineTok.nextToken();
			try {
				Long.parseLong(col1Str);
			} catch (NumberFormatException e) {
				ThreadLocalStopwatch.error("Column 1 (GIS ID) is not an integer, was " + col1Str);
				throw e;
			}
			String col2Str = lineTok.nextToken();
			try {
				Long.parseLong(col2Str);
			} catch (NumberFormatException e) {
				ThreadLocalStopwatch.error("Column 2 (SR Case number id) is not an integer, was " + col2Str);
				throw e;
			}
			String col3Str = lineTok.nextToken();
			if (col3Str.length() < 6) {
				throw new IllegalStateException("Column 3 (Case type) < 6 characters: " + col3Str);
			}
		} // headerline		
	}
	
	public void processAll() throws Exception {
		List<String> lines = loadFile(this.getClass().getResource(INPUT_CSV_FILE).toURI());
		for (String line : lines) {
			processOneLine(line);
		}
	}
	private void processOneLine(String line) {
		StringTokenizer t = new StringTokenizer(line, COLUMN_SEPARATOR);
		long gisInfoId = Long.parseLong(t.nextToken());
		NAD83Point p = getXYFromGISClob(gisInfoId);
		Json newLocationInfo = null;
		if (p != null) {
			GisClient gisClient = new GisClient();
			Json locationInfo = gisClient.getLocationInfo(p.getX(), p.getY(), null);
			if (locationInfo != null & locationInfo.has("address")) {
				newLocationInfo = locationInfo;
			}
		}
		if (newLocationInfo != null) {
			update(gisInfoId, newLocationInfo, Arrays.asList(COLUMNS_TO_UPDATE), false);
			ThreadLocalStopwatch.now("SUCCESS: " + line);
		} else {
			ThreadLocalStopwatch.error("FAIL: " + line);
		}
	}
	
	private NAD83Point getXYFromGISClob(final long gisInfoId) {
		return Refs.defaultRelationalStore.resolve().txn(new CirmTransaction<NAD83Point>() {
			public NAD83Point call()
			{
				return getXYFromGISClobTransact(gisInfoId);
			}
		});			
	}
		
	private NAD83Point getXYFromGISClobTransact(long gisInfoId) {
		NAD83Point result = null;
		Connection conn = null;
		Statement stmt = null;
		ResultSet rs = null;
		try
		{
			
			conn = Refs.defaultRelationalStoreExt.resolve().getConnection();
			stmt = conn.createStatement();
			rs = stmt.executeQuery("select DATA from CIRM_GIS_INFO WHERE ID = " + gisInfoId);
			if (rs.next())
			{
				String s = rs.getString("DATA");
				//boolean directMapping = s.contains("\"MUNCODE\"");
				Json locationInfo = Json.read(s);
				if (locationInfo.has("address")) {
					Json address = locationInfo.at("address");
					if (address.has("location")) {
						Json location = address.at("location");
						if (location.has("x") && location.has("y")) {
    						double x = Double.parseDouble(location.at("x").asString());
    						double y = Double.parseDouble(location.at("y").asString());
    						result = new NAD83Point();
    						result.setX(x);
    						result.setY(y);
						} // xy
					} // loc
				} //address
			}
			conn.commit();			
			return result;
		}catch (Exception ex)
		{
			throw new RuntimeException(ex);
		}
		finally
		{
			if (rs != null)
				try { rs.close(); } catch (Throwable t) { } 
			if (stmt != null)
				try { stmt.close(); } catch (Throwable t) { }
			if (conn != null)
				try { conn.close(); } catch (Throwable t) { } 
		}
	}

	/**
	 * @param args
	 * @throws Exception 
	 */
	public static void main(String[] args) throws Exception
	{
		GisClient.DBGSQL =true;
		GisClient.DBG = false;
		GisClient.DBGX = false;
		StartupUtils.disableCertificateValidation();
		//Force init of ontologies early; this is slow; you may disable this for quick file parsing tests.
		OWL.reasoner();
		GisInfoTableCSVUpdate g = new GisInfoTableCSVUpdate();
		g.processAll();
	}
	
	public boolean update(final long dbId, final Json locationInfo, List<String> columns, final boolean directMapping)
	{
		return Refs.defaultRelationalStore.resolve().txn(new CirmTransaction<Boolean>() {
			public Boolean call()
			{
				GisDAO.updateNormalizedColumns(dbId, locationInfo, columns, directMapping);
				return true;
			}
		});
	}
	
	static class NAD83Point {
		
		private double x,y;

		public double getX() {
			return x;
		}

		public void setX(double x) {
			this.x = x;
		}

		public double getY() {
			return y;
		}

		public void setY(double y) {
			this.y = y;
		}
	}	
}
