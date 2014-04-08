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
package gov.miamidade.cirm.maintenance;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;

import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLDataProperty;
import org.semanticweb.owlapi.model.OWLLiteral;
import org.semanticweb.owlapi.model.OWLNamedIndividual;
import org.semanticweb.owlapi.model.OWLObjectProperty;
import org.sharegov.cirm.OWL;
import org.sharegov.cirm.Refs;
import org.sharegov.cirm.user.DBUserProvider;
import org.sharegov.cirm.utils.LazyRef;
import org.sharegov.cirm.utils.Ref;

import com.sun.msv.datatype.xsd.Comparator;
/**
 * 
 * A class to bring together all definitions of county departments (bluebook/payroll/ontology)
 * and compare them for changes, updates, outdated-ness etc.
 * @author Syed
 *
 */
public class DepartmentConsistencyChecker
{
	
	private static final OWLObjectProperty hasParentAgency = OWL.objectProperty("hasParentAgency");
	private static final OWLObjectProperty Divisions = OWL.objectProperty("Divisions");
	private static final OWLObjectProperty hasDivision = OWL.objectProperty("hasDivision");
	private static final OWLNamedIndividual MiamiDadeCounty = OWL.individual("Miami-Dade_County");
	private static final OWLDataProperty Dept_Code = OWL.dataProperty("Dept_Code");
	private static final OWLDataProperty Name = OWL.dataProperty("Name");
	
	public static final int DEPT_MATCH_ONTO_AND_PAYROLL = 0;
	public static final int DEPT_IN_ONTO_NOT_IN_PAYROLL = 1;
	public static final int DEPT_IN_PAYROLL_NOT_IN_ONTO = 2;
	
	public static final int DIV_MATCH_ONTO_AND_PAYROLL = 3;
	public static final int DIV_IN_ONTO_NOT_IN_PAYROLL = 4;
	public static final int DIV_IN_PAYROLL_NOT_IN_ONTO = 5;
	
	private static Ref<DBUserProvider>  bluebookProvider =  new LazyRef<DBUserProvider>(new Callable<DBUserProvider>()
	{
		public DBUserProvider call()
		{
			return getBlueBookProvider();
		}
	});
	
	public static void main(String[] args)
	{
		DepartmentConsistencyChecker checker = new DepartmentConsistencyChecker();
		//System.out.println(checker.isOntologyConsistentWithPayroll());
		checker.compareToConsole();
		System.exit(0);
	
		
	}
	
	public boolean isOntologyConsistentWithPayroll()
	{
		boolean result = true;
		List<Object[]> comparison = compareOntologyWithPayroll();
		for(Object[] c :comparison)
		{
			//first object in array indicates comparison status
			//whenever there isn't an exact match then ontology is inconsistent with payroll.
			if(((Integer)c[0]) != DEPT_MATCH_ONTO_AND_PAYROLL && ((Integer)c[0]) != DIV_MATCH_ONTO_AND_PAYROLL)
			{
				return false;
			}
		}
		
		return result;
	}
	
	public List<Object[]> getInconsistencies()
	{
		List<Object[]> result = new ArrayList<Object[]>();
		List<Object[]> comparison = compareOntologyWithPayroll();
		for(Object[] c :comparison)
		{
			//first object in array indicates comparison status
			//where there is not an exact match then ontology is inconsistent with payroll.
			if(((Integer)c[0]) != DEPT_MATCH_ONTO_AND_PAYROLL && ((Integer)c[0]) != DIV_MATCH_ONTO_AND_PAYROLL)
			{
				result.add(c);
			}
		}
		return result;
	}
	
	public Map<String, Object> getPayrollDepartment(String ontologyDeptCode, List<Map<String, Object>>  payrollDepartments )
	{
		for(Map<String, Object> payrollDepartment : payrollDepartments )
		{
			String s = payrollDepartment.get("payr_dept_id").toString();
			String payrollDeptCode = new Integer(s).toString();
			if(ontologyDeptCode.equals(payrollDeptCode))
			{
				return payrollDepartment;
			}
		}
		return null;
	}
	
