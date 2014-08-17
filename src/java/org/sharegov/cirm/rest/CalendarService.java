package org.sharegov.cirm.rest;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;

import mjson.Json;

import org.sharegov.cirm.AutoConfigurable;
import org.sharegov.cirm.OWL;
import org.sharegov.cirm.Refs;
import org.sharegov.cirm.utils.GenUtils;

/**
 * 
 * Encapsulates business calendar functions( i.e., the calculating of business
 * days between two dates or adding of business days to a date, observed
 * holidays, fiscal start and end. )
 * 
 * @author SABBAS
 */
@Path("calendar")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class CalendarService extends RestService implements AutoConfigurable
{
	public static final String DEFAULT_FISCAL_START = "1 January";
	public static final int SECONDS_IN_DAY = 86400;

	private Json config = Json.object();

	public CalendarService()
	{
		autoConfigure(Refs.owlJsonCache.resolve()
				.individual(OWL.fullIri("CalendarService")).resolve());
	}
	
	/**
	 * Returns the next business day taking into consideration
	 * any holidays that have been ontology configured and 
	 * and weekends (SAT + SUN) are considered non work days.
	 * 
	 * @param start
	 * @param daysToAdd
	 * @return A json structure containing the next business day.
	 */
	@GET
	@Path("/nextBusinessDay")
	@Produces(MediaType.APPLICATION_JSON)
	public Json nextBusinessDay(
			@QueryParam(value = "startDate") String startDate,
			@QueryParam(value = "daysToAdd") int daysToAdd)
	{
		Date result = null;
		int secondsToAdd = (int) (SECONDS_IN_DAY * daysToAdd);
		Calendar c = Calendar.getInstance();
		//put the holidays as strings in a set for matching.
		Set<String> holidays = getHolidaysAsSet();
		c.setTime(OWL.parseDate(startDate));
		int diff = secondsToAdd % SECONDS_IN_DAY;
		for (int workSeconds = 0; workSeconds < secondsToAdd - diff;)
		{
			c.add(Calendar.SECOND, SECONDS_IN_DAY);
			int dayOfWeek = c.get(Calendar.DAY_OF_WEEK);
			if (!(dayOfWeek == Calendar.SATURDAY || dayOfWeek == Calendar.SUNDAY))
			{
				String dateAsString = OWL.dateLiteral(c.getTime()).getLiteral();
				if (!holidays.contains(dateAsString))
					workSeconds = workSeconds + SECONDS_IN_DAY;
			}

		}
		c.add(Calendar.SECOND, diff);
		holidays.clear();
		result = c.getTime();
		return GenUtils.ok().set("nextBusinessDay",
				OWL.toJSON(OWL.dateLiteral(result)));
	}
	
	@GET
	@Path("/nextBusinessDayExcludeHolidays")
	@Produces(MediaType.APPLICATION_JSON)
	public Json nextBusinessDayExcludeHolidays(
			@QueryParam(value = "startDate") String startDate,
			@QueryParam(value = "daysToAdd") int daysToAdd)
	{
		Date result = null;
		int secondsToAdd = (int) (SECONDS_IN_DAY * daysToAdd);
		Calendar c = Calendar.getInstance();
		//put the holidays as strings in a set for matching.
	
		c.setTime(OWL.parseDate(startDate));
		int diff = secondsToAdd % SECONDS_IN_DAY;
		for (int workSeconds = 0; workSeconds < secondsToAdd - diff;)
		{
			c.add(Calendar.SECOND, SECONDS_IN_DAY);
			int dayOfWeek = c.get(Calendar.DAY_OF_WEEK);
			if (!(dayOfWeek == Calendar.SATURDAY || dayOfWeek == Calendar.SUNDAY))
			{
				workSeconds = workSeconds + SECONDS_IN_DAY;
			}

		}
		c.add(Calendar.SECOND, diff);
		result = c.getTime();
		return GenUtils.ok().set("nextBusinessDay",
				OWL.toJSON(OWL.dateLiteral(result)));
	}
	
	@GET
	@Path("/nextBusinessDayExcludeWeekends")
	@Produces(MediaType.APPLICATION_JSON)
	public Json nextBusinessDayExcludeWeekends(
			@QueryParam(value = "startDate") String startDate,
			@QueryParam(value = "daysToAdd") int daysToAdd)
	{
		Date result = null;
		int secondsToAdd = (int) (SECONDS_IN_DAY * daysToAdd);
		Calendar c = Calendar.getInstance();
		//put the holidays as strings in a set for matching.
		Set<String> holidays = getHolidaysAsSet();
		c.setTime(OWL.parseDate(startDate));
		int diff = secondsToAdd % SECONDS_IN_DAY;
		for (int workSeconds = 0; workSeconds < secondsToAdd - diff;)
		{
			c.add(Calendar.SECOND, SECONDS_IN_DAY);
			String dateAsString = OWL.dateLiteral(c.getTime()).getLiteral();
			if (!holidays.contains(dateAsString))
			{
				workSeconds = workSeconds + SECONDS_IN_DAY;
			}
		}
		c.add(Calendar.SECOND, diff);
		holidays.clear();
		result = c.getTime();
		return GenUtils.ok().set("nextBusinessDay",
				OWL.toJSON(OWL.dateLiteral(result)));
	}

	@GET
	@Path("/nextBusinessDayExcludeHolidaysAndWeekends")
	@Produces(MediaType.APPLICATION_JSON)
	public Json nextBusinessDayExcludeHolidaysAndWeekends(
			@QueryParam(value = "startDate") String startDate,
			@QueryParam(value = "daysToAdd") int daysToAdd)
	{
		Date result = null;
		int secondsToAdd = (int) (SECONDS_IN_DAY * daysToAdd);
		Calendar c = Calendar.getInstance();
		c.setTime(OWL.parseDate(startDate));
		int diff = secondsToAdd % SECONDS_IN_DAY;
		for (int workSeconds = 0; workSeconds < secondsToAdd - diff;)
		{
			c.add(Calendar.SECOND, SECONDS_IN_DAY);
			workSeconds = workSeconds + SECONDS_IN_DAY;
		}
		c.add(Calendar.SECOND, diff);

		result = c.getTime();
		return GenUtils.ok().set("nextBusinessDay",
				OWL.toJSON(OWL.dateLiteral(result)));
	}
	
	/**
	 * Calculates the days between two dates considering holidays and
	 * weekends as non-working days.
	 * 
	 * @param fromDate iso8601 from date
	 * @param toDate iso8601 to dates
	 * @return a Json structure containing a daysBetween property as an integer count.
	 */
	@GET
	@Path("/daysBetween")
	@Produces(MediaType.APPLICATION_JSON)
	public Json daysBetween(
			@QueryParam(value = "fromDate") String fromDate,
			@QueryParam(value = "toDate") String toDate)
	{

		try
		{
			int result = 0;
			Date from = OWL.parseDate(fromDate);
			Date to = OWL.parseDate(toDate);
			if((to.getTime() - from.getTime()) < 0)
			{ 
				return GenUtils.ko("Parameter toDate must be greater than or equal to fromDate");
			}
			Calendar c = Calendar.getInstance();
			Set<String> holidays = getHolidaysAsSet();
			c.setTime(from);
			while(!OWL.dateLiteral(to)
					.equals(OWL.dateLiteral(c.getTime())))
			{
				int dayOfWeek = c.get(Calendar.DAY_OF_WEEK);
				if (!(dayOfWeek == Calendar.SATURDAY || dayOfWeek == Calendar.SUNDAY))
				{
					String dateAsString = OWL.dateLiteral(c.getTime()).getLiteral();
					if (!holidays.contains(dateAsString))
					{
						result = result + 1;
					}
				}
				c.add(Calendar.SECOND, SECONDS_IN_DAY);
			}
			return GenUtils.ok().set("daysBetween",
					result);
		}catch(Throwable t)
		{
			return GenUtils.ko(t);
		}
	}

	@GET
	@Path("/daysBetweenExcludeHolidays")
	@Produces(MediaType.APPLICATION_JSON)
	public Json daysBetweenExcludeHolidays(
			@QueryParam(value = "fromDate") String fromDate,
			@QueryParam(value = "toDate") String toDate)
	{

		try
		{
			int result = 0;
			Date from = OWL.parseDate(fromDate);
			Date to = OWL.parseDate(toDate);
			if((to.getTime() - from.getTime()) < 0)
			{ 
				return GenUtils.ko("Parameter toDate must be greater than or equal to fromDate");
			}
			Calendar c = Calendar.getInstance();
			c.setTime(from);
			while(!OWL.dateLiteral(to)
					.equals(OWL.dateLiteral(c.getTime())))
			{
				int dayOfWeek = c.get(Calendar.DAY_OF_WEEK);
				if (!(dayOfWeek == Calendar.SATURDAY || dayOfWeek == Calendar.SUNDAY))
				{
					result = result + 1;
				}
				c.add(Calendar.SECOND, SECONDS_IN_DAY);
			}
			return GenUtils.ok().set("daysBetween",
					result);
		}catch(Throwable t)
		{
			return GenUtils.ko(t);
		}
	}

	@GET
	@Path("/daysBetweenExcludeHolidaysAndWeekends")
	@Produces(MediaType.APPLICATION_JSON)
	public Json daysBetweenExcludeHolidaysAndWeekends(
			@QueryParam(value = "fromDate") String fromDate,
			@QueryParam(value = "toDate") String toDate)
	{

		try
		{
			int result = 0;
			Date from = OWL.parseDate(fromDate);
			Date to = OWL.parseDate(toDate);
			if((to.getTime() - from.getTime()) < 0)
			{ 
				return GenUtils.ko("Parameter toDate must be greater than or equal to fromDate");
			}
			Calendar c = Calendar.getInstance();
			c.setTime(from);
			while(!OWL.dateLiteral(to)
					.equals(OWL.dateLiteral(c.getTime())))
			{
				result = result + 1;
				c.add(Calendar.SECOND, SECONDS_IN_DAY);
			}
			return GenUtils.ok().set("daysBetween",
					result);
		}catch(Throwable t)
		{
			return GenUtils.ko(t);
		}
	}
	
	/**
	 * Returns the ontology configuration of the start of a fiscal
	 * year. Format is 'dd MMMMM' where dd is the day in month
	 * and MMMMM is the full month name. 
	 * @return
	 */
	@GET
	@Path("/fiscalStart")
	@Produces(MediaType.APPLICATION_JSON)
	public Json fiscalStart()
	{
		return GenUtils.ok().set("hasFiscalYearStart",
				config.at("hasFiscalYearStart", DEFAULT_FISCAL_START));

	}

	/**
	 * Determines the start and end of the current fiscal year.
	 * 
	 * @return A json structure containing the fiscalStart date and
	 *         currentFiscalEnd date based on the currentTime given by
	 *         operationsService.
	 */
	@GET
	@Path("/currentFiscalYear")
	@Produces(MediaType.APPLICATION_JSON)
	public Json currentFiscalYear()
	{
		Json result = null;
		try
		{
			long opTime = new OperationService().getTime().at("time").asLong();
			result = getFiscalYear(opTime);
		} catch (Throwable t)
		{
			result = GenUtils.ko(t);
		}
		return result;
	}
	
	@Override
	public void autoConfigure(Json config)
	{
		if (config == null)
		{
			throw new IllegalArgumentException(
					"configuration of calendar service cannot be null");
		}
		this.config = config;
	}

	public Date getFiscalYearStartDate(int year)
	{
		try
		{

			SimpleDateFormat myDateFormat = new SimpleDateFormat(
					"dd MMMMM yyyy");
			String fiscalStartDate = config.at("hasFiscalYearStart",
					DEFAULT_FISCAL_START).asString()
					+ " " + year;
			return myDateFormat.parse(fiscalStartDate);
		}
		catch (ParseException ex)
		{
			throw new RuntimeException(ex);
		}
	}
	
	/**
	 * For a given date calculate the fiscalStart and
	 * fiscalEnd dates based on the fiscalYearStart configuration
	 * of the CalendarService.
	 * 
	 * @param forDate
	 * @return A json structure containing the fiscalStart and fiscalEnd date
	 */
	public Json getFiscalYear(long forDate)
	{
		Calendar opCalendar = Calendar.getInstance();
		opCalendar.setTime(new Date(forDate));
		int opYear = opCalendar.get(Calendar.YEAR);
		int dayInCurrentYear = opCalendar.get(Calendar.DAY_OF_YEAR);
		Date fiscalStartDate = getFiscalYearStartDate(opYear);
		opCalendar.setTime(fiscalStartDate);
		int fiscalDayInCurrentYear = opCalendar.get(Calendar.DAY_OF_YEAR);
		if (dayInCurrentYear < fiscalDayInCurrentYear)
		{
			opCalendar.add(Calendar.YEAR, -1);
			fiscalStartDate = opCalendar.getTime();
		}
		opCalendar.add(Calendar.YEAR, 1);
		opCalendar.add(Calendar.DAY_OF_MONTH, -1);
		Date fiscalEndDate = opCalendar.getTime();
		return GenUtils
				.ok()
				.set("fiscalStart",
						OWL.toJSON(OWL.dateLiteral(fiscalStartDate)))
				.set("fiscalEnd", OWL.toJSON(OWL.dateLiteral(fiscalEndDate)));
	}

	private Set<String> getHolidaysAsSet()
	{
		Set<String> result = new HashSet<String>();
		for(Json holiday : config.at("hasHolidays",Json.array()).asJsonList())
		{
			if(holiday.has("hasDate"))
			{
				if(holiday.at("hasDate").isArray())
				{
					for(Json holidayDate : holiday.at("hasDate").asJsonList())
					{
						result.add(holidayDate.asString());
					}
				}else
				{
					result.add(holiday.at("hasDate").asString());
				}
			}
		}
		return result;
	}
}