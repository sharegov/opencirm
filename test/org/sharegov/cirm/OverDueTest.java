package org.sharegov.cirm;

import java.text.DateFormat;
import java.text.ParseException;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

import org.semanticweb.owlapi.model.OWLLiteral;

public class OverDueTest {

	public static Date addDaysToDate(Date start, float days, boolean useWorkWeek)
	{
		Date result = null;
		int seconds = (int) (86400 * days);
		Calendar c = Calendar.getInstance();
		Set<OWLLiteral> holidays = new HashSet<OWLLiteral>();
		
//		for(OWLNamedIndividual holiday: reasoner()
//				.getInstances(owlClass("Observed_County_Holiday"), false).getFlattened())
//		{
//				for(OWLLiteral date: reasoner().getDataPropertyValues(holiday, dataProperty("hasDate")))
//				{
//					if (date != null)
//						holidays.add(date);
//				}
//		}
		c.setTime(start);
		if (!useWorkWeek)
		{
			c.add(Calendar.SECOND, seconds);
			result = c.getTime();
		}
		else
		{
			int diff = seconds % 86400; 
			//Find start workday
			while (!isWorkDay(c)) {
				c.add(Calendar.SECOND, 86400);
			}
			int workSeconds = 0;
			while (workSeconds < seconds-diff) {
				c.add(Calendar.SECOND, 86400);
				if (isWorkDay(c)) {
					workSeconds = workSeconds + 86400;
				}
			}
			c.add(Calendar.SECOND, diff);
			result = c.getTime();
		}
		return result;
	}
	
	public static boolean isWorkDay(Calendar c) {
		boolean result;
		int dow = c.get(Calendar.DAY_OF_WEEK);
		result = (dow != Calendar.SATURDAY && dow != Calendar.SUNDAY);
		if (result) {
			//OWLLiteral literal = dateLiteral(c.getTime());
			//result = !holidays.contains(literal))
		}
		return result;
	}

	public static void main(String[] args)  throws ParseException {
		Date fri = DateFormat.getDateInstance().parse("Jun 2, 2017"); //Friday
		Date sat = DateFormat.getDateInstance().parse("Jun 3, 2017");
		Date sun = DateFormat.getDateInstance().parse("Jun 4, 2017");
		Date mon = DateFormat.getDateInstance().parse("Jun 5, 2017"); //Monday	
		//add 1 with workday5
		Date expectMo = addDaysToDate(fri, 1, true);
		Date expectTue = addDaysToDate(sat, 1, true);
		Date expectTue2 = addDaysToDate(sun, 1, true);
		Date expectTue3 = addDaysToDate(mon, 1, true);
		System.out.println(expectMo);
		System.out.println(expectTue);
		System.out.println(expectTue2);
		System.out.println(expectTue3);
	}

}
