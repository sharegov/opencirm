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
package org.sharegov.cirm.utils;

import static org.sharegov.cirm.OWL.dataProperty;
import static org.sharegov.cirm.OWL.fullIri;
import static org.sharegov.cirm.OWL.individual;
import static org.sharegov.cirm.OWL.getEntityLabel;
import static org.sharegov.cirm.OWL.objectProperty;
import static org.sharegov.cirm.OWL.ontology;
import static org.sharegov.cirm.utils.ServiceRequestReportUtil.formatDate;

import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map.Entry;
import java.util.regex.Pattern;

import mjson.Json;

import org.apache.poi.hssf.usermodel.HSSFCell;
import org.apache.poi.hssf.usermodel.HSSFCellStyle;
import org.apache.poi.hssf.usermodel.HSSFFont;
import org.apache.poi.hssf.usermodel.HSSFFooter;
import org.apache.poi.hssf.usermodel.HSSFHeader;
import org.apache.poi.hssf.usermodel.HSSFPrintSetup;
import org.apache.poi.hssf.usermodel.HSSFRow;
import org.apache.poi.hssf.usermodel.HSSFSheet;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.hssf.util.HSSFColor;
import org.apache.poi.ss.util.CellRangeAddress;
import org.semanticweb.owlapi.model.OWLOntology;

/**
 * An Excel spreadsheet is created using the poi library.
 * The excel sheet is populated with the Json data 
 */
public class ExcelExportUtil
{
    private HSSFSheet sheet; 

    public static final String basicSearchReportHeader = "Basic Search Results Report";
    public static final String totalResults = "Total Results";
    public static final String searchCriteriaHeader = "The Search Criteria selected to generate this report is";

    public Integer searchCriteriaRows1(Json searchCriteria, HSSFCellStyle boldCenterStyle, int totalRecords) {
	    int rowCounter = 0;
	    int cellCounter = 0;
	    //Create Headings and info about the report
	    HSSFRow row = sheet.createRow(rowCounter++);
	    HSSFCell cell = row.createCell(cellCounter++);
    	cell.setCellValue(basicSearchReportHeader);
    	cell.setCellStyle(boldCenterStyle);
    	sheet.addMergedRegion(new CellRangeAddress(row.getRowNum(), row.getRowNum(), 
    			cell.getColumnIndex(), cell.getColumnIndex() + 3));
	    row = sheet.createRow(rowCounter++);
	    cellCounter = 0;
	    cell = row.createCell(cellCounter++);
	    cell.setCellValue(searchCriteriaHeader);
    	sheet.addMergedRegion(new CellRangeAddress(row.getRowNum(), row.getRowNum(), 
    			cell.getColumnIndex(), cell.getColumnIndex()+2));

    	for(Entry<String, Json> prop : searchCriteria.asJsonMap().entrySet())
    	{
		    row = sheet.createRow(rowCounter++);
		    cellCounter = 0;
		    cell = row.createCell(cellCounter++);
		    cell.setCellValue(prop.getKey());
		    cell = row.createCell(cellCounter++);
		    cell.setCellValue(prop.getValue().asString());
    	}
	    
	    row = sheet.createRow(rowCounter++);
	    row = sheet.createRow(rowCounter++);
	    cellCounter = 0;
    	cell = row.createCell(cellCounter++);
    	cell.setCellValue(totalResults + " : " +totalRecords);
    	sheet.addMergedRegion(new CellRangeAddress(row.getRowNum(), row.getRowNum(), 
    			cell.getColumnIndex(), cell.getColumnIndex()+1));
	    row = sheet.createRow(rowCounter++);
    	return rowCounter;
    }

