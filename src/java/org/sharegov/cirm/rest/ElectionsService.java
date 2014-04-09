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
package org.sharegov.cirm.rest;

import static mjson.Json.array;
import static mjson.Json.object;
import static org.sharegov.cirm.utils.GenUtils.ko;
import static org.sharegov.cirm.utils.GenUtils.ok;
import static org.sharegov.cirm.utils.GenUtils.trim;
import static org.sharegov.cirm.utils.GenUtils.formatDate;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.Map.Entry;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;

import mjson.Json;

import org.sharegov.cirm.OWL;
import org.sharegov.cirm.rdb.GenericStore;

@Path("elections")
@Produces("application/json")
public class ElectionsService extends RestService
{
	@GET
	@Path("/query")
	@Produces("application/json")
	public Json getElectionInfo(@QueryParam("arg") String search,
			@QueryParam("flag") String flag)
	{

		Json searchCriteria = Json.read(search);
		StringBuilder criteria = new StringBuilder(" where ");
		for (Entry<String, Object> entry : searchCriteria.asMap().entrySet())
		{
			if (entry.getValue() == null
					|| entry.getValue().toString().isEmpty()
					|| entry.getValue().toString().equals("N/A"))
				continue;
			if (flag.equals("true") && entry.getKey().equals("NAME"))
				criteria.append("v." + entry.getKey());
			else
				criteria.append(entry.getKey());
			criteria.append(" = ");
			if (entry.getKey().equals("BIRTHDATE"))
				criteria.append("'"
						+ new SimpleDateFormat("MM/dd/yyyy").format(
								(OWL.parseDate(entry.getValue().toString())
										.getTime())).toString() + "'");
			else
				criteria.append("'" + entry.getValue().toString() + "'");
			criteria.append(" and ");
		}
		int i = criteria.toString().lastIndexOf("and");
		criteria.replace(i, i + 3, "");

		Connection conn = null;
		ResultSet rs = null;
		Json result = object();

		try
		{
			conn = GenericStore.getElectionInstance()
					.getElectionSQLPooledConnection();
			if (flag.equals("false"))
			{
				int count = multipleElectionRecords(conn, criteria.toString());
				if (count == 0)
					return ok().set("result", result.set("records", array()));
				else if (count == 1)
					fetchIndividualElectionInfo(criteria, conn, rs, result);
				else if (count > 1 && count < 200)
				{
					Json recordsArray = array();
					StringBuilder query = new StringBuilder();
					query.append("select FVRSIDNUM, NAME, BIRTHDATE, SSNUM, STATUS from VOTER ");
					query.append(criteria.toString());
					query.append(" order by NAME");
					rs = conn.createStatement().executeQuery(query.toString());
					while (rs.next())
					{
						Json x = object();
						x.set("FVRSIDNUM", trim(rs.getString(1)));
						x.set("NAME", trim(rs.getString(2)));
						x.set("BIRTHDATE", formatDate(rs.getTimestamp(3), "MM/dd/yyyy"));
						x.set("SSNUM", trim(rs.getString(4)));
						x.set("STATUS", trim(rs.getString(5)));
						recordsArray.add(x);
					}
					result.set("records", recordsArray);
				}
				else if (count > 200)
					result.set("totalCount", count);
			}
			else if (flag.equals("true"))
				fetchIndividualElectionInfo(criteria, conn, rs, result);
			return ok().set("result", result);
		}
		catch (Exception e)
		{
			e.printStackTrace();
			return ko(e.getMessage());
		}
		finally
		{
			if (rs != null)
				try
				{
					rs.close();
				}
				catch (Throwable e)
				{
					e.printStackTrace();
				}
			if (conn != null)
				try
				{
					conn.close();
				}
				catch (Throwable e)
				{
					e.printStackTrace();
				}
		}
	}

	private int multipleElectionRecords(Connection conn, String criteria)
	{
		ResultSet rs = null;
		int count = 0;
		try
		{
			StringBuilder query = new StringBuilder();
			query.append("select count(Name) from VOTER ");
			query.append(criteria);
			rs = conn.createStatement().executeQuery(query.toString());
			while (rs.next())
			{
				count = rs.getInt(1);
			}
			return count;
		}
		catch (Exception e)
		{
			e.printStackTrace();
			return count;
		}
		finally
		{
			if (rs != null)
				try
				{
					rs.close();
				}
				catch (SQLException e)
				{
					e.printStackTrace();
				}
		}
	}

