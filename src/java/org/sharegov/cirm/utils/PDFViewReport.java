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

import static org.sharegov.cirm.utils.GenUtils.containsWhiteSpace;
import static org.sharegov.cirm.OWL.individual;
import static org.sharegov.cirm.OWL.objectProperty;
import static org.sharegov.cirm.OWL.dataProperty;
import static org.sharegov.cirm.OWL.getEntityLabel;
import static org.sharegov.cirm.utils.ServiceRequestReportUtil.getAddressPropertyValue;
import static org.sharegov.cirm.utils.ServiceRequestReportUtil.getAllServiceAnswers;
import static org.sharegov.cirm.utils.ServiceRequestReportUtil.blankField;
import static org.sharegov.cirm.utils.ServiceRequestReportUtil.emptyField;
import static org.sharegov.cirm.utils.ServiceRequestReportUtil.legacyPrefix;
import static org.sharegov.cirm.utils.ServiceRequestReportUtil.formatDate;
import static org.sharegov.cirm.utils.ServiceRequestReportUtil.getCity;

import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.semanticweb.owlapi.model.OWLAnnotation;
import org.semanticweb.owlapi.model.OWLIndividual;
import org.semanticweb.owlapi.model.OWLLiteral;
import org.semanticweb.owlapi.model.OWLNamedIndividual;
import org.semanticweb.owlapi.model.OWLObjectProperty;
import org.sharegov.cirm.rest.LegacyEmulator;
import org.sharegov.cirm.rest.UserService;
import org.sharegov.cirm.BOntology;
import org.sharegov.cirm.OWL;
import org.sharegov.cirm.StartUp;

import mjson.Json;

import com.itextpdf.text.Anchor;
import com.itextpdf.text.BaseColor;
import com.itextpdf.text.Chapter;
import com.itextpdf.text.Chunk;
import com.itextpdf.text.Document;
import com.itextpdf.text.DocumentException;
import com.itextpdf.text.Element;
import com.itextpdf.text.Font;
import com.itextpdf.text.Image;
import com.itextpdf.text.PageSize;
import com.itextpdf.text.Paragraph;
import com.itextpdf.text.Phrase;
import com.itextpdf.text.Rectangle;
import com.itextpdf.text.Section;
import com.itextpdf.text.pdf.PdfPCell;
import com.itextpdf.text.pdf.PdfPTable;
import com.itextpdf.text.pdf.PdfPageEventHelper;
import com.itextpdf.text.pdf.PdfWriter;
import com.itextpdf.text.pdf.draw.LineSeparator;

public class PDFViewReport 
{

	public static boolean DBG = true;
	private static final Font boldFont = new Font(Font.FontFamily.TIMES_ROMAN, 10, Font.BOLD);
	private static final Font normalFont = new Font(Font.FontFamily.TIMES_ROMAN, 10, Font.NORMAL);
	private static final Font bigBoldFont = new Font(Font.FontFamily.TIMES_ROMAN, 12, Font.BOLD);
	private static final Font bigNormalFont = new Font(Font.FontFamily.TIMES_ROMAN, 12, Font.NORMAL);

	private static final String genericErrorMsg = "Sorry, an error occured while generating the PDF report. " +
			"An automated email was sent to the Admin with all the required information.";

	String title = "Service Request Summary Report";
	String time;
	
	private final UserService userService;
	
	public PDFViewReport() 
	{
		userService = new UserService();
	}
	
	public void setTime(String time)
	{
		this.time = time;
	}

	public String getTime()
	{
		return time;
	}

	public void setTitle(String title)
	{
		this.title = title;
	}

	public String getTitle()
	{
		return title;
	}

	class TableHeader extends PdfPageEventHelper
	{
		public void onEndPage(PdfWriter writer, Document d)
		{
			float cellHeight = d.topMargin();
			Rectangle page = d.getPageSize();
			
			PdfPTable head = new PdfPTable(1);
			head.setTotalWidth(page.getWidth());
			
			PdfPCell c = new PdfPCell(new Phrase(getTitle(), bigBoldFont));
			c.setHorizontalAlignment(Element.ALIGN_CENTER);
			c.setFixedHeight(cellHeight);
			c.setBorder(PdfPCell.NO_BORDER);
			head.addCell(c);

			float temp = page.getHeight() - cellHeight + head.getTotalHeight();
			
			head.writeSelectedRows(0, -1, 0, temp, writer.getDirectContent());

			head = new PdfPTable(1);
			head.setTotalWidth(page.getWidth());
			c = new PdfPCell(new Phrase(getTime(), bigNormalFont));
			c.setHorizontalAlignment(Element.ALIGN_CENTER);
			c.setFixedHeight(cellHeight);
			c.setBorder(PdfPCell.NO_BORDER);
			head.addCell(c);

			//table header requires absolute positioning
			head.writeSelectedRows(0, -1, 0, temp-15, writer.getDirectContent());
		}
	}
	
	private void addCell(PdfPTable table, String name, int align, Font font, BaseColor color)
	{
		PdfPCell cell = new PdfPCell(new Phrase(name, font));
		cell.setHorizontalAlignment(align);
		cell.setBorder(Rectangle.NO_BORDER);
		if(color != null)
			cell.setBackgroundColor(color);
		table.addCell(cell);
	}

	private void addCell(PdfPCell cell, PdfPTable table, String name, int align, Font font, BaseColor color)
	{
		cell = new PdfPCell(new Phrase(name, font));
		cell.setHorizontalAlignment(align);
		cell.setBorder(Rectangle.NO_BORDER);
		if(color != null)
			cell.setBackgroundColor(color);
		table.addCell(cell);
	}
	
