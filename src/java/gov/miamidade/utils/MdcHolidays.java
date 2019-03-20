package gov.miamidade.utils;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * MdcHolidays, a perpetual calendar for observed MDC holidays, including SW Garbage observed holidays.<br>
 * 
 * <br>
 * 2019: <br>
 * New Year's Day - Jan 1 (Tues)<br>
 * Martin Luther King, Jr. Day - Jan 21 (Mon)<br>
 * President's Day - Feb 18 (Mon)<br>
 * Memorial Day - May 27 (Mon)<br>
 * Independence Day - Jul 4 (Thurs)<br>
 * Labor Day - Sep 2 (Mon)<br>
 * Columbus Day - Oct 14 (Mon)<br>
 * Veterans Day - Nov 11 (Mon)<br>
 * Thanksgiving - Nov 28 (Thurs)<br>
 * Day after Thanksgiving - Nov 29 (Fri)<br>
 * Christmas Day - Dec 25 (Wed)<br>
 * <br>
 * Fully thread safe due to usage of Java 8 Time API.<br>
 * 
 * @author Thomas Hilpold
 */
public class MdcHolidays {

	/**
	 * Gets all holidays of the given year. <br>
	 * New Year might be observed in the prev year and will always be in list.<br>
	 * 
	 * @param year 4 digit year
	 * @return set of observed holidays including New Year.
	 */
	public static SortedSet<LocalDate> getMdcHolidaysObservedOf(int year) {
		TreeSet<LocalDate> allObs = new TreeSet<>();
		allObs.add(getNewYearsDayObserved1(year));
		addMdcHolidaysObservedAlwaysSameYear(allObs, year);
		return allObs;		
	}
	
	/**
	 * Gets all holidays observed in year. Checks for New Year observed in next, prev or cur year.
	 * Set may contain multiple observed New Year Holidays, all in the given year.
	 * @param year 4 digit year
	 * @return set of observed holidays including all New Year holidays observed in the year.
	 */
	public static SortedSet<LocalDate> getMdcHolidaysObservedIn(int year) {
		TreeSet<LocalDate> allObs = new TreeSet<>();
		LocalDate nyObserved = getNewYearsDayObserved1(year);
		if (nyObserved.getYear() == year) {
			allObs.add(nyObserved);
		} 
		nyObserved = getNewYearsDayObserved1(year - 1);
		if (nyObserved.getYear() == year) {
			allObs.add(nyObserved);
		}	
		nyObserved = getNewYearsDayObserved1(year + 1);
		if (nyObserved.getYear() == year) {
			allObs.add(nyObserved);
		}
		addMdcHolidaysObservedAlwaysSameYear(allObs, year);
		return allObs;		
	}
	
	private static void addMdcHolidaysObservedAlwaysSameYear(Set<LocalDate> allObs, int year) {
		allObs.add(getMartinLutherKingObserved2(year));
		allObs.add(getPresidentsDayObserved3(year));
		allObs.add(getMemorialDayObserved4(year));
		allObs.add(getIndependenceDayObserved5(year));
		allObs.add(getLaborDayObserved6(year));
		allObs.add(getColumbusDayObserved7(year));
		allObs.add(getVeteransDayObserved8(year));
		allObs.add(getThanksgivingObserved9(year));
		allObs.add(getDayAfterThanksgivingObserved10(year));
		allObs.add(getChristmasDayObserved11(year));
	}

	/**
	 * Determines if the given day is a MDC observed holiday.
	 * (Will return false for weekend days, where holidays are observed Mo or Fri,
	 * e.g. Sat 1/1 2022 (false), which is observed Fri 12/31/2021 (true))
	 * @param day
	 * @return
	 */
	public static boolean isMdcHolidayObserved(LocalDate day) {
		boolean result = getMdcHolidaysObservedIn(day.getYear()).contains(day);
		return result;
	}
	
	/**
	 *  No MDC SW Garbage Service on:
	 *
	 * Martin Luther King, Jr. Day, 
	 * Independence Day and 
	 * Christmas Day, as observed by Miami-Dade County.
	 */ 
	public static boolean isMdcSolidwasteGarbageHolidayObserved(LocalDate day) {
		boolean result = getMdcSwGarbageHolidaysObservedIn(day.getYear()).contains(day);
		return result;
	}
	