	public Map<String, Object> getPayrollDivision(String ontologyDivision, List<Map<String, Object>>  payrollDivisions )
	{
		for(Map<String, Object> payrollDivision : payrollDivisions )
		{
			String s = payrollDivision.get("payr_div_id").toString();
			String payrollDivisionCode = new Integer(s).toString();
			if(ontologyDivision.equals(payrollDivisionCode))
			{
				return payrollDivision;
			}
		}
		return null;
	}
	
	private int compareDepartmentToPayroll(String ontologyDeptCode, List<Map<String, Object>>  payrollDepartments )
	{
		for(Map<String, Object> payrollDepartment: payrollDepartments )
		{
			String s = payrollDepartment.get("payr_dept_id").toString();
			String payrollDeptCode = new Integer(s).toString();
			if(ontologyDeptCode.equals(payrollDeptCode))
			{
				return DEPT_MATCH_ONTO_AND_PAYROLL;
			}
		}
		return DEPT_IN_ONTO_NOT_IN_PAYROLL;
	}
	
	private int compareDepartmentToOntology(String payrollDeptCode, List<Map<String, Object>> ontologyDepartments )
	{
		for(Map<String, Object> ontologyDepartment: ontologyDepartments )
		{
			String ontologyDeptCode = ontologyDepartment.get("Dept_Code").toString();
			if(ontologyDeptCode.equals(payrollDeptCode))
			{
				return DEPT_MATCH_ONTO_AND_PAYROLL;
			}
		}
		return DEPT_IN_PAYROLL_NOT_IN_ONTO;
	}
	
	private int compareDivisionToPayroll(String ontologyDivCode, Map<String,Object> ontologyDivision, List<Map<String, Object>>  payrollDivisions)
	{
		for(Map<String, Object> payrollDivision: payrollDivisions )
		{
			String s = payrollDivision.get("payr_div_id").toString();
			String payrollDivCode = new Integer(s).toString();
			if(ontologyDivCode.equals(payrollDivCode))
			{
				return DIV_MATCH_ONTO_AND_PAYROLL;
			}
		}
		return DIV_IN_ONTO_NOT_IN_PAYROLL;
	}
	
	private int compareDivisionToOntology(String payrollDivCode, Map<String,Object> payrollDivision,List<Map<String, Object>>  ontologyDivisions)
	{
		for(Map<String, Object> ontologyDivision: ontologyDivisions )
		{
			String ontologyDivCode = ontologyDivision.get("Division_Code").toString();
			if(new Integer(payrollDivCode).toString().equals(ontologyDivCode))
			{
				return DIV_MATCH_ONTO_AND_PAYROLL;
			}
		}
		return DIV_IN_PAYROLL_NOT_IN_ONTO;
	}
	
	public void compareToConsole()
	{
		List<Object[]> comparison = compareOntologyWithPayroll();
		printHeaderToConsole();
		for(Object[] row : comparison )
		{
			printRowToConsole(row);
		}
	}
	
	public List<Object[]> compareOntologyWithPayroll()
	{
		List<Object[]> output = new ArrayList<Object[]>();
		List<Map<String, Object>> ontologyDepartments =  getDepartmentsFromOntology();
		List<Map<String, Object>>  payrollDepartments = getDepartmentsFromPayroll();
		
		for(Map<String, Object> ontologyDepartment : ontologyDepartments)
		{
			Object[] row = null;
			OWLNamedIndividual department = OWL.individual(OWL.fullIri(ontologyDepartment.get("iri").toString()));
			String s = ontologyDepartment.get("Dept_Code").toString();
			if(s.equals("-N/A-"))
			{
				row = getDeptNotInPayrollRow(ontologyDepartment);
				output.add(row);
				continue;
			}
			String ontologyDeptCode = new Integer(s).toString();
			int check = compareDepartmentToPayroll(ontologyDeptCode, payrollDepartments);
			if(check ==  DEPT_MATCH_ONTO_AND_PAYROLL)
			{
				Map<String,Object> payrollDepartment = getPayrollDepartment(ontologyDeptCode, payrollDepartments);
				row  = getMatchingDeptRow(ontologyDepartment,payrollDepartment);
				output.add(row);
				compareOntologyDivisionsWithPayroll(department,payrollDepartment.get("payr_dept_id").toString(),ontologyDepartment, payrollDepartment, check, output);
			}
			else if(check ==  DEPT_IN_ONTO_NOT_IN_PAYROLL)
			{
				row = getDeptNotInPayrollRow(ontologyDepartment);
				output.add(row);
				compareOntologyDivisionsWithPayroll(department,ontologyDeptCode,ontologyDepartment, null, check, output);
			}
		}
		
		for(Map<String, Object> payrollDepartment : payrollDepartments)
		{
			String s = payrollDepartment.get("payr_dept_id").toString();
			String payrollDeptCode = new Integer(s).toString();
			int check = compareDepartmentToOntology(payrollDeptCode, ontologyDepartments);
			if(check == DEPT_IN_PAYROLL_NOT_IN_ONTO)
			{
				Object[] row = getDeptNotInOntologyRow(payrollDepartment);
				output.add(row);
				for(Map<String,Object> payrollDivision: getDivisionsFromPayroll(payrollDeptCode))
				{
					Object divisionRow[] = getDivNotInOntologyRow(null, payrollDepartment, payrollDivision);
					output.add(divisionRow);
				}
				
			}
		}
		
		return output;
	}
	