	private BaseColor addColor(Integer i)
	{
		if(i == null)
			return null;
		if(i%2 == 0)
			return BaseColor.LIGHT_GRAY;
		else
			return BaseColor.WHITE;
	}

	private void addTopTable(Section subCatPart, Json data) throws DocumentException
	{
		PdfPTable table = new PdfPTable(4);
		table.setWidthPercentage(100);
		int[] widths = {12, 55, 15, 20};
		table.setWidths(widths);
		
		PdfPCell cell = null;
		
		OWLNamedIndividual typeInd = individual(legacyPrefix+data.at("type").asString());
		addCell(cell, table, "Type:", Element.ALIGN_RIGHT, boldFont, null);
		addCell(cell, table, getEntityLabel(typeInd), Element.ALIGN_LEFT, normalFont, null);
		addCell(cell, table, "SR #:", Element.ALIGN_RIGHT, boldFont, null);
		if(data.at("properties").has("hasCaseNumber"))
			addCell(cell, table, data.at("properties").at("hasCaseNumber").asString(), 
					Element.ALIGN_LEFT, normalFont, null);
		else
			addCell(cell, table, GenUtils.makeCaseNumber(data.at("boid").asLong()), 
					Element.ALIGN_LEFT, normalFont, null);
		//02-20-2014 - Syed add providedby to report
		addCell(cell, table, "Area:", Element.ALIGN_RIGHT, boldFont, null);
		addCell(cell, table, getArea(typeInd), Element.ALIGN_LEFT, normalFont, null);
		addCell(cell, table, "Priority:", Element.ALIGN_RIGHT, boldFont, null);
		addCell(cell, table, data.at("properties").at("hasPriority").at("label").asString(), 
				Element.ALIGN_LEFT, normalFont, null);
		addCell(cell, table, "Group:", Element.ALIGN_RIGHT, boldFont, null);
		addCell(cell, table, getGroup(typeInd), Element.ALIGN_LEFT, normalFont, null);
		addCell(cell, table, "Status:", Element.ALIGN_RIGHT, boldFont, null);
		addCell(cell, table, 
				getEntityLabel(individual(data.at("properties"), "hasStatus")), 
				Element.ALIGN_LEFT, normalFont, null);

		addCell(cell, table, "Jurisdiction:", Element.ALIGN_RIGHT, boldFont, null);
		OWLLiteral jurisdiction = dataProperty(typeInd, legacyPrefix+"hasJurisdictionDescription");
		addCell(cell, table, jurisdiction != null ? jurisdiction.getLiteral() : emptyField, 
				Element.ALIGN_LEFT, normalFont, null);
		addCell(cell, table, "Status Date:", Element.ALIGN_RIGHT, boldFont, null);
		String modifiedDate = data.at("properties").has("hasDateLastModified") ? 
				data.at("properties").at("hasDateLastModified").asString() : "";
		addCell(cell, table, formatDate(modifiedDate, true, false), Element.ALIGN_LEFT, normalFont, null);

		addCell(cell, table, "Input By:", Element.ALIGN_RIGHT, boldFont, null);
		String inputBy = data.at("properties").has("isCreatedBy") ? 
							data.at("properties").at("isCreatedBy").asString() : emptyField;
		addCell(cell, table, inputBy, Element.ALIGN_LEFT, normalFont, null);
		addCell(cell, table, "Created Date:", Element.ALIGN_RIGHT, boldFont, null);
		String createdDate = data.at("properties").has("hasDateCreated") ? 
				data.at("properties").at("hasDateCreated").asString() : "";
		addCell(cell, table, formatDate(createdDate, true, false), Element.ALIGN_LEFT, normalFont, null);

		addCell(cell, table, "CC Groups:", Element.ALIGN_RIGHT, boldFont, null);
		addCell(cell, table, blankField, Element.ALIGN_LEFT, normalFont, null);
		addCell(cell, table, "Created By:", Element.ALIGN_RIGHT, boldFont, null);
		addCell(cell, table, getEmployeeName(inputBy), Element.ALIGN_LEFT, normalFont, null);
		
		addCell(cell, table, "Location:", Element.ALIGN_RIGHT, boldFont, null);
		addCell(cell, table, data.at("properties").has("atAddress") ? buildAddress(data.at("properties").at("atAddress")) : blankField, 
				Element.ALIGN_LEFT, normalFont, null);
		addCell(cell, table, "Method Received:", Element.ALIGN_RIGHT, boldFont, null);
		addCell(cell, table, data.at("properties").at("hasIntakeMethod").at("label").asString(), 
				Element.ALIGN_LEFT, normalFont, null);
		
		subCatPart.add(table);
		subCatPart.add(new Chunk(new LineSeparator()));
	}
	
	private String getGroup(OWLNamedIndividual srType)
	{
		String result = blankField;
		OWLNamedIndividual providedBy  = OWL.objectProperty(srType, "legacy:providedBy");
		System.out.println(srType + " providedBy " + providedBy);
		if(providedBy != null)
		{
			OWLLiteral s = OWL.dataProperty(providedBy, "Name");
			if(s != null)
				result = s.getLiteral();
		}
		return result;
	}
	
	//In the PDF report for each 
	//SR add the Department the SR is 
	//associated with  where it now says Area,
	//and the division, where it says Group.
	private String getArea(OWLNamedIndividual srType)
	{
		String result = blankField;
		OWLNamedIndividual providedBy  = OWL.objectProperty(srType, "legacy:providedBy");
		if(providedBy != null )
		{
			OWLNamedIndividual parent = OWL.objectProperty(providedBy, "hasParentAgency");
			if(parent != null)
			{
				OWLLiteral s = OWL.dataProperty(parent, "Name");
				if(s != null)
					result = s.getLiteral();
			}
		}
		return result;
	}