	/**
	 *  No MDC SW Garbage Service on:
	 *
	 * Martin Luther King, Jr. Day, 
	 * Independence Day and 
	 * Christmas Day, as observed by Miami-Dade County.
	 * @param year 4 digit year
	 */ 
	public static SortedSet<LocalDate> getMdcSwGarbageHolidaysObservedIn(int year) {
		TreeSet<LocalDate> allObs = new TreeSet<>();
		allObs.add(getMartinLutherKingObserved2(year));
		allObs.add(getIndependenceDayObserved5(year));
		allObs.add(getChristmasDayObserved11(year));
		return allObs;
	}
	
	
	public static LocalDate getNewYearsDay1(int year) {
		return LocalDate.of(year, 1, 1);
	}

	/**
	 * NewYearsDayObserved1
	 * Must check cur and prev for observance.
	 * @param year 4 digit year
	 * @return
	 */
	public static LocalDate getNewYearsDayObserved1(int year) {
		LocalDate base = getNewYearsDay1(year);
		LocalDate result;
		DayOfWeek dow = base.getDayOfWeek();
		switch (dow) {
		case SUNDAY: {
			result = base.plusDays(1);break;
		}
		case SATURDAY: {
			result = base.minusDays(1);break;
		}
		default:
			result = base;break;
		}
		// Warning: May be observed in prev year or cur year.
		return result;
	}

	public static LocalDate getMartinLutherKingObserved2(int year) {
		//3rd Monday in January
		//2018 min 1/15, max +6 = 1/21 2019
		LocalDate base = LocalDate.of(year, 1, 15);
		LocalDate result = getNextMonday(base);
		return result;
	}

	public static LocalDate getPresidentsDayObserved3(int year) {
		// 3rd Monday in February
		//2021 min 2/15, max +6 = 2/21 2022
		LocalDate base = LocalDate.of(year, 2, 15);
		LocalDate result = getNextMonday(base);
		return result;
	}

	public static LocalDate getMemorialDayObserved4(int year) {
		//Last Mon in Sept
		//2020 min 5/25, max +6 = 5/31 2021
		LocalDate base = LocalDate.of(year, 5, 25);
		LocalDate result = getNextMonday(base);
		return result;
	}

	public static LocalDate getIndependenceDayObserved5(int year) {
		LocalDate base = getIndependenceDay5(year);
		LocalDate result;
		DayOfWeek dow = base.getDayOfWeek();
		switch (dow) {
		case SUNDAY: {
			result = base.plusDays(1);break;
		}
		case SATURDAY: {
			result = base.minusDays(1);break;
		}
		default:
			result = base;
		}
		return result;
	}

	public static LocalDate getIndependenceDay5(int year) {
		return LocalDate.of(year, 7, 4);
	}

	public static LocalDate getLaborDayObserved6(int year) {
		//1st Mon in Sept
		//2025 min 9/1, max +6 = 9/7 2020
		LocalDate base = LocalDate.of(year, 9, 1);
		LocalDate result = getNextMonday(base);
		return result;

	}

	public static LocalDate getColumbusDayObserved7(int year) {
		//2nd Mon in Oct
		//2029 min 10/8, max +6 = 10/14
		LocalDate base = LocalDate.of(year, 10, 8);
		LocalDate result = getNextMonday(base);
		return result;
	}

	public static LocalDate getVeteransDayObserved8(int year) {
		LocalDate base = getVeteransDay8(year);
		LocalDate result;
		DayOfWeek dow = base.getDayOfWeek();
		switch (dow) {
		case SUNDAY: {
			result = base.plusDays(1);break;
		}
		case SATURDAY: {
			result = base.minusDays(1);break;
		}
		default:
			result = base;
		}
		return result;
	}

	public static LocalDate getVeteransDay8(int year) {
		return LocalDate.of(year, 11, 11);
	}

