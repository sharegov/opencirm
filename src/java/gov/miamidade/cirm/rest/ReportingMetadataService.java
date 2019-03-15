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
package gov.miamidade.cirm.rest;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import mjson.Json;

import org.semanticweb.owlapi.model.OWLNamedIndividual;
import org.sharegov.cirm.OWL;
import org.sharegov.cirm.utils.GenUtils;

import gov.miamidade.cirm.maintenance.DepartmentConsistencyChecker;
import gov.miamidade.cirm.maintenance.ProvidedByChecker;
import gov.miamidade.cirm.reports.ReportingMetadataExport;

/**
 * Rest Service class to expose metadata needed for reporting
 * database
 * 
 * 		dumpSRTypes();
 *		dumpStatus();
 *		dumpPriority();
 *		dumpIntakeMethods();
 *		dumpActivities();
 *		dumpAnnotationLabels();
 *		dumpQuestions();
 *		dumpChoiceValues();
 *		dumpOutcomes();
 *		dumpUserOrgUnit();
 *		dumpObservedHolidays();
 *		dumpMetaIndividuals();
 * @author Syed
 *
 */
@Path("reportingMetadata")
@Produces("application/json")
@Consumes("application/json")
public class ReportingMetadataService
{
	
	ReportingMetadataExport export  = new ReportingMetadataExport();
	
	@GET
	@Path("/srTypes")
	@Produces(MediaType.APPLICATION_JSON)
	public Json srTypes()
	{
			return export.getSRTypesAsJson();
	}
	
	@GET
	@Path("/srStatus")
	@Produces(MediaType.APPLICATION_JSON)
	public Json srStatus()
	{
			return export.getSRStatusAsJson();
	}
	
	@GET
	@Path("/srPriority")
	@Produces(MediaType.APPLICATION_JSON)
	public Json srPriority()
	{
			return export.getSRPriorityAsJson();
	}
	
	@GET
	@Path("/srIntakeMethods")
	@Produces(MediaType.APPLICATION_JSON)
	public Json srIntakeMethods()
	{
			return export.getSRIntakeMethodsAsJson();
	}
	
	@GET
	@Path("/srActivities")
	@Produces(MediaType.APPLICATION_JSON)
	public Json srActivities()
	{
			return export.getSRActivitiesAsJson();
	}
	
	@GET
	@Path("/annotationLabels")
	@Produces(MediaType.APPLICATION_JSON)
	public Json annotationLabels()
	{
			return export.getSRAnnotationLabelsAsJson();
	}
	
	@GET
	@Path("/srQuestions")
	@Produces(MediaType.APPLICATION_JSON)
	public Json srQuestions()
	{
			return export.getSRQuestionsAsJson();
	}
	
	@GET
	@Path("/srOutcomes")
	@Produces(MediaType.APPLICATION_JSON)
	public Json srOutcomes()
	{
			return export.getSROutcomesAsJson();
	}
	
	@GET
	@Path("/userOrgUnit")
	@Produces(MediaType.APPLICATION_JSON)
	public Json userOrgUnit()
	{
			return export.getUserOrgUnitAsJson();
	}

	@GET
	@Path("/observedHolidays")
	@Produces(MediaType.APPLICATION_JSON)
	public Json observedHolidays()
	{
			return export.getObservedHolidaysAsJson();
	}
	
	@GET
	@Path("/iriLabels")
	@Produces(MediaType.APPLICATION_JSON)
	public Json iriLabels()
	{
			return export.getMetaIndividualsAsJson();
	}
	

	@GET
	@Path("/srChoiceValues")
	@Produces(MediaType.APPLICATION_JSON)
	public Json srChoiceValues()
	{
			return export.getSRChoiceValuesAsJson();
	}
	
	@GET
	@Path("/srOrgUnit")
	@Produces(MediaType.APPLICATION_JSON)
	public Json srOrgUnit()
	{
			return export.getSROrgUnitAsJson();
	}