	private void compareOntologyDivisionsWithPayroll(OWLNamedIndividual department, String departmentCode,
			Map<String,Object> ontologyDepartmentProperties,Map<String,Object> payrollDepartmentProperties,
			int deptCheck, List<Object[]> output)
	{
		 
		 List<Map<String, Object>> ontologyDivisions =  getDivisionsFromOntology(department);
		 List<Map<String, Object>> payrollDivisions = getDivisionsFromPayroll(departmentCode);
		 switch(deptCheck)
		 {
		 	case DEPT_IN_PAYROLL_NOT_IN_ONTO:
		 	{	
		 		for(Map<String, Object> payrollDivision : payrollDivisions)
		 		{
		 			Object[] row = getDivNotInOntologyRow(ontologyDepartmentProperties, payrollDepartmentProperties, payrollDivision);
		 			output.add(row);
		 		}
		 		break;
		 	}
		 	case DEPT_IN_ONTO_NOT_IN_PAYROLL:
		 	{
		 		for(Map<String, Object>  ontologyDivision :  ontologyDivisions)
		 		{
		 			Object[] row = getDivNotInPayrollRow(ontologyDepartmentProperties,  ontologyDivision);
		 			output.add(row);
		 		}
		 		break;
		 	}
		 	case DEPT_MATCH_ONTO_AND_PAYROLL:
		 	{	
		 		for(Map<String,Object> ontologyDivision : ontologyDivisions)
		 		{
		 			String ontologyDivisionCode = ontologyDivision.get("Division_Code").toString();
		 			int divisionCheck = compareDivisionToPayroll(ontologyDivisionCode, ontologyDivision, payrollDivisions);
		 			switch(divisionCheck)
 					{
 						case DIV_MATCH_ONTO_AND_PAYROLL:
 						{
 							Object[] row = getMatchingDivRow( ontologyDepartmentProperties, ontologyDivision, payrollDepartmentProperties ,getPayrollDivision(ontologyDivisionCode, payrollDivisions));
 							output.add(row);
 							break;
 						}
 						case DIV_IN_ONTO_NOT_IN_PAYROLL:
 						{
 							Object[] row = getDivNotInPayrollRow(ontologyDepartmentProperties, ontologyDivision);
 							output.add(row);
 							break;
 						}
 						default:
 							break;
 					}
		 		}
		 		for(Map<String,Object> payrollDivision : payrollDivisions)
		 		{
		 			String s = payrollDivision.get("payr_div_id").toString();
					String payrollDivCode = new Integer(s).toString();
		 			int divisionCheck = compareDivisionToOntology(payrollDivCode, payrollDivision, ontologyDivisions);
		 			if(divisionCheck == DIV_IN_PAYROLL_NOT_IN_ONTO)
		 			{
		 				Object[] row = getDivNotInOntologyRow(ontologyDepartmentProperties,payrollDepartmentProperties, payrollDivision);
		 				output.add(row);
		 			}
		 		}
		 		break;
		  	}
		 	default:
		 	{
		 		break;
		 	}
		 }
	}

