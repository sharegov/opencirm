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

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;

import mjson.Json;

import com.itextpdf.text.Anchor;
import com.itextpdf.text.BaseColor;
import com.itextpdf.text.Chapter;
import com.itextpdf.text.Document;
import com.itextpdf.text.DocumentException;
import com.itextpdf.text.Element;
import com.itextpdf.text.Font;
import com.itextpdf.text.PageSize;
import com.itextpdf.text.Paragraph;
import com.itextpdf.text.Phrase;
import com.itextpdf.text.Section;
import com.itextpdf.text.pdf.PdfPCell;
import com.itextpdf.text.pdf.PdfPTable;
import com.itextpdf.text.pdf.PdfWriter;

public class PDFExportUtil
{
	private static Font catFont = new Font(Font.FontFamily.TIMES_ROMAN, 18, Font.BOLD);
	private static Font redFont = new Font(Font.FontFamily.TIMES_ROMAN, 12, Font.NORMAL, BaseColor.RED);
	private static Font subFont = new Font(Font.FontFamily.TIMES_ROMAN, 16, Font.BOLD);
	private static Font smallBold = new Font(Font.FontFamily.TIMES_ROMAN, 8, Font.BOLD);
	private static Font smallFont = new Font(Font.FontFamily.TIMES_ROMAN, 8, Font.NORMAL);

	private static void addMetaData(Document doc)
	{
		doc.addTitle("My title");
		doc.addSubject("My subject");
		doc.addKeywords("itext, java, export");
		doc.addAuthor("");
		doc.addCreator("");
	}

	private static void createTable(Section subCatPart, Json allData) throws DocumentException {

		Json metaData = allData.at("metaData");
		PdfPTable table = null; 
		PdfPCell c1 = null;
		
		int columns = metaData.at("columns").asInteger();
		table = new PdfPTable(columns);
		table.setWidthPercentage(100);
		if(columns == 9) {
			int[] widths = {10, 20, 12, 12, 4, 6, 6, 8, 8};
			table.setWidths(widths);
		}
		else {
			int[] widths = {10, 25, 12, 12, 4, 6, 6, 8};
			table.setWidths(widths);
		}
		// t.setBorderColor(BaseColor.GRAY);
		// t.setPadding(4);
		// t.setSpacing(4);
		// t.setBorderWidth(1);

		c1 = new PdfPCell(new Phrase(metaData.at("boid").asString(), smallBold));
		c1.setHorizontalAlignment(Element.ALIGN_CENTER);
		c1.setBackgroundColor(BaseColor.LIGHT_GRAY);
		table.addCell(c1);
		c1 = new PdfPCell(new Phrase(metaData.at("type").asString(), smallBold));
		c1.setHorizontalAlignment(Element.ALIGN_CENTER);
		c1.setBackgroundColor(BaseColor.LIGHT_GRAY);
		table.addCell(c1);
		c1 = new PdfPCell(new Phrase(metaData.at("fullAddress").asString(), smallBold));
		c1.setHorizontalAlignment(Element.ALIGN_CENTER);
		c1.setBackgroundColor(BaseColor.LIGHT_GRAY);
		table.addCell(c1);
		c1 = new PdfPCell(new Phrase(metaData.at("city").asString(), smallBold));
		c1.setHorizontalAlignment(Element.ALIGN_CENTER);
		c1.setBackgroundColor(BaseColor.LIGHT_GRAY);
		table.addCell(c1);
		c1 = new PdfPCell(new Phrase(metaData.at("zip").asString(), smallBold));
		c1.setHorizontalAlignment(Element.ALIGN_CENTER);
		c1.setBackgroundColor(BaseColor.LIGHT_GRAY);
		table.addCell(c1);
		c1 = new PdfPCell(new Phrase(metaData.at("hasStatus").asString(), smallBold));
		c1.setHorizontalAlignment(Element.ALIGN_CENTER);
		c1.setBackgroundColor(BaseColor.LIGHT_GRAY);
		table.addCell(c1);
		c1 = new PdfPCell(new Phrase(metaData.at("createdDate").asString(), smallBold));
		c1.setHorizontalAlignment(Element.ALIGN_CENTER);
		c1.setBackgroundColor(BaseColor.LIGHT_GRAY);
		table.addCell(c1);
		c1 = new PdfPCell(new Phrase(metaData.at("lastActivityUpdatedDate").asString(), smallBold));
		c1.setHorizontalAlignment(Element.ALIGN_CENTER);
		c1.setBackgroundColor(BaseColor.LIGHT_GRAY);
		table.addCell(c1);
		if(columns == 9)
		{
			c1 = new PdfPCell(new Phrase(metaData.at("gisColumn").asString(), smallBold));
			c1.setHorizontalAlignment(Element.ALIGN_CENTER);
			c1.setBackgroundColor(BaseColor.LIGHT_GRAY);
			table.addCell(c1);
		}
		table.setHeaderRows(1);

		List<Json> data = allData.at("data").asJsonList();
		for(Json dataValue : data)
		{
			if(!dataValue.at("hasCaseNumber").asString().isEmpty())
				c1 = new PdfPCell(new Phrase(dataValue.at("hasCaseNumber").asString(), smallFont));
			else
				c1 = new PdfPCell(new Phrase(GenUtils.makeCaseNumber(dataValue.at("boid").asLong()), smallFont));
			table.addCell(c1);
			c1 = new PdfPCell(new Phrase(dataValue.at("label").asString(), smallFont));
			table.addCell(c1);
			c1 = new PdfPCell(new Phrase(dataValue.at("fullAddress").asString(), smallFont));
			table.addCell(c1);
			c1 = new PdfPCell(new Phrase(dataValue.at("Street_Address_City").asString(), smallFont));
			table.addCell(c1);
			c1 = new PdfPCell(new Phrase(dataValue.at("Zip_Code").asString(), smallFont));
			table.addCell(c1);
			c1 = new PdfPCell(new Phrase(dataValue.at("hasStatus").asString(), smallFont));
			table.addCell(c1);
			c1 = new PdfPCell(new Phrase(dataValue.at("hasDateCreated").asString(), smallFont));
			table.addCell(c1);
			c1 = new PdfPCell(new Phrase(dataValue.at("lastActivityUpdatedDate").asString(), smallFont));
			table.addCell(c1);
			if(columns == 9)
			{
				c1 = new PdfPCell(new Phrase(dataValue.at("gisColumn").asString(), smallFont));
				table.addCell(c1);
			}
		}
		
		subCatPart.add(table);
	}

    public void exportData(OutputStream out, Json data) throws IOException
    {
    	try {
        	Document doc = new Document(PageSize.LETTER.rotate());
        	PdfWriter.getInstance(doc, out);
			doc.open();
			//addMetaData(doc);
			addContent(doc, data);
			doc.close();
    	}
		catch (DocumentException e)
		{
			e.printStackTrace();
		}
    }

	private static void addContent(Document doc, Json data) throws DocumentException
	{
		Anchor anchor = new Anchor(); //new Anchor("First Chapter", catFont);
		//anchor.setName("First Chapter Anchor");
		
		Chapter catPart = new Chapter(new Paragraph(anchor), 1);
		catPart.setNumberDepth(0);
		Paragraph subPara = new Paragraph(); //new Paragraph("SubCategory 1", subFont);
		Section subCatPart = catPart.addSection(subPara);
		subCatPart.setNumberDepth(0);
		
		createTable(subCatPart, data);
		
		//Add everything to the document
		doc.add(catPart);
	}
}