	@GET
	@Path("/departments/payroll/active")
	@Produces(MediaType.TEXT_HTML)
	public String getCountyDepartmentsFromPayroll()
	{
		StringBuilder html = new StringBuilder();
		html
		.append("<!DOCTYPE HTML PUBLIC \"-//W3C//DTD HTML 4.01//EN\" \"http://www.w3.org/TR/html4/strict.dtd\"><html dir=\"ltr\" lang=\"en\">")
		.append("<head><meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\"><title>Ontology Departmentmental Inconsistencies")
		.append("</title><style>") 
		.append("table { width:400px;background:#D3E4E5;")
		.append(" border:1px solid gray;")
		.append(" border-collapse:collapse;")
		.append(" color:#fff;")
		.append(" font:normal 12px verdana, arial, helvetica, sans-serif;")
		.append("}")
		.append("caption { border:1px solid #5C443A;")
		.append(" color:#5C443A;")
		.append(" font-weight:bold;")
		.append(" letter-spacing:20px;")
		.append(" padding:6px 4px 8px 0px;")
		.append(" text-align:center;")
		.append(" text-transform:uppercase;")
		.append("}")
		.append("td, th { color:#363636;")
		.append(" padding:.4em;")
		.append("}")
		.append("tr { border:1px dotted gray;")
		.append("}")
		.append("thead th, tfoot th { background:#5C443A;")
		.append(" color:#FFFFFF;")
		.append(" padding:3px 10px 3px 10px;")
		.append(" text-align:left;")
		.append(" text-transform:uppercase;")
		.append("}")
		.append("tbody td a { color:#363636;")
		.append(" text-decoration:none;")
		.append("}")
		.append("tbody td a:visited { color:gray;")
		.append(" text-decoration:line-through;")
		.append("}")
		.append("tbody td a:hover { text-decoration:underline;")
		.append("}")
		.append("tbody th a { color:#363636;")
		.append(" font-weight:normal;")
		.append(" text-decoration:none;")
		.append("}")
		.append("tbody th a:hover { color:#363636;")
		.append("}")
		.append("tbody th, tbody td { text-align:left;")
		.append(" vertical-align:top;")
		.append("}")
		.append("tfoot td { background:#5C443A;")
		.append(" color:#FFFFFF;")
		.append(" padding-top:3px;")
		.append("}")
		.append(".odd { background:#fff;")
		.append("}")
		.append("tbody tr:hover { background:#99BCBF;")
		.append(" border:1px solid #03476F;")
		.append(" color:#000000;")
		.append("	}</style></head>")
		.append("<body><table>");

		DepartmentConsistencyChecker checker = new DepartmentConsistencyChecker();
		html.append("<thead><tr>");
		html.append("<th>").append("Active Employees").append("</th>");
		html.append("<th>").append("Department Name").append("</th>");
		html.append("<th>").append("Department #").append("</th>");
		html.append("<th>").append("Update Date").append("</th>");
		html.append("</tr></thead>");
		List<Map<String,Object>> depts = checker.getDepartmentsFromPayroll();
		for(Map<String, Object> dept : depts)
		{
			html.append("<tbody><tr>");
			html.append("<td>").append(dept.get("payr_active_empl").toString()).append("</td>");
			html.append("<td>").append(dept.get("payr_dept_name").toString()).append("</td>");
			html.append("<td><a href=\"../../departments/").append(dept.get("payr_dept_id").toString()).append("/divisions/payroll/active\">").append(dept.get("payr_dept_id").toString()).append("</a></td>");
			html.append("<td>").append(dept.get("payr_refresh_date").toString()).append("</td>");
			html.append("</tr></tbody>");
		}
		html.append("</table></body></html>");
		return html.toString();
	}
	