	private Object[] getDeptNotInOntologyRow(
			Map<String, Object> payrollDepartment)
	{
		return new Object[]{DEPT_IN_PAYROLL_NOT_IN_ONTO,
				"-- NOT IN ONTOLOGY(PROBABLY NEED TO ADD) --",
				"-- NOT IN ONTOLOGY(PROBABLY NEED TO ADD) --",
				"-- NOT IN ONTOLOGY(PROBABLY NEED TO ADD) --",
				"-- NOT IN ONTOLOGY(PROBABLY NEED TO ADD) --",
				"-- NOT IN ONTOLOGY(PROBABLY NEED TO ADD) --",
				"-- NOT IN ONTOLOGY(PROBABLY NEED TO ADD) --",
				payrollDepartment.get("payr_dept_name").toString(),
				payrollDepartment.get("payr_dept_id").toString(),
				payrollDepartment.get("payr_active_empl").toString(),
				payrollDepartment.get("payr_refresh_date").toString(),
				"-- N/A --",
				"-- N/A --"};
	}

	private Object[]  getDeptNotInPayrollRow(
			Map<String, Object> ontologyDepartment)
	{
		return new Object[]{DEPT_IN_ONTO_NOT_IN_PAYROLL,
				ontologyDepartment.get("iri").toString(),
				ontologyDepartment.get("Name").toString(),
				ontologyDepartment.get("Dept_Code").toString(),
				"--N/A--",
				"--N/A--",
				"--N/A--",
				"-- NOT IN PAYROLL (ONTOLOGY PROBABLY OUTDATED) --",
				"-- NOT IN PAYROLL (ONTOLOGY PROBABLY OUTDATED) --",
				"-- NOT IN PAYROLL (ONTOLOGY PROBABLY OUTDATED) --",
				"-- NOT IN PAYROLL (ONTOLOGY PROBABLY OUTDATED) --",
				"-- NOT IN PAYROLL (ONTOLOGY PROBABLY OUTDATED) --",
				"-- NOT IN PAYROLL (ONTOLOGY PROBABLY OUTDATED) --"};
	}

	private Object[] getMatchingDeptRow(
			Map<String, Object> ontologyDepartment,
			Map<String, Object> payrollDepartment)
	{
		return new Object[]{DEPT_MATCH_ONTO_AND_PAYROLL,ontologyDepartment.get("iri").toString(),
				ontologyDepartment.get("Name").toString(),
				ontologyDepartment.get("Dept_Code").toString(),
				"--N/A--",
				"--N/A--",
				"--N/A--",
				payrollDepartment.get("payr_dept_name").toString(),
				payrollDepartment.get("payr_dept_id").toString(),
				payrollDepartment.get("payr_active_empl").toString(),
				payrollDepartment.get("payr_refresh_date").toString(),
				"-- N/A --",
				"-- N/A --"};
	}

	private Object[] getDivNotInOntologyRow(Map<String, Object> ontologyDepartment, Map<String, Object> payrollDepartment,
			Map<String, Object> payrollDivision)
	{
		String iri = "-- NOT IN ONTOLOGY(PROBABLY NEED TO ADD) --";
		String name = "-- NOT IN ONTOLOGY(PROBABLY NEED TO ADD) --";
		String deptCode = "-- NOT IN ONTOLOGY(PROBABLY NEED TO ADD) --";
		if(ontologyDepartment != null)
		{
			iri = ontologyDepartment.get("iri").toString();
			name = ontologyDepartment.get("Name").toString();
			deptCode = ontologyDepartment.get("Dept_Code").toString();
		}
		return new Object[]{DIV_IN_PAYROLL_NOT_IN_ONTO,
				iri,
				name,
				deptCode,
				"-- NOT IN ONTOLOGY(PROBABLY NEED TO ADD) --",
				"-- NOT IN ONTOLOGY(PROBABLY NEED TO ADD) --",
				"-- NOT IN ONTOLOGY(PROBABLY NEED TO ADD) --",
				payrollDepartment.get("payr_dept_name").toString(),
				payrollDepartment.get("payr_dept_id").toString(),
				payrollDepartment.get("payr_active_empl").toString(),
				payrollDepartment.get("payr_refresh_date").toString(),
				payrollDivision.get("payr_div_name").toString(),
				payrollDivision.get("payr_active_empl").toString()};
	}