	private String getEmployeeName(String ceKey)
	{
		if(ceKey == null || ceKey.isEmpty() || ceKey.equals(blankField))
			return blankField;
		if (ceKey.contains("@")||ceKey.contains(";")) 
			return ceKey;
		//Lookup in Bluebook, ENET, Onto		
		String userFullName = userService.getFullName(ceKey);
		return (userFullName == null || userFullName.isEmpty())? ceKey : userFullName;
	}
	
	private String buildAddress(Json address)
	{
		StringBuilder addr = new StringBuilder();
		if(address.has("fullAddress"))
			addr.append(address.at("fullAddress").asString());
		if(address.has("Street_Unit_Number"))
			addr.append(", #").append(address.at("Street_Unit_Number").asString());
		if(address.has("Street_Address_City"))
			addr.append(", ").append(getCity(address).trim());
		if(address.has("Street_Address_State"))
			addr.append(", ").append(
					getAddressPropertyValue(address, "Street_Address_State", "USPS_Abbreviation"));
		if(address.has("Zip_Code"))
			addr.append(" - ").append(address.at("Zip_Code").asString());
		return addr.toString();
	}
	
	private void addDescription(Section subCatPart, Json data) throws DocumentException
	{
		PdfPTable table = new PdfPTable(2);
		table.setWidthPercentage(100);
		int[] widths = {15, 85};
		table.setWidths(widths);
		
		PdfPCell cell = null;

		addCell(cell, table, "Description:", Element.ALIGN_LEFT, bigBoldFont, null);
		if(data.at("properties").has("hasDetails"))
			addCell(cell, table, data.at("properties").at("hasDetails").asString(), Element.ALIGN_LEFT, normalFont, null);

		subCatPart.add(table);
		subCatPart.add(new Chunk(new LineSeparator()));
	}
	
	private void addQuestions(Section subCatPart, Json data) throws DocumentException, UnsupportedEncodingException
	{
		PdfPTable table = new PdfPTable(2);
		table.setWidthPercentage(100);
		int[] widths = {65, 35};
		table.setWidths(widths);

		PdfPCell cell = null;

		addCell(cell, table, "SR Questions", Element.ALIGN_LEFT, bigBoldFont, null);
		addCell(cell, table, "Answers", Element.ALIGN_LEFT, bigBoldFont, null);
		
		subCatPart.add(table);
		//subCatPart.add(Chunk.NEWLINE);
		//subCatPart.add(new Chunk(new LineSeparator()));

		table = new PdfPTable(2);
		table.setWidthPercentage(100);
		table.setWidths(widths);

		OWLNamedIndividual typeInd = individual(legacyPrefix+data.at("type").asString());
		List<Json> allServiceAnswers = new ArrayList<Json>();
		LegacyEmulator.getAllServiceFields(typeInd, allServiceAnswers, true);
		
		List<Json> saList = getAllServiceAnswers(data);

		for(int k = 0; k < allServiceAnswers.size(); k++)
		{
			Json serviceAnswer = allServiceAnswers.get(k);
			BaseColor color = addColor(k);
			int counter = 0;
			addCell(cell, table, serviceAnswer.at("hasServiceField").at("label").asString(), 
					Element.ALIGN_LEFT, normalFont, color);
			for (Json sa : saList)
			{
				if(sa.at("hasServiceField").at("iri").asString().
						equals(serviceAnswer.at("hasServiceField").at("iri").asString()))
				{
					OWLIndividual qtn = individual(sa.at("hasServiceField").at("iri").asString());
					OWLObjectProperty cvl = objectProperty(legacyPrefix+"hasChoiceValueList");

					if(sa.has("hasAnswerValue"))
					{
						if(sa.at("hasAnswerValue").at("literal").isArray())
						{
							List<Json> literalList = sa.at("hasAnswerValue").at("literal").asJsonList();
							for(int i=0; i<literalList.size(); i++)
							{
								String eachAns = literalList.get(i).asString();
								addCell(cell, table, eachAns, Element.ALIGN_LEFT, normalFont, color);
								if(i != literalList.size()-1)
									addCell(cell, table, blankField, Element.ALIGN_LEFT, normalFont, color);
							}
						}
						else 
						{
							String ans = sa.at("hasAnswerValue").at("literal").asString();
							if(serviceAnswer.at("hasDataType").asString().equals("NUMBER"))
								ans = Long.toString(new BigDecimal(ans).longValue());
							if(serviceAnswer.at("hasDataType").asString().equals("DATE"))
								ans = formatDate(ans, false, false);
							addCell(cell, table, ans, Element.ALIGN_LEFT, normalFont, color);
							
						}
					}
					else if(sa.has("hasAnswerObject"))
					{
						if(sa.at("hasAnswerObject").isArray())
						{
							List<Json> answerObjectList = sa.at("hasAnswerObject").asJsonList();
							for(int i=0; i<answerObjectList.size(); i++)
							{
								Json eachAnswerObject = answerObjectList.get(i);
								//OWL.objectProperties(qtn, cvl.getIRI().toString());
								if(!OWL.collectObjectProperties(qtn, cvl).isEmpty() && 
										!containsWhiteSpace(eachAnswerObject.at("iri").asString()))
								{
									addCell(cell, table, getEntityLabel(individual(eachAnswerObject.at("iri").asString())), 
											Element.ALIGN_LEFT, normalFont, color);
									if(i != answerObjectList.size()-1)
										addCell(cell, table, blankField, Element.ALIGN_LEFT, normalFont, color);
								}
							}
						}
						else 
						{
							String ans = sa.at("hasAnswerObject").at("iri").asString();
							if(!OWL.collectObjectProperties(qtn, cvl).isEmpty() && 
									!containsWhiteSpace(ans))
							{
								addCell(cell, table, getEntityLabel(individual(ans)), Element.ALIGN_LEFT, normalFont, color);
							}
							else
								addCell(cell, table, ans, Element.ALIGN_LEFT, normalFont, color);
						}
					}
					++counter;
				}
				else
					continue;
			}
			if(counter == 0)
				addCell(cell, table, emptyField, Element.ALIGN_LEFT, normalFont, color);
		}
		subCatPart.add(table);
		subCatPart.add(new Chunk(new LineSeparator()));
	}
	