	private void fetchIndividualElectionInfo(StringBuilder criteria,
			Connection conn, ResultSet rs, Json result)
	{
		try
		{
			StringBuilder query = buildVoterRegistrationDetailsQuery(criteria);
			rs = conn.createStatement().executeQuery(query.toString());
			Json x = object();
			while (rs.next())
			{
				buildVoterRegistrationDetailsResultsJson(rs, x);
			}
			rs = null;
			if (x.has("Certificate"))
			{
				query = buildVoterHistoryQuery(x.at("Certificate").asString());
				rs = conn.createStatement().executeQuery(query.toString());
			}
			if (rs != null)
			{
				Json historyArray = buildVoterHistoryResultsJsonArray(rs);
				result.set("record", object().set("registrationDetails", x)
						.set("voterHistory", historyArray));
			}
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
		finally
		{
			if (rs != null)
				try
				{
					rs.close();
				}
				catch (SQLException e)
				{
					e.printStackTrace();
				}
		}
	}

	private Json buildVoterHistoryResultsJsonArray(ResultSet rs)
	{
		Json historyArray = array();
		try
		{
			while (rs.next())
			{
				Json history = object();
				history.set("ElectionNumber", rs.getInt(1));
				history.set("ElectionDate", formatDate(rs.getTimestamp(2), "MM/dd/yyyy"));
				history.set("ElectionDescription", trim(rs.getString(3)));
				history.set("ElectionHowVoted", trim(rs.getString(4)));
				history.set("ElectionParty", trim(rs.getString(5)));
				historyArray.add(history);
			}
			return historyArray;
		}
		catch (Exception e)
		{
			e.printStackTrace();
			return historyArray;
		}
	}

	private StringBuilder buildVoterRegistrationDetailsQuery(
			StringBuilder criteria)
	{
		StringBuilder query = new StringBuilder();
		query.append("select ");
		query.append("v.Name, v.BIRTHDATE, v.FVRSIDNUM, v.SSNUM, v.DRIVERLIC, ");
		query.append("v.ADDR_NUM, v.ADDR_FRAC, v.ADDR_DIR, v.ADDR_STR, v.ADDR_TYPE, v.ADDR_OTHER, ");
		query.append("v.ADDR_ZIP, v.ADDR_ZIP4, v.STAT_ADR, ");
		query.append("v.RESIDENCE_ADDRESS, c.NAME as ADDR_CITY, v.PRECINCT, v.GRP, ");
		query.append("v.CERTNUM, v.STATUS, v.sex, v.race, ");
		query.append("v.PARTY, v.REGDATE, v.LASTVOTE, p.LOCATION, p.ADDRESS ");
		query.append("from VOTER v, CITY c, VRPREC p ");
		query.append(criteria.toString());
		query.append(" and v.CITYCODE = c.CITYCODE and v.PRECINCT = p.PREC_NUM");
		return query;
	}

	private void buildVoterRegistrationDetailsResultsJson(ResultSet rs, Json x)
	{
		try
		{
			x.set("Name", trim(rs.getString(1)));
			x.set("BirthDate", formatDate(rs.getTimestamp(2), "MM/dd/yyyy"));
			x.set("FVRSIDNUM", trim(rs.getString(3)));
			x.set("SSN", trim(rs.getString(4)));
			x.set("DriverLicense", trim(rs.getString(5)));

			x.set("Address", rs.getInt(6) + " " + trim(rs.getString(7)) + " "
					+ trim(rs.getString(8)) + " " + trim(rs.getString(9)) + " "
					+ trim(rs.getString(10)) + " " + trim(rs.getString(11)));
			x.set("Zip", trim(rs.getString(12)));
			x.set("ZipExtra", trim(rs.getString(13)));
			x.set("AddressStatus", trim(rs.getString(14)));

			x.set("ResidenceAddress", trim(rs.getString(15)));
			x.set("City", trim(rs.getString(16)));
			x.set("Precinct", trim(rs.getString(17)));
			x.set("Group", trim(rs.getString(18)));

			x.set("Certificate", trim(rs.getString(19)));
			x.set("Status", trim(rs.getString(20)));
			x.set("Sex", trim(rs.getString(21)));
			x.set("Race", trim(rs.getString(22)));

			x.set("Party", trim(rs.getString(23)));
			x.set("RegistrationDate", formatDate(rs.getTimestamp(24), "MM/dd/yyyy"));
			x.set("LastVotedDate", formatDate(rs.getTimestamp(25), "MM/dd/yyyy"));
			x.set("PollLocation",
					trim(rs.getString(26)) + " " + trim(rs.getString(27)));
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}

	}

	private StringBuilder buildVoterHistoryQuery(String cert)
	{
		StringBuilder query = new StringBuilder();
		query.append("select");
		query.append(" e.ELECTION, e.ELECDATE, e.NAME, h.HOWVOTED, h.PARTYCODE");
		query.append(" from VHIST h, VRELEC e");
		query.append(" where h.CERTNUM = ").append("'" + cert + "'");
		query.append(" and h.ELECTION = e.ELECTION");
		query.append(" order by e.ELECDATE DESC");
		return query;
	}
	
}
