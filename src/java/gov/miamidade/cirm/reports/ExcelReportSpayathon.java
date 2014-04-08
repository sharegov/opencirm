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
package gov.miamidade.cirm.reports;

import static org.sharegov.cirm.rest.OperationService.getPersister;
import static org.sharegov.cirm.utils.ServiceRequestReportUtil.formatDate;

import java.io.File;
import java.io.FileOutputStream;
import java.util.Calendar;
import java.util.Date;
import java.util.Set;
import java.util.regex.Pattern;

import mjson.Json;

import org.apache.poi.hssf.usermodel.HSSFCell;
import org.apache.poi.hssf.usermodel.HSSFCellStyle;
import org.apache.poi.hssf.usermodel.HSSFFont;
import org.apache.poi.hssf.usermodel.HSSFRow;
import org.apache.poi.hssf.usermodel.HSSFSheet;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.semanticweb.owlapi.model.OWLIndividual;
import org.semanticweb.owlapi.model.OWLLiteral;
import org.sharegov.cirm.OWL;
import org.sharegov.cirm.utils.GenUtils;
import org.sharegov.cirm.Refs;
import org.sharegov.cirm.rdb.Query;
import org.sharegov.cirm.rdb.QueryTranslator;
import org.sharegov.cirm.rdb.RelationalStore;
import org.sharegov.cirm.rest.LegacyEmulator;

public class ExcelReportSpayathon
{
	/**
	 * This report is specific to ASD Spayathon Service Requests.
	 * All Service Questions need to be displayed in columns.
	 */
	
	private HSSFSheet sheet;
	private HSSFWorkbook workbook;
	private HSSFRow row;
	private HSSFCell cell;
	private int rowCounter = 0;
	private int cellCounter = 0;
	
	public void addNewCell(String columnValue, HSSFCellStyle cellStyle)
	{
	    cell = row.createCell(cellCounter++);
	    cell.setCellValue(columnValue);
	    if(cellStyle != null)
	    	cell.setCellStyle(cellStyle);
	}

	public void addHeaderRow()
	{
	    HSSFFont boldFont = workbook.createFont();
	    boldFont.setBoldweight(HSSFFont.BOLDWEIGHT_BOLD);
	    HSSFCellStyle boldStyle = workbook.createCellStyle();
	    boldStyle.setFont(boldFont);
	    boldStyle.setWrapText(true);

	    row = sheet.createRow(rowCounter++);

	    Json columns = Json.object()
	    		.set("0", "SR#")
	    		.set("1", "ADDRESS")
	    		.set("2", "UNIT")
	    		.set("3", "CITY")
	    		.set("4","STATE")
	    		.set("5","ZIP")
	    		.set("6", "CREATED_DATE")
	    		.set("7", "HOUR")
	    		.set("8", "APPT_DATE")
	    		.set("9", "LAST_NAME")
	    		.set("10", "FIRST_NAME")
	    		.set("11", "LANGUAGE")
	    		.set("12", "PHONE#")
	    		.set("13", "ALT_CBR#")
	    		.set("14", "ANIMAL")
	    		.set("15", "GENDER")
	    		.set("16", "AGE")
	    		.set("17", "WEIGHT")
	    		.set("18", "FERAL")
	    		.set("19", "NEEDS_RABIES_VACCINE")
	    		.set("20", "EMAIL");
	    
	    for(int i=0; i<=20; i++)
	    	addNewCell(columns.at(Integer.toString(i)).asString(), boldStyle);
	}