	private void addActors(Section subCatPart, Json data) throws DocumentException
	{
		if(data.at("properties").has("hasServiceCaseActor"))
		{
			int iActorHeaderColumns = 5;
			int iActorColumns = 6;
			PdfPTable table = new PdfPTable(iActorHeaderColumns);
			table.setWidthPercentage(100);
			int[] headerWidths = {15, 15, 25, 18, 22};
			int[] widths = {15, 15, 25, 18, 5, 17};
			table.setWidths(headerWidths);
			PdfPCell cell = null;
	
			addCell(cell, table, "Customer", Element.ALIGN_LEFT, bigBoldFont, null);
			addCell(cell, table, "Name", Element.ALIGN_LEFT, bigBoldFont, null);
			addCell(cell, table, "Address", Element.ALIGN_LEFT, bigBoldFont, null);
			addCell(cell, table, "e-Mail", Element.ALIGN_LEFT, bigBoldFont, null);
			addCell(cell, table, "Contact No", Element.ALIGN_LEFT, bigBoldFont, null);
	
			subCatPart.add(table);
			//subCatPart.add(Chunk.NEWLINE);
			//subCatPart.add(new Chunk(new LineSeparator()));
	
			table = new PdfPTable(iActorColumns);
			table.setWidthPercentage(100);
			table.setWidths(widths);

			if(!data.at("properties").at("hasServiceCaseActor").isArray())
			{
				data.at("properties").set("hasServiceCaseActor", 
						Json.array().add(data.at("properties").at("hasServiceCaseActor")));
			}

			for(int k=0; k < data.at("properties").at("hasServiceCaseActor").asJsonList().size(); k++)
			{
				Json actor = data.at("properties").at("hasServiceCaseActor").at(k);
				BaseColor color = addColor(k);
				OWLNamedIndividual act = individual(actor, "hasServiceActor");
				String actorType = getEntityLabel(act);

				StringBuilder actorName = new StringBuilder();
				if(actor.has("Name"))
					actorName.append(actor.at("Name").asString());
				if(actor.has("LastName"))
				{
					if(actorName.toString().isEmpty())
						actorName.append(actor.at("LastName").asString());
					else
						actorName.append(" ").append(actor.at("LastName").asString());
				}
				if(actorName.toString().isEmpty())
					actorName.append(blankField);

				StringBuilder actorAddr = new StringBuilder();
				if(actor.has("atAddress"))
				{
					Json addr = actor.at("atAddress");
					if(addr.has("fullAddress"))
						actorAddr.append(addr.at("fullAddress").asString());
					if(addr.has("Street_Unit_Number"))
						actorAddr.append(", #").append(addr.at("Street_Unit_Number").asString());
					if(addr.has("Street_Address_City"))
					{
						OWLNamedIndividual cityInd = individual(addr, "Street_Address_City");
						OWLLiteral city = dataProperty(cityInd, "Name");
						if(city == null)
							city = dataProperty(cityInd, "Alias");
						if(city != null)
							actorAddr.append(", ").append(city.getLiteral());
					}
					if(addr.has("fullAddress") && addr.has("Street_Address_State"))
					{
						OWLNamedIndividual stateInd = individual(addr, "Street_Address_State");
						OWLLiteral state = dataProperty(stateInd, "USPS_Abbreviation");
						if(state != null)
							actorAddr.append(", ").append(state.getLiteral());
					}
					if(addr.has("Zip_Code"))
						actorAddr.append(" - ").append(addr.at("Zip_Code").asString());
				}
				else
					actorAddr.append(blankField);

				String eMail = actor.has("hasEmailAddress") ?
						actor.at("hasEmailAddress").isObject() ? 
							actor.at("hasEmailAddress").at("iri").asString().split(":")[1] : 
							actor.at("hasEmailAddress").asString().split(":")[1]
						: blankField;
						
				addCell(cell, table, actorType, Element.ALIGN_LEFT, normalFont, color);
				addCell(cell, table, actorName.toString(), Element.ALIGN_LEFT, normalFont, color);
				addCell(cell, table, actorAddr.toString(), Element.ALIGN_LEFT, normalFont, color);
				addCell(cell, table, eMail, Element.ALIGN_LEFT, normalFont, color);

				boolean isFirstContactNo = true;
				String actorHmPh = actor.has("HomePhoneNumber") ? 
						actor.at("HomePhoneNumber").asString() : null;
				String actorCellNo = actor.has("CellPhoneNumber") ?
						actor.at("CellPhoneNumber").asString() : null;
				String actorBizNo = actor.has("BusinessPhoneNumber") ?
						actor.at("BusinessPhoneNumber").asString() : null;
				String actorFaxNo = actor.has("FaxNumber") ?
						actor.at("FaxNumber").asString() : null;
				String actorOtherNo = actor.has("OtherPhoneNumber") ?
						actor.at("OtherPhoneNumber").asString() : null;
				
				if(actorHmPh != null)
					isFirstContactNo = addActorsContactNumbers(
							table, cell, actorHmPh, "Home ", iActorColumns, color, isFirstContactNo);
				if(actorCellNo != null)
					isFirstContactNo = addActorsContactNumbers(
							table, cell, actorCellNo, "Cell ", iActorColumns, color, isFirstContactNo);
				if(actorBizNo != null)
					isFirstContactNo = addActorsContactNumbers(
							table, cell, actorBizNo, "Biz ", iActorColumns, color, isFirstContactNo);
				if(actorFaxNo != null)
					isFirstContactNo = addActorsContactNumbers(
							table, cell, actorFaxNo, "Fax ", iActorColumns, color, isFirstContactNo);
				if(actorOtherNo != null)
					isFirstContactNo = addActorsContactNumbers(
							table, cell, actorOtherNo, "Other ", iActorColumns, color, isFirstContactNo);
				if(actorHmPh == null && actorCellNo == null && actorBizNo == null 
						&& actorFaxNo == null && actorOtherNo == null)
				{
					addCell(cell, table, blankField, Element.ALIGN_LEFT, normalFont, color);
					addCell(cell, table, blankField, Element.ALIGN_LEFT, normalFont, color);
				}
			}
			subCatPart.add(table);
			subCatPart.add(new Chunk(new LineSeparator()));
		}
	}