	private Object[]  getDivNotInPayrollRow(
			Map<String, Object> ontologyDepartment, Map<String, Object> ontologyDivision)
	{
		return new Object[]{DIV_IN_ONTO_NOT_IN_PAYROLL,ontologyDepartment.get("iri").toString(),
				ontologyDepartment.get("Name").toString(),
				ontologyDepartment.get("Dept_Code").toString(),
				ontologyDivision.get("iri").toString(),
				ontologyDivision.get("Name").toString(),
				ontologyDivision.get("Division_Code").toString(),
				"-- NOT IN PAYROLL (ONTOLOGY PROBABLY OUTDATED) --",
				"-- NOT IN PAYROLL (ONTOLOGY PROBABLY OUTDATED) --",
				"-- NOT IN PAYROLL (ONTOLOGY PROBABLY OUTDATED) --",
				"-- NOT IN PAYROLL (ONTOLOGY PROBABLY OUTDATED) --",
				"-- NOT IN PAYROLL (ONTOLOGY PROBABLY OUTDATED) --",
				"-- NOT IN PAYROLL (ONTOLOGY PROBABLY OUTDATED) --"};
	}

	private Object[] getMatchingDivRow(
			Map<String, Object> ontologyDepartment,
			Map<String, Object> ontologyDivision,
			Map<String, Object> payrollDepartment,
			Map<String, Object> payrollDivision)
	{
		return new Object[]{DIV_MATCH_ONTO_AND_PAYROLL,ontologyDepartment.get("iri").toString(),
				ontologyDepartment.get("Name").toString(),
				ontologyDepartment.get("Dept_Code").toString(),
				ontologyDivision.get("iri").toString(),
				ontologyDivision.get("Name").toString(),
				ontologyDivision.get("Division_Code").toString(),
				payrollDepartment.get("payr_dept_name").toString(),
				payrollDepartment.get("payr_dept_id").toString(),
				payrollDepartment.get("payr_active_empl").toString(),
				payrollDepartment.get("payr_refresh_date").toString(),
				payrollDivision.get("payr_div_name").toString(),
				payrollDivision.get("payr_active_empl").toString()};
	}

	private void printRowToConsole(Object[] row)
	{
		for(Object v : row)
		{
			System.out.print(v.toString());
			System.out.print("\t");
		}
		System.out.println();
		
	}

	private void printHeaderToConsole()
	{
		String[] columns = getHeader();
		for(String column : columns)
		{
			System.out.print(column);
			System.out.print("\t");
		}
		System.out.println();
	}

	public String[] getHeader()
	{
		String[] columns = new String[]{
										"Consistency Status",
										"Ontology Department Fragment",
										"Ontology Department Name",
										"Ontology Department Code",
										"Ontology Division Fragment",
										"Ontology Division Name",
										"Ontology Division Code",
										"Payroll Department Name",
										"Payroll Department Code",
										"Payroll Department Active Employee Count",
										"Payroll Load Date",
										"Payroll Division Name",
										"Payroll Division Active Employee Count"};
		return columns;
	}

	private Set<OWLAxiom> createDepartmentAxioms(OWLNamedIndividual departmentIndividual,  Map<String, Object> deptProps)
	{
		Set<OWLAxiom> departmentAxioms = new HashSet<OWLAxiom>();
		OWLDataFactory factory = OWL.dataFactory();
		OWLAxiom label = factory.getOWLAnnotationAssertionAxiom(
				departmentIndividual.getIRI(),
				factory.getOWLAnnotation(
						factory.getRDFSLabel()
						,factory.getOWLLiteral( deptProps.get("payr_dept_name").toString())));
		OWLAxiom deptProperty = factory.getOWLDataPropertyAssertionAxiom(Dept_Code, departmentIndividual, Integer.parseInt(deptProps.get("payr_dept_id").toString()));
		OWLAxiom nameProperty = factory.getOWLDataPropertyAssertionAxiom(Name, departmentIndividual, deptProps.get("payr_dept_name").toString());
		OWLAxiom parentAgency = factory.getOWLObjectPropertyAssertionAxiom(hasParentAgency, departmentIndividual, MiamiDadeCounty);
		departmentAxioms.add(label);
		departmentAxioms.add(deptProperty);
		departmentAxioms.add(nameProperty);
		departmentAxioms.add(parentAgency);
		return departmentAxioms;
	}
	