	public void addRow(Json data)
    {
		row = sheet.createRow(rowCounter++);
		Json rowData = Json.object();
		
		Json prop = data.at("properties");
		rowData.set("0", prop.at("hasCaseNumber").asString());
		if(prop.has("atAddress"))
		{
			OWLIndividual ind = null;
			Json addr = prop.at("atAddress");
			rowData.set("1", addr.at("fullAddress").asString());
			rowData.set("2", addr.has("Street_Unit_Number") ?
					addr.at("Street_Unit_Number").asString() : "");
			if(addr.has("Street_Address_City"))
			{
				if(addr.at("Street_Address_City").isString())
					ind = OWL.individual(addr.at("Street_Address_City").asString());
				else if(addr.at("Street_Address_City").isObject())
					ind = OWL.individual(addr.at("Street_Address_City").at("iri").asString());
				if(ind != null)
				{
					Set<OWLLiteral> dpSet = ind.getDataPropertyValues(OWL.dataProperty("Name"), Refs.topOntology.resolve());
					if(dpSet.isEmpty())
						dpSet = ind.getDataPropertyValues(OWL.dataProperty("Alias"), Refs.topOntology.resolve());
					if(!dpSet.isEmpty())
						rowData.set("3", dpSet.iterator().next().getLiteral());
					else
						rowData.set("3", "");
				}
			}
			else
				rowData.set("3", "");
			if(addr.has("Street_Address_State"))
			{
				if(addr.at("Street_Address_State").isString())
					ind = OWL.individual(addr.at("Street_Address_State").asString());
				else if(addr.at("Street_Address_State").isObject())
					ind = OWL.individual(addr.at("Street_Address_State").at("iri").asString());
				if(ind != null)
				{
					Set<OWLLiteral> dpSet = ind.getDataPropertyValues(
							OWL.dataProperty("USPS_Abbreviation"), Refs.topOntology.resolve());
					if(!dpSet.isEmpty())
						rowData.set("4", dpSet.iterator().next().getLiteral());
					else
						rowData.set("4", "");
				}
			}
			else
				rowData.set("4", "");
			rowData.set("5", addr.has("Zip_Code") ? 
					addr.at("Zip_Code").asString() : "");
		}
		
		Calendar cal = Calendar.getInstance();
		cal.setTime(GenUtils.parseDate(prop.at("hasDateCreated").asString()));
		rowData.set("6", cal.get(Calendar.MONTH)+1 + "/" + 
				 cal.get(Calendar.DATE) + "/" + 
				 cal.get(Calendar.YEAR));

		rowData.set("7", 
				String.format("%02d", cal.get(Calendar.HOUR_OF_DAY))
				+ ":" +
				String.format("%02d", cal.get(Calendar.MINUTE)));

		for(Json answer : prop.at("hasServiceAnswer").asJsonList())
		{
			String question = OWL.fullIri(answer.at("hasServiceField").at("iri").asString()).getFragment();
			if(question.equals("ASSPTHN_DATEOFEV"))
				addRowDataEntry(rowData, answer, "8");
			if(question.equals("ASSPTHN_SPAQ1"))
				addRowDataEntry(rowData, answer, "9");
			if(question.equals("ASSPTHN_SPAQ2"))
				addRowDataEntry(rowData, answer, "10");
			if(question.equals("ASSPTHN_SPALANG"))
				addRowDataEntry(rowData, answer, "11");
			if(question.equals("ASSPTHN_SPAQ4"))
				addRowDataEntry(rowData, answer, "12");
			if(question.equals("ASSPTHN_SPAQ5"))
				addRowDataEntry(rowData, answer, "13");
			if(question.equals("ASSPTHN_SPAQ13"))
				addRowDataEntry(rowData, answer, "14");
			if(question.equals("ASSPTHN_SPAQ7"))
				addRowDataEntry(rowData, answer, "15");
			if(question.equals("ASSPTHN_SPAQ8"))
				addRowDataEntry(rowData, answer, "16");
			if(question.equals("ASSPTHN_SPAQ9"))
				addRowDataEntry(rowData, answer, "17");
			if(question.equals("ASSPTHN_SPAQ11"))
				addRowDataEntry(rowData, answer, "18");
			if(question.equals("ASSPTHN_SPAQ12"))
				addRowDataEntry(rowData, answer, "19");
			if(question.equals("ASSPTHN_SPAQ6"))
				addRowDataEntry(rowData, answer, "20");
		}
		for(int i=0; i<=20; i++)
		{
			addNewCell(rowData.has(Integer.toString(i)) ? 
					rowData.at(Integer.toString(i)).asString(): "", 
					null);
    	}
    }
	