	private boolean addActorsContactNumbers(PdfPTable table, PdfPCell cell, 
			String contactNumber, String contactNoType, 
			int iActorColumns, BaseColor color, boolean isFirstContactNo)
	{
		String[] contactNumberList = contactNumber.split(",");
		if(contactNumberList.length == 1)
		{
			if(isFirstContactNo)
			{
				addCell(cell, table, contactNoType, Element.ALIGN_LEFT, normalFont, color);
				addCell(cell, table, contactNumberList[0], Element.ALIGN_LEFT, normalFont, color);
				isFirstContactNo = false;
			}	
			else
			{
				for(int j = 1; j < iActorColumns-1; j++)
					addCell(cell, table, blankField, Element.ALIGN_LEFT, normalFont, color);
				addCell(cell, table, contactNoType, Element.ALIGN_LEFT, normalFont, color);
				addCell(cell, table, contactNumberList[0], Element.ALIGN_LEFT, normalFont, color);
			}
		}
		else if(contactNumberList.length > 1)
		{
			for(int i=0; i<contactNumberList.length; i++)
			{
				if(i == 0 && isFirstContactNo)
				{
					addCell(cell, table, contactNoType, Element.ALIGN_LEFT, normalFont, color);
					addCell(cell, table, contactNumberList[i], Element.ALIGN_LEFT, normalFont, color);
					isFirstContactNo = false;
				}
				else
				{
					for(int j = 1; j < iActorColumns-1; j++)
						addCell(cell, table, blankField, Element.ALIGN_LEFT, normalFont, color);
					addCell(cell, table, contactNoType, Element.ALIGN_LEFT, normalFont, color);
					addCell(cell, table, contactNumberList[i], Element.ALIGN_LEFT, normalFont, color);
				}
			}
		}
		return isFirstContactNo;
	}