	private Set<OWLAxiom> createDivisionAxioms(OWLNamedIndividual departmentIndividual, OWLNamedIndividual divisionIndividual, Map<String, Object> divProps)
	{
		Set<OWLAxiom> divisionAxioms = new HashSet<OWLAxiom>();
		OWLDataFactory factory = OWL.dataFactory();
		OWLAxiom hasDivisionProperty =  factory.getOWLObjectPropertyAssertionAxiom(hasDivision, departmentIndividual, divisionIndividual);
		OWLAxiom divisionsProperty =  factory.getOWLObjectPropertyAssertionAxiom(Divisions, departmentIndividual, divisionIndividual);
		//OWLAxiom 
		
		divisionAxioms.add(hasDivisionProperty);
		divisionAxioms.add(divisionsProperty);
		return divisionAxioms;
	}
	
	/**
	 * Gets a list 'Active' departments from payroll
	 * An active department is determined by whether or not there 
	 * are employees within the department with an 'A%'
	 * status. A department with no employees is probably not 
	 * a very productive one and likely doesn't exist;
	 * the humor because there is no definitive way to determine 
	 * that a department in payroll is up to date and reflective 
	 * of the current organizational structure.
	 * 
	 * @return a List of departments with is properties as a Map.
	 */
	public List<Map<String, Object>> getDepartmentsFromPayroll()
	{
		List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
		Connection conn = null;
		PreparedStatement stmt = null;
		ResultSet rs = null;
		String query = "select distinct payr_dept_id, payr_dept_name, payr_refresh_date, b.payr_active_empl from PAYROLL_DEPT_REFRESH a, (select department, count(department) payr_active_empl  from UserList where status like 'A%' group by department having  count(department) > 0) b where a.payr_dept_id = b.department";
		try
		{
			conn = bluebookProvider.resolve().getConnection();
			stmt = conn.prepareStatement(query);
			rs = stmt.executeQuery();
			ResultSetMetaData meta = rs.getMetaData();			
			while (rs.next())
			{
				Map<String,Object> u = new HashMap<String,Object>();
				for(int i = 1; i <= meta.getColumnCount(); i++)
				{
					if(meta.getColumnType(i) == Types.TIMESTAMP )
						u.put(meta.getColumnName(i), rs.getTimestamp(i));
					else if(meta.getColumnType(i) == Types.NUMERIC || meta.getColumnType(i) == Types.INTEGER  )
						u.put(meta.getColumnName(i), rs.getLong(i));
					else
						u.put(meta.getColumnName(i), rs.getString(i));
				}
				result.add(u);
			}						
		}
		catch (SQLException e)
		{
			e.printStackTrace();
		}
		finally
		{
			try{rs.close();}catch(Exception e){}
			try{stmt.close();}catch(Exception e){}
			try{conn.close();}catch(Exception e){}
		}
		return result;
	}
	
	public List<Map<String, Object>> getDivisionsFromPayroll(String departmentCode)
	{
		List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
		Connection conn = null;
		PreparedStatement stmt = null;
		ResultSet rs = null;
		String query = "select distinct payr_div_id, payr_div_name, payr_active_empl from PAYROLL_DEPT_REFRESH a, (select department, division, count(division) payr_active_empl  from UserList where status like 'A%' group by department, division having  count(division) > 0 ) b where payr_dept_id = ? and a.payr_dept_id = b.department and a.payr_div_id = b.division";
		try
		{
			conn = bluebookProvider.resolve().getConnection();
			stmt = conn.prepareStatement(query);
			stmt.setString(1, departmentCode);
			rs = stmt.executeQuery();
			ResultSetMetaData meta = rs.getMetaData();			
			while (rs.next())
			{
				Map<String,Object> u = new HashMap<String,Object>();
				for(int i = 1; i <= meta.getColumnCount(); i++)
				{
					if(meta.getColumnType(i) == Types.TIMESTAMP )
						u.put(meta.getColumnName(i), rs.getTimestamp(i));
					else if(meta.getColumnType(i) == Types.NUMERIC )
						u.put(meta.getColumnName(i), rs.getLong(i));
					else
						u.put(meta.getColumnName(i), rs.getString(i));
				}
				result.add(u);
			}						
		}
		catch (SQLException e)
		{
			e.printStackTrace();
		}
		finally
		{
			try{rs.close();}catch(Exception e){}
			try{stmt.close();}catch(Exception e){}
			try{conn.close();}catch(Exception e){}
		}
		return result;
	}
	