	public static LocalDate getThanksgivingObserved9(int year) {
		//4th Thu in November
		//2018 min 11/22, max +6 = 11/28
		LocalDate base = LocalDate.of(year, 11, 22);
		LocalDate result;
		DayOfWeek dow = base.getDayOfWeek();
		switch (dow) {
		case SUNDAY: {
			result = base.plusDays(4);break;
		}
		case MONDAY:{
			result = base.plusDays(3);break;
		}
		case TUESDAY: {
			result = base.plusDays(2);break;
		}
		case WEDNESDAY:{
			result = base.plusDays(1);break;
		}
		case THURSDAY:{
			result = base;break; 
		}
		case FRIDAY: {
			result = base.plusDays(6);break;
		}
		case SATURDAY: {
			result = base.plusDays(5);break;
		}
		default:
			//Error
			result = null;
		}
		return result;
	}
	
	public static LocalDate getDayAfterThanksgivingObserved10(int year) {
		return getThanksgivingObserved9(year).plusDays(1);
	}

	public static LocalDate getChristmasDayObserved11(int year) {
		LocalDate base = getChristmasDay11(year);
		LocalDate result = getSatSunObserved(base);
		return result;
	}
	
	private static LocalDate getNextMonday(LocalDate base) {
		//base must be current year but with month/day of minimum Monday set.
		LocalDate result;
		DayOfWeek dow = base.getDayOfWeek();
		switch (dow) {
		case SUNDAY: {
			result = base.plusDays(1);break;
		}
		case MONDAY:{
			result = base;break;
		}
		case TUESDAY: {
			result = base.plusDays(6);break;
		}
		case WEDNESDAY:{
			result = base.plusDays(5);break;
		}
		case THURSDAY:{
			result = base.plusDays(4);break;
		}
		case FRIDAY: {
			result = base.plusDays(3);break;
		}
		case SATURDAY: {
			result = base.plusDays(2);break;
		}
		default:
			throw new IllegalStateException("Day of Week not detected for " + base);
		}
		return result;
	}
	
	private static LocalDate getSatSunObserved(LocalDate base) {
		LocalDate result;
		DayOfWeek dow = base.getDayOfWeek();
		switch (dow) {
		case SUNDAY: {
			result = base.plusDays(1);break;
		}
		case SATURDAY: {
			result = base.minusDays(1);break;
		}
		default:
			result = base;break;
		}
		return result;
		
	}

	public static LocalDate getChristmasDay11(int year) {
		return LocalDate.of(year, 12, 25);
	}
	
	//
	// Helper methods to quickly print:
	//

	public static void printMdcHolidaysObservedOf(int year) {
		print(getMdcHolidaysObservedOf(year));
	}

	public static void printMdcHolidaysObservedIn(int year) {
		print(getMdcHolidaysObservedIn(year));
	}
	
	public static void printMdcSwGarbageHolidaysObservedIn(int year) {
		print(getMdcSwGarbageHolidaysObservedIn(year));
	}
	
	private static final DateTimeFormatter DTFUS = DateTimeFormatter.ofPattern("EEE MM/dd/yyyy");
	
	private static void print(Set<LocalDate> l) {
		int i = 0;
		for (LocalDate d :l) {
			i++;
			System.out.println(i + "\t" + d.format(DTFUS));
		}
	}
	
	/**
	 * Quick test: Prints holidays from 2018 - 2025 observed of, in and SW.
	 * @param args
	 */
	public static void main(String[] args) {
		for (int year = 2018; year < 2025; year ++) {
			System.out.println("printMdcHolidaysObservedOf " + year);
			printMdcHolidaysObservedOf(year);
			System.out.println("printMdcHolidaysObservedIn " + year);
			printMdcHolidaysObservedIn(year);
			System.out.println("printMdcSwGarbageHolidaysObservedIn " + year);
			printMdcSwGarbageHolidaysObservedIn(year);
		}
		//Test Fri 12.31.2021 observed
		LocalDate test = LocalDate.of(2021, 1, 1);
		for (int i = 0; i < 365; i++) {
			boolean isObserved = isMdcHolidayObserved(test);
			if (isObserved) {
				System.out.println(test.format(DTFUS) + " observed? : " + isObserved);
			}
			test = test.plusDays(1);
		}
	}
}