	private void addActivities(Section subCatPart, Json data) throws DocumentException
	{
		if(data.at("properties").has("hasServiceActivity"))
		{
			if(!data.at("properties").at("hasServiceActivity").isArray())
			{
				data.at("properties").set("hasServiceActivity", 
						Json.array().add(data.at("properties").at("hasServiceActivity")));
			}
			Json filteredActivities = Json.array();
			//Do not add any StatusChangeActivity Activities to the report
			for(Json act : data.at("properties").at("hasServiceActivity").asJsonList())
			{
				OWLNamedIndividual actInd = individual(act, "hasActivity");
				if(!actInd.getIRI().getFragment().equals("StatusChangeActivity"))
					filteredActivities.add(act);
			}
			
			if(filteredActivities.asJsonList().size() > 0)
			{
				//PdfPTable table = new PdfPTable(7);
				PdfPTable table = new PdfPTable(6);
				table.setWidthPercentage(100);
				//int[] widths = {17, 13, 12, 12, 13, 13, 20};
				int[] widths = {25, 20, 13, 12, 13, 17};
				table.setWidths(widths);
				PdfPCell cell = null;
		
				addCell(cell, table, "Activity", Element.ALIGN_LEFT, bigBoldFont, null);
				addCell(cell, table, "Assigned To", Element.ALIGN_LEFT, bigBoldFont, null);
				addCell(cell, table, "Created Date", Element.ALIGN_LEFT, bigBoldFont, null);
				addCell(cell, table, "Due Date", Element.ALIGN_LEFT, bigBoldFont, null);
				addCell(cell, table, "Completed Date", Element.ALIGN_LEFT, bigBoldFont, null);
				addCell(cell, table, "Outcome", Element.ALIGN_LEFT, bigBoldFont, null);
				//addCell(cell, table, "Details", Element.ALIGN_LEFT, bigBoldFont);
		
				subCatPart.add(table);
				//subCatPart.add(Chunk.NEWLINE);
				//subCatPart.add(new Chunk(new LineSeparator()));
		
				//table = new PdfPTable(7);
				//table = new PdfPTable(6);
				//table.setWidthPercentage(100);
				//table.setWidths(widths);

				//Store all outcome iris(key), labels(value).
				//Because in case of duplicate outcomes all outcome values (except the first) will be a string and not object
				Map<String, String> outcomeMap = new HashMap<String, String>(10);
				
				//for (int k = 0; k < data.at("properties").at("hasServiceActivity").asJsonList().size(); k++)
				for(int k = 0; k < filteredActivities.asJsonList().size(); k++)
				{
					Json activity = filteredActivities.at(k);
					BaseColor color = addColor(k);
					OWLIndividual act = individual(activity, "hasActivity");
					OWLLiteral typeLabel = null;
					for (OWLAnnotation ann : OWL.annotations(act.asOWLNamedIndividual()))
					{
						if(ann.getProperty().isLabel())
							typeLabel = (OWLLiteral)ann.getValue();
					}

					String activityType = typeLabel == null ? "" : typeLabel.getLiteral();
					String assignedTo = activity.has("isAssignedTo") ? 
							activity.at("isAssignedTo").asString() : blankField;
					String createdDate = activity.has("hasDateCreated") ? 
							activity.at("hasDateCreated").asString() : blankField;
					String dueDate = activity.has("hasDueDate") ? 
							activity.at("hasDueDate").asString() : blankField;
					String completedDate = activity.has("hasCompletedTimestamp") ? 
							activity.at("hasCompletedTimestamp").asString() : blankField;
					String details = activity.has("hasDetails") ? 
							activity.at("hasDetails").asString() : blankField;
					StringBuilder outcome = new StringBuilder("");
					if(activity.has("hasOutcome"))
					{
						if(activity.at("hasOutcome").isObject())
						{
							if(!outcomeMap.containsKey(activity.at("hasOutcome").at("iri").asString()))
							{
								outcomeMap.put(
										activity.at("hasOutcome").at("iri").asString(), 
										activity.at("hasOutcome").at("label").asString()
								);
								outcome.append(activity.at("hasOutcome").at("label").asString());
							}
						}
						else if(activity.at("hasOutcome").isString())
						{
							if(outcomeMap.containsKey(activity.at("hasOutcome").asString()))
								outcome.append(outcomeMap.get(activity.at("hasOutcome").asString()));
							else
								outcome.append(OWL.fullIri(activity.at("hasOutcome").asString()).getFragment());
						}
					}
					
					//table = new PdfPTable(7);
					table = new PdfPTable(6);
					table.setWidthPercentage(100);
					table.setWidths(widths);

					addCell(cell, table, activityType, Element.ALIGN_LEFT, normalFont, color);
					addCell(cell, table, getEmployeeName(assignedTo), Element.ALIGN_LEFT, normalFont, color);
					addCell(cell, table, formatDate(createdDate, false, false), Element.ALIGN_LEFT, normalFont, color);
					addCell(cell, table, formatDate(dueDate, false, false), Element.ALIGN_LEFT, normalFont, color);
					addCell(cell, table, formatDate(completedDate, true, false), Element.ALIGN_LEFT, normalFont, color);
					addCell(cell, table, outcome.toString(), Element.ALIGN_LEFT, normalFont, color);
					subCatPart.add(table);
					if(!details.equals(blankField))
					{
						table = new PdfPTable(2);
						table.setWidthPercentage(100);
						int[] detailsWitdh = {10, 90};
						table.setWidths(detailsWitdh);
						addCell(cell, table, "Details: ", Element.ALIGN_LEFT, normalFont, color);
						addCell(cell, table, details, Element.ALIGN_LEFT, normalFont, color);
						subCatPart.add(table);
					}
				}
				//subCatPart.add(table);
				subCatPart.add(new Chunk(new LineSeparator()));
			}
		}
	}
	
	private void addContent(Document d, Json data) 
	{
		Anchor anchor = new Anchor();
		Chapter catPart = new Chapter(new Paragraph(anchor), 1);
		catPart.setNumberDepth(0);
		Paragraph subPara = new Paragraph(); //new Paragraph("SubCategory 1", subFont);
		Section subCatPart = catPart.addSection(subPara);
		subCatPart.setNumberDepth(0);
		
		try{
			addTopTable(subCatPart, data);
			addDescription(subCatPart, data);
			addQuestions(subCatPart, data);
			addActors(subCatPart, data);
			addActivities(subCatPart, data);
			d.add(catPart);
		}
		catch(Exception e)
		{
			e.printStackTrace();
			throw new RuntimeException("Problem during addContend for BO: " + data.toString(), e);
		}
	}

    public void generateReport(OutputStream out, List<Long> boids)
    {
    	if (DBG) System.out.println("Start: PDFgenerateReport: for nrOfBoids: " + boids.size());
    	Document doc = null;
    	try {
			Date date = new Date();
			setTime(formatDate(date, true, false));
			doc = new Document(PageSize.A4);
			doc.setMargins(9, 9, doc.topMargin(), doc.bottomMargin());
			PdfWriter writer = PdfWriter.getInstance(doc, out);
			writer.setPageEvent(new TableHeader());
        	doc.open();
			//addMetaData(doc);
        	LegacyEmulator le = new LegacyEmulator();
        	int i = 1;
        	for(Long boid : boids)
        	{
       			ThreadLocalStopwatch.getWatch().time("PDFViewReport loading " + i + " of " + boids.size());
    			Json data = le.findServiceCaseOntology(boid).toJSON();
       			addContent(doc, data);
       			i++;
        	}
    	}
		catch (DocumentException e)
		{
			e.printStackTrace();
			throw new RuntimeException(e);
		}
    	finally {
    		doc.close();
    	}
    	if (DBG) System.out.println("Done: PDFgenerateReport: for nrOfBoids: " + boids.size());
    }
    