    public void exportData(OutputStream out, Json allData) throws IOException
    {
    	//Set the filename
    	Date dt = new Date();
		SimpleDateFormat fmt = new SimpleDateFormat("MM-dd-yyyy");
		String filename = fmt.format(dt);
    	
		// Create Excel Workbook and Sheet
		HSSFWorkbook wb = new HSSFWorkbook();
		sheet = wb.createSheet(filename);
		HSSFHeader header = sheet.getHeader();
		header.setCenter(filename);
    	
	    HSSFFont boldFont = wb.createFont();
	    boldFont.setBoldweight(HSSFFont.BOLDWEIGHT_BOLD);
	    HSSFCellStyle boldStyle = wb.createCellStyle();
	    boldStyle.setAlignment(HSSFCellStyle.ALIGN_CENTER);
	    boldStyle.setFont(boldFont);
	    boldStyle.setWrapText(true);

	    //Start : populate the spreadsheet
    	int rowCounter = 0;
    	rowCounter = searchCriteriaRows(allData, boldStyle);
	    rowCounter = headerRow(allData, boldStyle, rowCounter);
	    int headingsRowSplitter = rowCounter;
		rowCounter = dataRows(allData, rowCounter);
	    //end : populate the spreadsheet
	    
		// Freeze Panes on Header Row
		sheet.createFreezePane(0, headingsRowSplitter);
		// Row 1 Repeats on each page
		wb.setRepeatingRowsAndColumns(0, 0, 0, 0, headingsRowSplitter);
	
		// Set Print Area, Footer
	    int colCount = allData.at("metaData").at("columns").asInteger();
		wb.setPrintArea(0, 0, colCount, 0, rowCounter);
		HSSFFooter footer = sheet.getFooter();
		footer.setCenter("Page " + HSSFFooter.page() + " of " + HSSFFooter.numPages());
		// Fit Sheet to 1 page wide but very long
		sheet.setAutobreaks(true);
		HSSFPrintSetup ps = sheet.getPrintSetup();
		ps.setFitWidth((short) 1);
		ps.setFitHeight((short) 9999);
		sheet.setGridsPrinted(true);
		sheet.setHorizontallyCenter(true);
		ps.setPaperSize(HSSFPrintSetup.LETTER_PAPERSIZE);
		if (colCount > 5)
		{
		    ps.setLandscape(true);
		}
		if (colCount > 10)
		{
		    ps.setPaperSize(HSSFPrintSetup.LEGAL_PAPERSIZE);
		}
		if (colCount > 14)
		{
		    ps.setPaperSize(HSSFPrintSetup.EXECUTIVE_PAPERSIZE);
		}
		// Set Margins
		ps.setHeaderMargin((double) .35);
		ps.setFooterMargin((double) .35);
		sheet.setMargin(HSSFSheet.TopMargin, (double) .50);
		sheet.setMargin(HSSFSheet.BottomMargin, (double) .50);
		sheet.setMargin(HSSFSheet.LeftMargin, (double) .50);
		sheet.setMargin(HSSFSheet.RightMargin, (double) .50);
	
		// Write out the spreadsheet
		wb.write(out);
		out.close();
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
    public void setHeaderColumnStyle(HSSFRow row, HSSFCell cell, HSSFCellStyle boldStyle, String columnValue, int columnNo) 
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
		// Determine the height of the column head
		int height = wordCnt * 275;
		if (row.getHeight() < height)
		    row.setHeight((short) height);
		// Set Cell to boldStyle
		cell.setCellStyle(boldStyle);
    }