	@GET
	@Path("/departments/{department}/divisions/payroll/active")
	@Produces(MediaType.TEXT_HTML)
	public String getCountyDivisionsFromPayroll(@PathParam("department" ) String department)
	{
		StringBuilder html = new StringBuilder();
		html
		.append("<!DOCTYPE HTML PUBLIC \"-//W3C//DTD HTML 4.01//EN\" \"http://www.w3.org/TR/html4/strict.dtd\"><html dir=\"ltr\" lang=\"en\">")
		.append("<head><meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\"><title>Ontology Departmentmental Inconsistencies")
		.append("</title><style>") 
		.append("table { width:400px;background:#D3E4E5;")
		.append(" border:1px solid gray;")
		.append(" border-collapse:collapse;")
		.append(" color:#fff;")
		.append(" font:normal 12px verdana, arial, helvetica, sans-serif;")
		.append("}")
		.append("caption { border:1px solid #5C443A;")
		.append(" color:#5C443A;")
		.append(" font-weight:bold;")
		.append(" letter-spacing:20px;")
		.append(" padding:6px 4px 8px 0px;")
		.append(" text-align:center;")
		.append(" text-transform:uppercase;")
		.append("}")
		.append("td, th { color:#363636;")
		.append(" padding:.4em;")
		.append("}")
		.append("tr { border:1px dotted gray;")
		.append("}")
		.append("thead th, tfoot th { background:#5C443A;")
		.append(" color:#FFFFFF;")
		.append(" padding:3px 10px 3px 10px;")
		.append(" text-align:left;")
		.append(" text-transform:uppercase;")
		.append("}")
		.append("tbody td a { color:#363636;")
		.append(" text-decoration:none;")
		.append("}")
		.append("tbody td a:visited { color:gray;")
		.append(" text-decoration:line-through;")
		.append("}")
		.append("tbody td a:hover { text-decoration:underline;")
		.append("}")
		.append("tbody th a { color:#363636;")
		.append(" font-weight:normal;")
		.append(" text-decoration:none;")
		.append("}")
		.append("tbody th a:hover { color:#363636;")
		.append("}")
		.append("tbody th, tbody td { text-align:left;")
		.append(" vertical-align:top;")
		.append("}")
		.append("tfoot td { background:#5C443A;")
		.append(" color:#FFFFFF;")
		.append(" padding-top:3px;")
		.append("}")
		.append(".odd { background:#fff;")
		.append("}")
		.append("tbody tr:hover { background:#99BCBF;")
		.append(" border:1px solid #03476F;")
		.append(" color:#000000;")
		.append("	}</style></head>")
		.append("<body><table>");

		DepartmentConsistencyChecker checker = new DepartmentConsistencyChecker();
		html.append("<thead><tr>");
			html.append("<th>").append("Active Employees").append("</th>");
			html.append("<th>").append("Division Code").append("</th>");
			html.append("<th>").append("Division Name").append("</th>");
		html.append("</tr></thead>");
		List<Map<String,Object>> depts = checker.getDivisionsFromPayroll(department);
		for(Map<String, Object> dept : depts)
		{
			html.append("<tbody><tr>");
			html.append("<td>").append(dept.get("payr_active_empl").toString()).append("</td>");
			html.append("<td>").append(dept.get("payr_div_id").toString()).append("</td>");
			html.append("<td>").append(dept.get("payr_div_name").toString()).append("</td>");
			html.append("</tr></tbody>");
		}
		html.append("</table></body></html>");
		return html.toString();
	}
	