    /**
     * Generates the HET Rebate letter based on the given input parameters
     * @param out : OutputStream to which the letter is written to
     * @param applicant : ServiceCaseActor of type Applicant
     * @param hasCaseNumber : Service Request's Case Number 
     * @param isEnglish : A Flag denoting the language of the letter
     */
    public void generateHETRebateLetter(OutputStream out, 
    		Json applicant, String hasCaseNumber, 
    		boolean isEnglish)
    {
    	if(DBG) System.out.println("Start : generateHETRebateLetter");
    	Document doc = null;
    	try {
    		doc = new Document(PageSize.A4);
			doc.setMargins(9, 9, doc.topMargin(), doc.bottomMargin());
			PdfWriter writer = PdfWriter.getInstance(doc, out);
			doc.open();
			
			addWASDContent(doc, applicant, hasCaseNumber, isEnglish);
    	}
    	catch(Exception e)
    	{
    		e.printStackTrace();
    		throw new RuntimeException(e);
    	}
    	finally {
			doc.close();
	    	if(DBG) System.out.println("End : generateHETRebateLetter");
    	}
    }
    
    private Section addSection(Chapter chapter)
	{
		Section section = chapter.addSection(new Paragraph());
		section.setNumberDepth(0);
		return section;
	}

	private void addWASDContent(Document d, Json applicant, String hasCaseNumber, boolean isEnglish)
	{
		//Anchor anchor = new Anchor();
		try{
			Chapter chapter = new Chapter(new Paragraph(), 1);
			chapter.setNumberDepth(0);
			//addWASDHeader(chapter);
			addWASDBody(chapter, applicant, hasCaseNumber, isEnglish);
			d.add(chapter);
		}
		catch(Exception e)
		{
			e.printStackTrace();
			throw new RuntimeException(e);
		}
	}

	public String spanishHETRebateLetterPart1()
    {
    	return "Gracias por su inter�s en participar en el Programa " +
    			"para el Uso Eficiente del Agua del Departamento de " +
    			"Aguas y Alcantarillodas de Miami-Dade. Hemos recibido " +
    			"su aplicaci�n para el proyecto de Reembolso y est� siendo " +
    			"procesada, usted debe recibir su reembolso en los pr�ximos 60 dias.";
    }

	public String spanishHETRebateLetterPart2()
    {
    	return "Si necesita informaci�n adicional, favor de ponerse en contacto " +
    			"con la oficina del programa para el Uso Eficiente del Agua al" +
    			"(786) 552-8974 o por correo electr�nico en " +
    			"waterconservation@miamidad.gov";
    }

	public String englishHETRebateLetterPart1()
    {
    	return "We have received your application regarding the " +
    			"Water-Use Efficiency Program High-Efficiency Toilet (HET) " +
    			"Rebate Project. Your application is currently being " +
    			"processed and you should receive your rebate within 60 days. ";
    }
    public String englishHETRebateLetterPart2()
    {
    	return "For additional information, please contact the " +
    			"Water-Use Efficiency Program at (786) 552-8974 " +
    			"or via email at waterconservation@miamidade.gov";
    }
    
	private void addWASDBody(Chapter chapter, 
			Json applicant, String hasCaseNumber, 
			boolean isEnglish) throws DocumentException
	{
		addSection(chapter).add(new Phrase(" "));
		addSection(chapter).add(new Phrase(" "));
		addSection(chapter).add(new Phrase(" "));
		addSection(chapter).add(new Phrase(" "));
		addSection(chapter).add(new Phrase(" "));
		addSection(chapter).add(new Phrase(" "));
		addSection(chapter).add(new Phrase(" "));
    	Json address = applicant.at("atAddress");
//    	Json address = data.at("properties").at("atAddress");
		
		addSection(chapter).add(new Phrase(" "));
		addSection(chapter).add(new Phrase(formatDate(new Date(), false, false)));

		addSection(chapter).add(new Phrase(" "));
		StringBuilder name = new StringBuilder("");
		name.append(applicant.at("Name").asString())
			.append(" ")
			.append(applicant.at("LastName").asString());
		addSection(chapter).add(new Phrase(name.toString()));
/*
		for(Json sa : data.at("properties").at("hasServiceAnswer").asJsonList())
		{
			if(OWL.fullIri(sa.at("hasServiceField").at("iri").asString())
					.getFragment().equals("WASDHET_WSCQ7") && sa.has("hasAnswerValue"))
			{
				StringBuilder name = new StringBuilder();
				if(sa.at("hasAnswerValue").at("literal").isArray())
				{
					for(Json literal : sa.at("hasAnswerValue").at("literal").asJsonList())
						name.append(literal.asString()).append(" , ");
					name.deleteCharAt(name.lastIndexOf(","));
				}
				else
					name.append(sa.at("hasAnswerValue").at("literal").asString());
				addSection(chapter).add(new Phrase(name.toString()));
			}
		}
*/		
		StringBuilder addr = new StringBuilder();
		if(address.has("fullAddress"))
			addr.append(address.at("fullAddress").asString());
		if(address.has("Street_Unit_Number") && !address.at("Street_Unit_Number").asString().isEmpty())
			addr.append(", #").append(address.at("Street_Unit_Number").asString());
		addSection(chapter).add(new Phrase(addr.toString()));

		addr = new StringBuilder();
		if(address.has("Street_Address_City"))
			addr.append(getCity(address).trim());
		if(address.has("Street_Address_State"))
			addr.append(", ").append(
					getAddressPropertyValue(address, "Street_Address_State", "USPS_Abbreviation"));
		if(address.has("Zip_Code") && !address.at("Zip_Code").asString().isEmpty())
			addr.append(" - ").append(address.at("Zip_Code").asString());
		addSection(chapter).add(new Phrase(addr.toString()));
		addSection(chapter).add(new Phrase(" "));
		addSection(chapter).add(new Phrase(" "));
		
		if(isEnglish)
		{
			addSection(chapter).add(new Phrase(englishHETRebateLetterPart1()));
			addSection(chapter).add(new Phrase(" "));
			addSection(chapter).add(new Phrase(englishHETRebateLetterPart2()));
		}
		else
		{
			addSection(chapter).add(new Phrase(spanishHETRebateLetterPart1()));
			addSection(chapter).add(new Phrase(" "));
			addSection(chapter).add(new Phrase(spanishHETRebateLetterPart2()));
		}
		addSection(chapter).add(new Phrase(" "));
		addSection(chapter).add(new Phrase(" "));
		addSection(chapter).add(new Phrase(" "));
		addSection(chapter).add(new Phrase("HET REBATE PROCESS # :"+hasCaseNumber));
		//chapter.add(new Chunk(new LineSeparator()));
	}