    public int headerRow(Json allData, HSSFCellStyle boldStyle, int rowCounter)
    {
    	Json metaData = allData.at("metaData");
    	//Create Header Row and their cells
	    HSSFRow row = sheet.createRow(rowCounter++);
	    int cellCounter = 0;
	    HSSFCell cell = row.createCell(cellCounter);
	    setHeaderColumnStyle(row, cell, boldStyle, metaData.at("boid").asString(), cellCounter++);
    	cell.setCellValue(metaData.at("boid").asString());

	    cell = row.createCell(cellCounter);
	    setHeaderColumnStyle(row, cell, boldStyle, metaData.at("type").asString(), cellCounter++);
    	cell.setCellValue(metaData.at("type").asString());

	    cell = row.createCell(cellCounter);
	    setHeaderColumnStyle(row, cell, boldStyle, metaData.at("fullAddress").asString(), cellCounter++);
    	cell.setCellValue(metaData.at("fullAddress").asString());

	    cell = row.createCell(cellCounter);
	    setHeaderColumnStyle(row, cell, boldStyle, metaData.at("city").asString(), cellCounter++);
    	cell.setCellValue(metaData.at("city").asString());

	    cell = row.createCell(cellCounter);
	    setHeaderColumnStyle(row, cell, boldStyle, metaData.at("zip").asString(), cellCounter++);
    	cell.setCellValue(metaData.at("zip").asString());

    	cell = row.createCell(cellCounter);
	    setHeaderColumnStyle(row, cell, boldStyle, metaData.at("hasStatus").asString(), cellCounter++);
    	cell.setCellValue(metaData.at("hasStatus").asString());

	    cell = row.createCell(cellCounter);
	    setHeaderColumnStyle(row, cell, boldStyle, metaData.at("createdDate").asString(), cellCounter++);
    	cell.setCellValue(metaData.at("createdDate").asString());

    	cell = row.createCell(cellCounter);
	    setHeaderColumnStyle(row, cell, boldStyle, metaData.at("lastActivityUpdatedDate").asString(), cellCounter++);
    	cell.setCellValue(metaData.at("lastActivityUpdatedDate").asString());

    	if(metaData.at("columns").asInteger() == 9) {
        	cell = row.createCell(cellCounter);
    	    setHeaderColumnStyle(row, cell, boldStyle, metaData.at("gisColumn").asString(), cellCounter++);
        	cell.setCellValue(metaData.at("gisColumn").asString());
    	}
    	return rowCounter;
    }

    public int dataRows(Json allData, int rowCounter)
    {
    	HSSFRow row = null;
	    HSSFCell cell = null;
    	List<Json> data = allData.at("data").asJsonList();
    	int columnCount = allData.at("metaData").at("columns").asInteger();
	    //Create Data Rows and their cells
		for(Json dataValue : data)
		{
		    int innerCellCounter = 0;
			row = sheet.createRow(rowCounter++);

		    cell = row.createCell(innerCellCounter);
    		setColumnWidth(dataValue.at("hasCaseNumber").asString(), innerCellCounter++);
	    	if(!dataValue.at("hasCaseNumber").asString().isEmpty())
	    		cell.setCellValue(dataValue.at("hasCaseNumber").asString());
	    	else
	    		cell.setCellValue(GenUtils.makeCaseNumber(dataValue.at("boid").asLong()));
		    cell = row.createCell(innerCellCounter);
		    setColumnWidth(dataValue.at("label").asString(), innerCellCounter++);
	    	cell.setCellValue(dataValue.at("label").asString());

		    cell = row.createCell(innerCellCounter);
		    setColumnWidth(dataValue.at("fullAddress").asString(), innerCellCounter++);
	    	cell.setCellValue(dataValue.at("fullAddress").asString());

		    cell = row.createCell(innerCellCounter);
		    setColumnWidth(dataValue.at("Street_Address_City").asString(), innerCellCounter++);
	    	cell.setCellValue(dataValue.at("Street_Address_City").asString());

		    cell = row.createCell(innerCellCounter);
		    setColumnWidth(dataValue.at("Zip_Code").asString(), innerCellCounter++);
	    	cell.setCellValue(dataValue.at("Zip_Code").asString());

	    	cell = row.createCell(innerCellCounter);
		    setColumnWidth(dataValue.at("hasStatus").asString(), innerCellCounter++);
	    	cell.setCellValue(dataValue.at("hasStatus").asString());

	    	cell = row.createCell(innerCellCounter);
		    setColumnWidth(dataValue.at("hasDateCreated").asString(), innerCellCounter++);
	    	cell.setCellValue(dataValue.at("hasDateCreated").asString());

	    	cell = row.createCell(innerCellCounter);
		    setColumnWidth(dataValue.at("lastActivityUpdatedDate").asString(), innerCellCounter++);
	    	cell.setCellValue(dataValue.at("lastActivityUpdatedDate").asString());

	    	if(columnCount == 9) {
		    	cell = row.createCell(innerCellCounter);
			    setColumnWidth(dataValue.at("gisColumn").asString(), innerCellCounter++);
		    	cell.setCellValue(dataValue.at("gisColumn").asString());
	    	}
		}
		return rowCounter;
    }
    