	@GET
	@Path("/departments/{department}/divisions/ontology/active")
	@Produces(MediaType.TEXT_HTML)
	public String getCountyDivisionsFromOntology(@PathParam("department" ) String department)
	{
		StringBuilder html = new StringBuilder();
		html
		.append("<!DOCTYPE HTML PUBLIC \"-//W3C//DTD HTML 4.01//EN\" \"http://www.w3.org/TR/html4/strict.dtd\"><html dir=\"ltr\" lang=\"en\">")
		.append("<head><meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\"><title>Ontology Departmentmental Inconsistencies")
		.append("</title><style>") 
		.append("table { width:400px;background:#D3E4E5;")
		.append(" border:1px solid gray;")
		.append(" border-collapse:collapse;")
		.append(" color:#fff;")
		.append(" font:normal 12px verdana, arial, helvetica, sans-serif;")
		.append("}")
		.append("caption { border:1px solid #5C443A;")
		.append(" color:#5C443A;")
		.append(" font-weight:bold;")
		.append(" letter-spacing:20px;")
		.append(" padding:6px 4px 8px 0px;")
		.append(" text-align:center;")
		.append(" text-transform:uppercase;")
		.append("}")
		.append("td, th { color:#363636;")
		.append(" padding:.4em;")
		.append("}")
		.append("tr { border:1px dotted gray;")
		.append("}")
		.append("thead th, tfoot th { background:#5C443A;")
		.append(" color:#FFFFFF;")
		.append(" padding:3px 10px 3px 10px;")
		.append(" text-align:left;")
		.append(" text-transform:uppercase;")
		.append("}")
		.append("tbody td a { color:#363636;")
		.append(" text-decoration:none;")
		.append("}")
		.append("tbody td a:visited { color:gray;")
		.append(" text-decoration:line-through;")
		.append("}")
		.append("tbody td a:hover { text-decoration:underline;")
		.append("}")
		.append("tbody th a { color:#363636;")
		.append(" font-weight:normal;")
		.append(" text-decoration:none;")
		.append("}")
		.append("tbody th a:hover { color:#363636;")
		.append("}")
		.append("tbody th, tbody td { text-align:left;")
		.append(" vertical-align:top;")
		.append("}")
		.append("tfoot td { background:#5C443A;")
		.append(" color:#FFFFFF;")
		.append(" padding-top:3px;")
		.append("}")
		.append(".odd { background:#fff;")
		.append("}")
		.append("tbody tr:hover { background:#99BCBF;")
		.append(" border:1px solid #03476F;")
		.append(" color:#000000;")
		.append("	}</style></head>")
		.append("<body><table>");

		DepartmentConsistencyChecker checker = new DepartmentConsistencyChecker();
		html.append("<thead><tr>");
		html.append("<th>").append("Division Name").append("</th>");
		html.append("<th>").append("Division Code").append("</th>");
		html.append("<th>").append("Fragment").append("</th>");
		html.append("</tr></thead>");
		List<Map<String,Object>> divs = checker.getDivisionsFromOntology(OWL.individual(OWL.fullIri(department)));
		for(Map<String, Object> div : divs)
		{
			html.append("<tbody><tr>");
			html.append("<td>").append(div.get("Name").toString()).append("</td>");
			html.append("<td>").append(div.get("Division_Code").toString()).append("</td>");
			html.append("<td>").append(div.get("iri").toString()).append("</td>");
			html.append("</tr></tbody>");
		}
		html.append("</table></body></html>");
		return html.toString();
	}
	@GET
	@Path("/departments/ontology/active")
	@Produces(MediaType.TEXT_HTML)
	public String getCountyDepartmentsFromOntology()
	{
		StringBuilder html = new StringBuilder();
		html
		.append("<!DOCTYPE HTML PUBLIC \"-//W3C//DTD HTML 4.01//EN\" \"http://www.w3.org/TR/html4/strict.dtd\"><html dir=\"ltr\" lang=\"en\">")
		.append("<head><meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\"><title>Ontology Departmentmental Inconsistencies")
		.append("</title><style>") 
		.append("table { width:400px;background:#D3E4E5;")
		.append(" border:1px solid gray;")
		.append(" border-collapse:collapse;")
		.append(" color:#fff;")
		.append(" font:normal 12px verdana, arial, helvetica, sans-serif;")
		.append("}")
		.append("caption { border:1px solid #5C443A;")
		.append(" color:#5C443A;")
		.append(" font-weight:bold;")
		.append(" letter-spacing:20px;")
		.append(" padding:6px 4px 8px 0px;")
		.append(" text-align:center;")
		.append(" text-transform:uppercase;")
		.append("}")
		.append("td, th { color:#363636;")
		.append(" padding:.4em;")
		.append("}")
		.append("tr { border:1px dotted gray;")
		.append("}")
		.append("thead th, tfoot th { background:#5C443A;")
		.append(" color:#FFFFFF;")
		.append(" padding:3px 10px 3px 10px;")
		.append(" text-align:left;")
		.append(" text-transform:uppercase;")
		.append("}")
		.append("tbody td a { color:#363636;")
		.append(" text-decoration:none;")
		.append("}")
		.append("tbody td a:visited { color:gray;")
		.append(" text-decoration:line-through;")
		.append("}")
		.append("tbody td a:hover { text-decoration:underline;")
		.append("}")
		.append("tbody th a { color:#363636;")
		.append(" font-weight:normal;")
		.append(" text-decoration:none;")
		.append("}")
		.append("tbody th a:hover { color:#363636;")
		.append("}")
		.append("tbody th, tbody td { text-align:left;")
		.append(" vertical-align:top;")
		.append("}")
		.append("tfoot td { background:#5C443A;")
		.append(" color:#FFFFFF;")
		.append(" padding-top:3px;")
		.append("}")
		.append(".odd { background:#fff;")
		.append("}")
		.append("tbody tr:hover { background:#99BCBF;")
		.append(" border:1px solid #03476F;")
		.append(" color:#000000;")
		.append("	}</style></head>")
		.append("<body><table>");

		DepartmentConsistencyChecker checker = new DepartmentConsistencyChecker();
		html.append("<thead><tr>");
		html.append("<th>").append("Department Name").append("</th>");
		html.append("<th>").append("Department #").append("</th>");
		html.append("<th>").append("Fragment").append("</th>");
		html.append("</tr></thead>");
		List<Map<String,Object>> depts = checker.getDepartmentsFromOntology();
		for(Map<String, Object> dept : depts)
		{
			html.append("<tbody><tr>");
			html.append("<td>").append(dept.get("Name").toString()).append("</td>");
			html.append("<td>").append(dept.get("Dept_Code").toString()).append("</td>");
			html.append("<td><a href=\"../../departments/").append(dept.get("iri").toString()).append("/divisions/ontology/active\">").append(dept.get("iri").toString()).append("</a></td>");
			html.append("</tr></tbody>");
		}
		html.append("</table></body></html>");
		return html.toString();
	}
	