	public void addRowDataEntry(Json rowData, Json answer, String elementNo)
	{
		if(answer.has("hasAnswerValue"))
		{
			rowData.set(elementNo, 
					answer.at("hasAnswerValue").at("literal").asString());
		}
		else
		{
			rowData.set(elementNo, 
					answer.at("hasAnswerObject").at("label").asString());
		}
	}

	public HSSFWorkbook addContent(Set<Long> boids)
	{
		workbook = new HSSFWorkbook();
		sheet = workbook.createSheet();
		//HSSFHeader header = sheet.getHeader();
		//header.setCenter(filename);

	    addHeaderRow();
    	LegacyEmulator le = new LegacyEmulator();
    	for(Long boid : boids)
    	{
			Json data = le.findServiceCaseOntology(boid).toJSON();
			cellCounter = 0;
	    	addRow(data);
    	}
		return workbook;
	}

    public void setColumnWidth(String columnValue, int columnNo)
    {
	    int width = columnValue.length() * 325;
	    if (width > sheet.getColumnWidth(columnNo))
	    {
	    	sheet.setColumnWidth(columnNo, width);
	    }
    }

    // Determine the width of the column head
    public void setHeaderColumnStyle(HSSFRow row, 
    		HSSFCell cell, 
    		HSSFCellStyle boldStyle, 
    		String columnValue, 
    		int columnNo)
    {
        setColumnWidth(columnValue, columnNo);
		// Calculate what the column width should be.
	    // Increase if the current width is smaller than the calculated width.
	    int width = columnValue.length() * 325;
    	String[] splitHead = Pattern.compile(" ").split(columnValue);
		int wordCnt = splitHead.length;
		for (int q = 0; q < splitHead.length; q++)
		{
		    if (splitHead[q].length() * 325 > width)
		    	width = splitHead[q].length() * 325;
		    sheet.setColumnWidth(columnNo, width);
		}
		// Dertermin the height of the column head
		int height = wordCnt * 275;
		if (row.getHeight() < height)
		    row.setHeight((short) height);
		// Set Cell to boldStyle
		cell.setCellStyle(boldStyle);

    }

    /**
     * To generate ASD Spayathon report, 
     * put the required search criteria in the pattern object 
     * and run the main method
     * @return
     */
    public Json searchCriteria()
    {
    	Json pattern = Json.object();
    	pattern.set("caseSensitive", false);
    	pattern.set("currentPage", 1);
    	pattern.set("itemsPerPage", 1000);
    	pattern.set("sortBy","boid");
    	pattern.set("sortDirection","desc");
    	//Do not change the type of Service Request. As the report is specific to this type.
    	pattern.set("type","legacy:ASSPTHN");
    	pattern.set("http://www.miamidade.gov/cirm/legacy#ASSPTHN_SPAQ13", 
    			"http://www.miamidade.gov/cirm/legacy#ASSPTHN_SPAQ13_ASDSPN_DOGTYPE");
    	pattern.set("hasDateCreated", ">=2013-04-24T00:00:00.000");
    	return pattern;
    }
	
    public static void main(String args[])
    {
    	try {
			ExcelReportSpayathon spayathon = new ExcelReportSpayathon();
    		Json pattern = spayathon.searchCriteria();
    		QueryTranslator qt = new QueryTranslator();
			RelationalStore store = getPersister().getStore();
			Query q = qt.translate(pattern, store);
			Set<Long> boids = store.query(q, 
					Refs.tempOntoManager.resolve().getOWLDataFactory());

    		if(boids.size() > 0)
   			{
	        	HSSFWorkbook wb = spayathon.addContent(boids);
	        	String fileName = "C:\\ASD_Spayathon_"+formatDate(new Date())+".xls";
	        	File f = new File(fileName);
	        	FileOutputStream out = new FileOutputStream(f);
	    		wb.write(out);
	    		out.close();
	    		System.out.println("Successfully created the ASD Spayathon report.");
			}
    		else
    			System.out.println("No ASD Spayathon SRs present for given search criteria.");
    	}
    	catch(Exception e)
    	{
    		e.printStackTrace();
			throw new RuntimeException(e);
    	}
    }

}