    public Integer searchCriteriaRows(Json allData, HSSFCellStyle boldStyle) {
		Json basicSCLabels = Json.object()
				.set("type", "SR Type")
				.set("legacy:hasCaseNumber", "SR ID")
				.set("atAddress", "Address")
				.set("isCreatedBy", "Input By")
				.set("name", "First Name")
				.set("lastName", "Last Name")
				.set("legacy:hasStatus", "Status")
				.set("legacy:hasIntakeMethod", "Intake Method")
				.set("legacy:hasPriority", "Priority")
				.set("serviceQuestion", "SR Question")
				.set("legacy:hasDueDate", "Over Due Date")
				.set("hasDateCreated", "Created Date")
				.set("legacy:hasServiceCaseActor", "Customer Name")
				.set("gisColumnName", "");
    	
	    Json searchCriteria = allData.at("searchCriteria");

		OWLOntology ont = ontology();
	    int rowCounter = 0;
	    //int cellCounter = 0;
	    //Create Headings and info about the report
	    HSSFRow row = sheet.createRow(rowCounter++);
	    HSSFCell cell = row.createCell(0);
    	cell.setCellValue(basicSearchReportHeader);
    	cell.setCellStyle(boldStyle);
    	sheet.addMergedRegion(new CellRangeAddress(row.getRowNum(), row.getRowNum(), 
    			cell.getColumnIndex(), cell.getColumnIndex() + 3));
	    row = sheet.createRow(rowCounter++);
	    row = sheet.createRow(rowCounter++);
//	    cellCounter = 0;
	    cell = row.createCell(0);
	    cell.setCellValue(searchCriteriaHeader);
    	sheet.addMergedRegion(new CellRangeAddress(row.getRowNum(), row.getRowNum(), 
    			cell.getColumnIndex(), cell.getColumnIndex()+2));
    	for(Entry<String, Json> prop : searchCriteria.asJsonMap().entrySet())
    	{
    		if(prop.getKey().equalsIgnoreCase("sortBy") || 
    				prop.getKey().equalsIgnoreCase("caseSensitive") ||
    				prop.getKey().equalsIgnoreCase("sortDirection") || 
    				prop.getKey().equalsIgnoreCase("currentPage") || 
    				prop.getKey().equalsIgnoreCase("itemsPerPage")) 
    			continue;
 			if(prop.getKey().equalsIgnoreCase("type")) {
				if(prop.getValue().isString() && prop.getValue().asString().equalsIgnoreCase("legacy:ServiceRequestType"))
					continue;
				if(prop.getValue().isArray()) {
					List<Json> typeList = prop.getValue().asJsonList();
					if(typeList.size() == 1 && typeList.get(0).asString().equalsIgnoreCase("legacy:ServiceRequestType"))
						continue;
				}
			}

    		StringBuilder sbQuestion = new StringBuilder("");
			StringBuilder sbAnswer = new StringBuilder("");
			
			sbQuestion.append(basicSCLabels.has(prop.getKey()) ?
					basicSCLabels.at(prop.getKey()).asString() : prop.getKey());
			if(prop.getKey().equalsIgnoreCase("type"))
    		{
    			//sbAnswer.append(getEntityLabel(individual(prop.getValue().asString())));
    			sbAnswer.append("SR TYPE");
    		}
			else if(ont.isDeclared(dataProperty(fullIri(prop.getKey())), true))
			{
   				sbAnswer.append(prop.getValue().asString());
			}
    		else if(ont.isDeclared(objectProperty(fullIri(prop.getKey())), true))
			{
    			if(prop.getKey().equals("legacy:hasStatus") || 
						prop.getKey().equals("legacy:hasIntakeMethod") || 
						prop.getKey().equals("legacy:hasPriority"))
				{
					sbAnswer.append(getEntityLabel(individual(prop.getValue().at("iri").asString())));
				}
				if(prop.getKey().equals("atAddress"))
				{
					if(prop.getValue().has("fullAddress"))
						sbAnswer.append(prop.getValue().at("fullAddress").asString());
					if(prop.getValue().has("Street_Unit_Number"))
						sbAnswer.append("#").append(prop.getValue().at("Street_Unit_Number").asString());
					if(prop.getValue().has("Street_Address_City"))
						sbAnswer.append(", ").append(ServiceRequestReportUtil.getCity(prop.getValue()));
					if(prop.getValue().has("Zip_Code"))
						sbAnswer.append(" - ").append(prop.getValue().at("Zip_Code").asString());
				}
   				else if(prop.getKey().equals("legacy:hasServiceCaseActor"))
				{
					if(prop.getValue().has("Name"))
						sbAnswer.append(prop.getValue().at("Name").asString());
					if(prop.getValue().has("LastName"))
						sbAnswer.append(" ").append(prop.getValue().at("LastName").asString());
				}
   				else if(prop.getKey().equals("hasGeoPropertySet"))
   				{
   					sbQuestion = new StringBuilder("");
   					for(Entry<String, Json> geoProp : prop.getValue().asJsonMap().entrySet())
   					{
   						if(geoProp.getKey().equals("type"))
   							continue;
   						else
   						{
   		   	    			sbQuestion.append(geoProp.getKey());
   		   	    			sbAnswer.append(geoProp.getValue().asString());
  						}
   					}
   				}
			}
			else if (ont.containsIndividualInSignature(fullIri(prop.getKey()), true)) {
				sbQuestion = new StringBuilder("");
    			sbQuestion.append(getEntityLabel(individual(prop.getKey())));
    			if(prop.getValue().isString())
    				sbAnswer.append(getEntityLabel(individual(prop.getValue().asString())));
    			else if(prop.getValue().isObject())
    				sbAnswer.append(prop.getValue().at("literal").asString());
			}
    		else
    		{
				continue;
    		}
		    row = sheet.createRow(rowCounter++);
//		    cellCounter = 0;
		    cell = row.createCell(0);
		    cell.setCellValue(sbQuestion.toString());
	    	sheet.addMergedRegion(new CellRangeAddress(row.getRowNum(), row.getRowNum(), 
	    			cell.getColumnIndex(), cell.getColumnIndex() + 1));
		    cell = row.createCell(2);
		    cell.setCellValue(sbAnswer.toString());
	    	sheet.addMergedRegion(new CellRangeAddress(row.getRowNum(), row.getRowNum(), 
	    			cell.getColumnIndex(), cell.getColumnIndex() + 1));
    	}
	    
	    row = sheet.createRow(rowCounter++);
	    row = sheet.createRow(rowCounter++);
//	    cellCounter = 0;
    	cell = row.createCell(0);
    	cell.setCellValue(totalResults);
    	cell.setCellStyle(boldStyle);
    	cell = row.createCell(1);
    	cell.setCellValue(allData.at("data").asJsonList().size());
//    	boldStyle.setFillForegroundColor(HSSFColor.YELLOW.index);
//    	boldStyle.setFillPattern(HSSFCellStyle.SOLID_FOREGROUND);
    	cell.setCellStyle(boldStyle);
    	
	    row = sheet.createRow(rowCounter++);
    	return rowCounter;
    }

}