	@GET
	@Path("/departments/inconsistencies")
	@Produces(MediaType.TEXT_HTML)
	public String getCountyDepartmentalInconsistencies()
	{
		StringBuilder html = new StringBuilder();
		html
		.append("<!DOCTYPE HTML PUBLIC \"-//W3C//DTD HTML 4.01//EN\" \"http://www.w3.org/TR/html4/strict.dtd\"><html dir=\"ltr\" lang=\"en\">")
		.append("<head><meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\"><title>Ontology Departmentmental Inconsistencies")
		.append("</title><style>") 
		.append("table { width:400px;background:#D3E4E5;")
		.append(" border:1px solid gray;")
		.append(" border-collapse:collapse;")
		.append(" color:#fff;")
		.append(" font:normal 12px verdana, arial, helvetica, sans-serif;")
		.append("}")
		.append("caption { border:1px solid #5C443A;")
		.append(" color:#5C443A;")
		.append(" font-weight:bold;")
		.append(" letter-spacing:20px;")
		.append(" padding:6px 4px 8px 0px;")
		.append(" text-align:center;")
		.append(" text-transform:uppercase;")
		.append("}")
		.append("td, th { color:#363636;")
		.append(" padding:.4em;")
		.append("}")
		.append("tr { border:1px dotted gray;")
		.append("}")
		.append("thead th, tfoot th { background:#5C443A;")
		.append(" color:#FFFFFF;")
		.append(" padding:3px 10px 3px 10px;")
		.append(" text-align:left;")
		.append(" text-transform:uppercase;")
		.append("}")
		.append("tbody td a { color:#363636;")
		.append(" text-decoration:none;")
		.append("}")
		.append("tbody td a:visited { color:gray;")
		.append(" text-decoration:line-through;")
		.append("}")
		.append("tbody td a:hover { text-decoration:underline;")
		.append("}")
		.append("tbody th a { color:#363636;")
		.append(" font-weight:normal;")
		.append(" text-decoration:none;")
		.append("}")
		.append("tbody th a:hover { color:#363636;")
		.append("}")
		.append("tbody th, tbody td { text-align:left;")
		.append(" vertical-align:top;")
		.append("}")
		.append("tfoot td { background:#5C443A;")
		.append(" color:#FFFFFF;")
		.append(" padding-top:3px;")
		.append("}")
		.append(".odd { background:#fff;")
		.append("}")
		.append("tbody tr:hover { background:#99BCBF;")
		.append(" border:1px solid #03476F;")
		.append(" color:#000000;")
		.append("	}</style></head>")
		.append("<body><table>");

		DepartmentConsistencyChecker checker = new DepartmentConsistencyChecker();
		String[] header = checker.getHeader();
		html.append("<thead><tr>");
		for(String value : header)
		{
			html.append("<th>").append(value).append("</th>");
		}
		html.append("</tr></thead>");
		List<Object[]> rows = checker.getInconsistencies();
		for(Object[] row : rows)
		{
			html.append("<tbody><tr>");
				for(Object value : row)
				{
					if(value instanceof Integer)
					{
						switch((Integer)value)
						{
							case DepartmentConsistencyChecker.DEPT_IN_ONTO_NOT_IN_PAYROLL:
								html.append("<td>").append("Dept Not In Payroll").append("</td>");
								break;
							case DepartmentConsistencyChecker.DEPT_IN_PAYROLL_NOT_IN_ONTO:
								html.append("<td>").append("Dept Not In Ontology").append("</td>");
								break;
							case DepartmentConsistencyChecker.DIV_IN_ONTO_NOT_IN_PAYROLL:
								html.append("<td>").append("Div Not In Payroll").append("</td>");
								break;
							case DepartmentConsistencyChecker.DIV_IN_PAYROLL_NOT_IN_ONTO:
								html.append("<td>").append("Div Not In Onto").append("</td>");
								break;
							default:
								break;
							
						}
					}else
					{
						html.append("<td>").append(value.toString()).append("</td>");
					}
				}
			html.append("</tr></tbody>");
		}
		html.append("</table></body></html>");
		return html.toString();
	}
	