	public List<Map<String,Object>> getDepartmentsFromOntology()
	{
			List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();	
			OWL.reasoner();
			Set<OWLNamedIndividual> rs = OWL.queryIndividuals("Department_County");
			
			for (OWLNamedIndividual ind : rs)
			{
				Map<String,Object> u = new HashMap<String,Object>();
				OWLLiteral deptCode = OWL.dataProperty(ind, "Dept_Code");
				u.put("iri", ind.getIRI().getFragment());
				OWLLiteral name = OWL.dataProperty(ind, "Name");
				if(name == null)
					u.put("Name","-N/A-");
				else
					u.put("Name",name.getLiteral());
				if(deptCode != null)
					u.put("Dept_Code", deptCode.getLiteral());
				else 
					u.put("Dept_Code", "-N/A-");
				result.add(u);
			}
			Collections.sort(result, new java.util.Comparator<Map<String, Object>>()
			{ 	@Override
				public int compare(Map<String, Object> o1, Map<String, Object> o2) {
				Double d1 = 0.0;
				Double d2 = 0.0;
				String s1 = o1.get("Dept_Code").toString();
				String s2 = o2.get("Dept_Code").toString();
				try
				{
					
					d1 = Double.parseDouble(s1);
					d2 = Double.parseDouble(s2);
					
				}catch(NumberFormatException nfe )
				{
					System.out.println("Number format exception when comparing Dept_Code " + s1 + " = " + s2);
					return s1.compareTo(s2);
				}
					return d1.compareTo(d2);
				}
			});
			return result;
	}
	
	public List<Map<String,Object>> getDivisionsFromOntology(OWLNamedIndividual department)
	{
			List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();	
			Set<OWLNamedIndividual> rs = OWL.objectProperties(department, Divisions.getIRI().getFragment());
			for (OWLNamedIndividual ind : rs)
			{
				Map<String,Object> u = new HashMap<String,Object>();
				OWLLiteral divisionCode = OWL.dataProperty(ind, "Division_Code");
				u.put("iri", ind.getIRI().getFragment());
				OWLLiteral name = OWL.dataProperty(ind, "Name");
				if(name == null)
					u.put("Name","-N/A-");
				else
					u.put("Name",name.getLiteral());
				if(divisionCode == null)
					u.put("Division_Code","-N/A-");
				else
					u.put("Division_Code",divisionCode.getLiteral());
				result.add(u);
			}
			Collections.sort(result, new java.util.Comparator<Map<String, Object>>()
					{ 	@Override
						public int compare(Map<String, Object> o1, Map<String, Object> o2) {
						Double d1 = 0.0;
						Double d2 = 0.0;
						String s1 = o1.get("Division_Code").toString();
						String s2 = o2.get("Division_Code").toString();
						try
						{
							
							d1 = Double.parseDouble(s1);
							d2 = Double.parseDouble(s2);
							
						}catch(NumberFormatException nfe )
						{
							System.out.println("Number format exception when comparing Division_Code " + s1 + " = " + s2);
							return s1.compareTo(s2);
						}
							return d1.compareTo(d2);
						}
					});
			return result;
	}
	
	private static DBUserProvider getBlueBookProvider()
	{
		DBUserProvider provider = new DBUserProvider();
		OWLNamedIndividual info = Refs.configSet.resolve().get("BluebookConfig");
		OWLNamedIndividual dbType = OWL.objectProperty(info, "hasDatabaseType");
		String dataSourceClassName = OWL.dataProperty(dbType, "hasDataSourceClassName")
				.getLiteral();
		String url = OWL.dataProperty(info, "hasUrl").getLiteral();
		String username = OWL.dataProperty(info, "hasUsername").getLiteral();
		String password = OWL.dataProperty(info, "hasPassword").getLiteral();
		provider.setDataSourceClassName(dataSourceClassName);
		provider.setUrl(url);
		provider.setUser(username);
		provider.setPwd(password);
		provider.setIdColumn("UserID");
		provider.setTable("UserList");
		return provider;
	}
}