	private void addWASDHeader(Chapter chapter) throws DocumentException
	{
		try {
			String img = StartUp.config.at("workingDir").asString() + "/src/html/images/md-logo.png";
			Image logo = Image.getInstance(img);
			logo.scalePercent(67);
			
			PdfPTable table = new PdfPTable(2);
			table.setWidthPercentage(100);
			int[] widths = {50, 50};
			table.setWidths(widths);
			//table.addCell(logo); //This exactly fits the image to the size of the cell.
			PdfPCell cell = new PdfPCell(logo);
			cell.setBorder(Rectangle.NO_BORDER);
			table.addCell(cell);
			String str1 = "Miami-Dade Water and Sewer Department";
			String str2 = "P.O.Box 33016 . 3071 SW 38 Ave";
			String str3 = "Miami, Florida 33146";
			String str4 = "T: 786-552-8974";
			String str5 = "www.miamidade.gov";
			PdfPTable wasdTable = new PdfPTable(1);
			addCell(wasdTable, str1, Element.ALIGN_RIGHT, boldFont, null);
			addCell(wasdTable, str2, Element.ALIGN_RIGHT, normalFont, null);
			addCell(wasdTable, str3, Element.ALIGN_RIGHT, normalFont, null);
			addCell(wasdTable, str4, Element.ALIGN_RIGHT, normalFont, null);
			addCell(wasdTable, str5, Element.ALIGN_RIGHT, boldFont, null);
			cell = new PdfPCell(wasdTable);
			cell.setBorder(Rectangle.NO_BORDER);
			table.addCell(cell);
			addSection(chapter).add(table);
		}
		catch(Exception e)
		{
			e.printStackTrace();
			throw new RuntimeException(e.getMessage());
		}
	}

    private Json getServiceCase(String caseNumber)
    {
    	LegacyEmulator le = new LegacyEmulator();
    	Long boid = null;
    	if(caseNumber.contains("-"))
    	{
    		Json queryData = Json.object()
        			.set("legacy:hasCaseNumber", caseNumber)
        			.set("type","legacy:ServiceRequestType");
        		boid = le.lookupServiceCaseId(queryData);
        		if(boid == -1)
        			throw new RuntimeException("No Service Request present with Case Number : "+caseNumber);
    	}
    	else
    	{
    		boid = new Long(caseNumber);
    	}
    	BOntology bo = le.findServiceCaseOntology(boid);
    	if(bo == null)
    		throw new RuntimeException("No Service Request present with Case Number :"+caseNumber);
    	else
    		return bo.toJSON();
   }
    
	public void errorReport(OutputStream out)
	{
		if (DBG) System.out.println("Start: PDF errorReport");
		try {
			Date date = new Date();
			setTime(formatDate(date, true, false));
			Document doc = new Document(PageSize.A4);
			doc.setMargins(9, 9, doc.topMargin(), doc.bottomMargin());
			PdfWriter writer = PdfWriter.getInstance(doc, out);
			TableHeader th = new TableHeader();
			writer.setPageEvent(th);
			doc.open();
			
			Chapter chapter = new Chapter(new Paragraph(), 1);
			chapter.setNumberDepth(0);
			addSection(chapter).add(new Phrase(" "));
			addSection(chapter).add(new Phrase(" "));
			addSection(chapter).add(new Phrase(genericErrorMsg));
			doc.add(chapter);
			doc.close();
		}
		catch (DocumentException e)
		{
			e.printStackTrace();
			throw new RuntimeException(e);
		}
		if (DBG) System.out.println("Done: PDF errorReport");
	}

/*
    public static void main(String args[])
	{
		try {
			PDFViewReport pvr = new PDFViewReport();
			pvr.setTitle("Service Request Summary Report");
			Date date = new Date();
//			pvr.setTime(pvr.formatDate(date, true));

			OutputStream os = new FileOutputStream("C:/phaniTest.pdf");
			Document d = new Document(PageSize.A4);
			d.setMargins(9, 9, d.topMargin(), d.bottomMargin());
			PdfWriter writer = PdfWriter.getInstance(d, os);
			TableHeader th = pvr.new TableHeader();
			writer.setPageEvent(th);
			d.open();
//			Long boid = (long) 1927;
//			pvr.addContent(d, boid);
			pvr.addWASDContent(d, pvr.getServiceCase("13-10005150"), "13-10005151", true); //5164546
			d.close();
		}
		catch(Exception e)
		{
			e.printStackTrace();
		}
		finally
		{
			System.out.println("************** Execution Complete **************");
		}
	}
*/
}