	@GET
	@Path("/providedBy")
	@Produces(MediaType.TEXT_HTML)
	public String getProvidedBy()
	{
		StringBuilder html = new StringBuilder();
		html
		.append("<!DOCTYPE HTML PUBLIC \"-//W3C//DTD HTML 4.01//EN\" \"http://www.w3.org/TR/html4/strict.dtd\"><html dir=\"ltr\" lang=\"en\">")
		.append("<head><meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\"><title>Ontology Departmentmental Inconsistencies")
		.append("</title><style>") 
		.append("table { width:400px;background:#D3E4E5;")
		.append(" border:1px solid gray;")
		.append(" border-collapse:collapse;")
		.append(" color:#fff;")
		.append(" font:normal 12px verdana, arial, helvetica, sans-serif;")
		.append("}")
		.append("caption { border:1px solid #5C443A;")
		.append(" color:#5C443A;")
		.append(" font-weight:bold;")
		.append(" letter-spacing:20px;")
		.append(" padding:6px 4px 8px 0px;")
		.append(" text-align:center;")
		.append(" text-transform:uppercase;")
		.append("}")
		.append("td, th { color:#363636;")
		.append(" padding:.4em;")
		.append("}")
		.append("tr { border:1px dotted gray;")
		.append("}")
		.append("thead th, tfoot th { background:#5C443A;")
		.append(" color:#FFFFFF;")
		.append(" padding:3px 10px 3px 10px;")
		.append(" text-align:left;")
		.append(" text-transform:uppercase;")
		.append("}")
		.append("tbody td a { color:#363636;")
		.append(" text-decoration:none;")
		.append("}")
		.append("tbody td a:visited { color:gray;")
		.append(" text-decoration:line-through;")
		.append("}")
		.append("tbody td a:hover { text-decoration:underline;")
		.append("}")
		.append("tbody th a { color:#363636;")
		.append(" font-weight:normal;")
		.append(" text-decoration:none;")
		.append("}")
		.append("tbody th a:hover { color:#363636;")
		.append("}")
		.append("tbody th, tbody td { text-align:left;")
		.append(" vertical-align:top;")
		.append("}")
		.append("tfoot td { background:#5C443A;")
		.append(" color:#FFFFFF;")
		.append(" padding-top:3px;")
		.append("}")
		.append(".odd { background:#fff;")
		.append("}")
		.append("tbody tr:hover { background:#99BCBF;")
		.append(" border:1px solid #03476F;")
		.append(" color:#000000;")
		.append("	}</style></head>")
		.append("<body><table>");

		ProvidedByChecker checker = new ProvidedByChecker();
		html.append("<thead><tr>");
		html.append("<th>").append("ServiceCase").append("</th>");
		html.append("<th>").append("ProvidedBy").append("</th>");
		html.append("<th>").append("ProvidedByOWLClass").append("</th>");
		html.append("<th>").append("CSR Jurisdiction").append("</th>");
		html.append("<th>").append("CSR Group Code").append("</th>");
		html.append("<th>").append("CSR Group Description").append("</th>");
		html.append("</tr></thead>");
		Map<OWLNamedIndividual, Map<OWLNamedIndividual, List<Object>>> serviceCases = checker.getServiceCasesWithProvidedBy(true);
		for(Map.Entry<OWLNamedIndividual, Map<OWLNamedIndividual, List<Object>>> entry : serviceCases.entrySet())
		{
			html.append("<tbody><tr>");
			html.append("<td>").append(entry.getKey().getIRI().getFragment()).append("</td>");
			Map<OWLNamedIndividual, List<Object>> value = entry.getValue();
			if(value.equals(Collections.emptyMap()))
			{
				html.append("<td>").append("--No Provided By--").append("</td>");
				html.append("<td>").append("&nbsp;").append("</td>");
			}
			else
			{
				Map.Entry<OWLNamedIndividual, List<Object>> providedByWithProps = value.entrySet().iterator().next();
				html.append("<td>").append(providedByWithProps.getKey().getIRI().getFragment()).append("</td>");
				html.append("<td>").append(providedByWithProps.getValue().get(0)).append("</td>");
			}
			Map<String,String> csrProps = checker.getCSRGroup(entry.getKey());
			html.append("<td>").append(csrProps.get("JURISDICTION_DESC")).append("</td>");
			html.append("<td>").append(csrProps.get("CSR_GROUP_CODE")).append("</td>");
			html.append("<td>").append(csrProps.get("CSR_GROUP_DESC")).append("</td>");
			html.append("</tr></tbody>");
		}
		html.append("</table></body></html>");
		return html.toString();
	}

}
