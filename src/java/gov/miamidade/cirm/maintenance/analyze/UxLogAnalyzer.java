package gov.miamidade.cirm.maintenance.analyze;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.text.DateFormat;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * A simple log file User Experience analyzer that prints some hourly statistics and daily percentiles.<br>
 * <br>
 * Expected log format:<br>
 * <br>
 * INFO   | jvm 1    | 2016/09/27 17:23:50 | T444-END createNewKOSR 3.635 sec<br>
 * <br>
 * Usage:<br>
 * 1. Modify logMatch<br>
 * 2. Modify ANALYSIS_DAYS_HISTORY_CUTOFF_DEFAULT<br>
 * 3. Modify rotatingLogSeriesBaseFiles<br>
 * 4. Modify BAD_UX_THRESHOLD_SECS<br>
 * Run as java program.<br>
 * <br>
 * @author Thomas Hilpold
 *
 */
class UxLogAnalyzer {
	
	static final int ANALYSIS_DAYS_HISTORY_CUTOFF_DEFAULT = 2;
	
	static final boolean DBG_MATCHES = false;
	
	static final double BAD_UX_THRESHOLD_SECS = 10;	
	
	//static String logMatch = "END updateServiceCase data"; 
	//static String logMatch = "sendEmail: One Email sent to";
	static String logMatch = "END createNewKOSR";
	//static String logMatch = "DONE: SmsService: All";
	//static String logMatch = "END saveBusinessObjectOntology";
	//static String logMatch = "END DuplicateCheck"; 
	//static String logMatch = "END lookupAdvancedSearch"; 
	//static String logMatch = "END asdDispatchLookup"; 
	//static String logMatch = "End GisService getLocationInfo Call"; 
	//static String logMatch = "END populateGisDataInternal"; 
	//static String logMatch = "Start GisService getLocationInfo Call";
	//static String logMatch = "End GisService getLocationInfo Call";
	//static String logMatch = "Start GisService getLocationInfo Call";
	//static String logMatch = "LocationInfoCache cache put";
	//static String logMatch = "LocationInfoCache cache hit ";
	//static String logMatch = "LocationInfoCache cache hit, but expired ";
	//static String logMatch = "sqlsrvelecprd1";
	
	static String[] rotatingLogSeriesBaseFiles = new String[] {
			"\\\\s2030050\\cirmservices\\logs\\wrapper.log",
			"\\\\s2030051\\cirmservices\\logs\\wrapper.log",
			"\\\\s2030057\\cirmservices\\logs\\wrapper.log",
			"\\\\s2030059\\cirmservices\\logs\\wrapper.log",
			"\\\\s2030060\\cirmservices\\logs\\wrapper.log"
	};
	
	private final static DateFormat logDf = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
	private final static NumberFormat avgDf = new DecimalFormat("#0.0");

	private UxStats stats = new UxStats();
	
	public UxLogAnalyzer() {
	}
	
	void run() {
		System.out.println("***** UX Log analyzer started *****");
		System.out.println("Rotating base log files to analyze: " + rotatingLogSeriesBaseFiles.length);
		System.out.println("Line Match: " + logMatch);
		Date cutOffDate = determineCutoffDay(ANALYSIS_DAYS_HISTORY_CUTOFF_DEFAULT);
		System.out.println("Start day for analysis: " + cutOffDate);		
		for (String logFile : rotatingLogSeriesBaseFiles) {
			File cur = new File(logFile);
			analyzeRotatingLogFileSeries(cur, logMatch, cutOffDate);
		}
		stats.printHourly();
		stats.printDayPercentiles();
	}
	
	/**
	 * Determines the oldes day in history for which analysis should be conducted.
	 * e..g if analysisHistoryTargetDays = 1, yesterday 00:00:00 will be returned
	 * @param analysisHistoryTargetDays
	 * @return
	 */
	Date determineCutoffDay(int analysisHistoryTargetDays) {
		Calendar cutoff = Calendar.getInstance();
		cutoff.set(Calendar.HOUR_OF_DAY, 0);
		cutoff.set(Calendar.MINUTE, 0);
		cutoff.set(Calendar.SECOND, 0);
		cutoff.set(Calendar.MILLISECOND, 0);
		cutoff.add(Calendar.DAY_OF_MONTH, -analysisHistoryTargetDays);
		return cutoff.getTime();
	}
	
	/**
	 * Starts analysis with the base log file and finds other log files in series and goes back in time until cutOffMinDate.
	 * Log files in series with a last modified date prior to cutoff are ignored.
	 * 
	 * @param baseLogFile
	 * @param lineMatch
	 * @param cutOffMinDate
	 */
	void analyzeRotatingLogFileSeries(File baseLogFile, String lineMatch, Date cutOffMinDate) {
		int rotatingLogNumber = 0;
		boolean continueNextRotatingLog;
		do {
			continueNextRotatingLog = false;
			File curLogFile = getRotatingLogFile(baseLogFile, rotatingLogNumber);
			if (curLogFile != null) {
				Date curLogFileLastModified = new Date(curLogFile.lastModified());
				continueNextRotatingLog = (curLogFileLastModified.after(cutOffMinDate) || curLogFileLastModified.equals(cutOffMinDate));
				if (continueNextRotatingLog) {
					System.out.println("Analyzing " + curLogFile+ " (LastMod: " + logDf.format(curLogFileLastModified) + ")");
					analyzeOneRotatingLogFile(curLogFile, lineMatch, cutOffMinDate);
				} else {
					System.out.println("Log history was complete.");
				}
			} else {
				System.err.println("Log history incomplete for this series.");
				System.err.println("Rotating log " + rotatingLogNumber + " either not found or inaccessible. Increase log.");
			}
			rotatingLogNumber ++;
		} while (continueNextRotatingLog);		
	}
	
	/**
	 * Returns the log file by adding .1,.2,.3 to determine other files in log series.
	 * Also checks if the log file exists and can be read.
	 * 
	 * @param baseLogFile
	 * @param rotatingSuffixNumber
	 * @return
	 */
	File getRotatingLogFile(File baseLogFile, int rotatingSuffixNumber) {
		if (rotatingSuffixNumber == 0) {
			return baseLogFile;
		} else {
			String suffix = "." + rotatingSuffixNumber;
			String fullPath = baseLogFile.getAbsolutePath() + suffix;
			File f = new File(fullPath);
			if (f.exists() && f.canRead()) { 
				return f;
			} else {
				return null;
			}
		}
	}

	/**
	 * 
	 * @param curLogfile
	 * @param lineMatch
	 * @param cutOffMinDate
	 * @return true if all matched lines in file have dates after cutOffminDate 
	 */
	boolean analyzeOneRotatingLogFile(File curLogFile, String lineMatch, Date cutOffMinDate) {
		boolean continueWithNextFile = true;
		try (BufferedReader br = new BufferedReader(new FileReader(curLogFile))) {
		    String line;
		    while ((line = br.readLine()) != null) {
		    	Date curLineDate = analyzeLine(line, lineMatch, cutOffMinDate);
		    	if (curLineDate != null && (curLineDate.before(cutOffMinDate) && continueWithNextFile)) {
		    		//this file has a match with a date prior to cutOff
		    		//we'll still read the  whole file, but return false to indicate that the next (older)
		    		//rotating log file does not need to be processed
		    		continueWithNextFile = false;
		    	}
		    }
		    br.close();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return continueWithNextFile;
	}
	/**
	 * Analyzes a line by matching and then finding dateTime and duration for that sample.
	 * Modify and test the pattern if the log file format changes.
	 * 
	 * @param line
	 * @param lineMatch a string expected between the last | and the duration second double value.
	 * @return lineDateTime or null if not determined
	 */
	Date analyzeLine(String line, String lineMatch, Date cutoff) {
		//Pattern p = Pattern.compile("(^.+\\|.+\\|([\\d\\/\\s:]+)\\|.+" + lineMatch + "([\\s\\d.]+)sec.*)");
		Pattern p = Pattern.compile("(^.+\\|.+\\|([\\d\\/\\s:]+)\\|.+" + lineMatch + ".* ([\\s\\d.]+) sec)");
		Matcher m = p.matcher(line);
		Date lineDateTime = null;
		if (m.find()) {
			//System.out.println(line);
			String dateTimeStr = m.group(2).trim();
			String durationStr = m.group(3).trim();
			//System.out.println(dateTimeStr + " " + durationStr);
			try {
				lineDateTime = logDf.parse(dateTimeStr);
				Double durationSecs = Double.parseDouble(durationStr);
				if (lineDateTime.after(cutoff)) {
					stats.addSample(lineDateTime, lineMatch, durationSecs);
					if (DBG_MATCHES) System.out.println(line + " >> Found: " + durationSecs + " DT: " + dateTimeStr);
				}
			} catch (ParseException e) {
				System.out.println("Ignoring line: " + line);
			}
		}
		return lineDateTime;
	}
	
	/**
	 * Simple stats class keeping all durations by day and UxStatsEntries by day/hour.
	 * 
	 * @author Thomas Hilpold
	 *
	 */
	class UxStats {
		SortedMap<Date, UxStatsEntry> stats = new TreeMap<>(); 
		SortedMap<Date, List<Double>> durationsByDay = new TreeMap<>(); 
		
		void addSample(Date dateTime, String name, double durationSecs) {
			//System.out.println("" + dateTime + " " + name + " " +  durationSecs);
			Calendar c = Calendar.getInstance();
			c.setTime(dateTime);
			c.set(Calendar.MINUTE, 0);
			c.set(Calendar.SECOND, 0);
			c.set(Calendar.MILLISECOND, 0);
			UxStatsEntry e = stats.get(c.getTime());
			if (e == null) {
				e = new UxStatsEntry();
				stats.put(c.getTime(), e);
			}
			e.name = name;
			e.addSample(durationSecs);
			c.set(Calendar.HOUR_OF_DAY,0);
			List<Double> dayDurations = durationsByDay.get(c.getTime());
			if (dayDurations == null) {
				dayDurations = new ArrayList<Double>(10000);
				durationsByDay.put(c.getTime(), dayDurations);
			}
			dayDurations.add(durationSecs);
		}
		
		void printCSV() {
			System.out.println("Date               ,name,min,avg,max");
			for (Map.Entry<Date, UxStatsEntry> e : stats.entrySet()) {
				System.out.println(logDf.format(e.getKey()) 
						+ "," + e.getValue().name 
						+ "," + avgDf.format(e.getValue().minDuration)
						+ "," + avgDf.format(e.getValue().calcAverage())
						+ "," + avgDf.format(e.getValue().maxDuration)
						+ "," + avgDf.format(e.getValue().nrOfSamples)
						);
			}
		}
		
		/**
		 * Prints a tab separated table showing avg, min, max, nf of bad Ux and total number of samples per day/hour.
		 */
		void printHourly() {
			System.out.println("311Hub hourly averages/max/badUx for log lines matching: " + logMatch);
			System.out.println("Date               \tavg\tmax\tBadUx\ttotal");
			for (Map.Entry<Date, UxStatsEntry> e : stats.entrySet()) {
				System.out.println(logDf.format(e.getKey()) 
						+ "\t" + avgDf.format(e.getValue().calcAverage())
						+ "\t" + avgDf.format(e.getValue().maxDuration)
						+ "\t" + e.getValue().nrOfBadUx
						+ "\t" + e.getValue().nrOfSamples
						);
			}
		}
		
		/**
		 * Prints a tab separated table with Date, each percentile and the total number of samples that day.
		 */
		void printDayPercentiles() {
			System.out.println("311Hub percentiles in seconds for log lines matching: " + logMatch);
			System.out.println("Date            \tAvg\t10th\t20th\t30th\t40th\t50th\t60th\t70th\t80th\t90th\t95th\t99th\t100th\tNrOfSamples");
			for (Map.Entry<Date, List<Double>> e : durationsByDay.entrySet()) {
				List<Double> sortedList = e.getValue();
				Collections.sort(sortedList);
				double valuesPer10Percent = sortedList.size() / 10.0;
				//System.out.println(sortedList.size() + " per10: " + valuesPer10Percent);
				System.out.print(logDf.format(e.getKey())  );
				System.out.print("\t" + avgDf.format(getAverage(sortedList)));
				int start = (int)Math.round(valuesPer10Percent);
				if (valuesPer10Percent == 0) valuesPer10Percent = 1;
				for (int i = 0; i < 10; i++) {
					int index = (int)Math.round(i * valuesPer10Percent + start);
					if (index >= sortedList.size()) {
						index = sortedList.size() - 1;
					}
					System.out.print("\t" + avgDf.format(sortedList.get(index)));
					//Print 95th%, 99th%:
					if (i == 8) {
						int index95 = (int)Math.round(i * valuesPer10Percent + valuesPer10Percent/2 + start);
						if (index95 >= sortedList.size()) {
							index95 = sortedList.size() - 1;
						}
						System.out.print("\t" + avgDf.format(sortedList.get(index95)));	
						int index99 = (int)Math.round(i * valuesPer10Percent + (valuesPer10Percent * 9.0 / 10) + start);
						if (index99 >= sortedList.size()) {
							index99 = sortedList.size() - 1;
						}
						System.out.print("\t" + avgDf.format(sortedList.get(index99)));	
					}
				}
				System.out.print("\t" + sortedList.size());
				System.out.println();
			}
		}

		private double getAverage(List<Double> sortedList) {
			if(sortedList.isEmpty()) return 0;
			double total = 0;
			for(Double val : sortedList) {
				total += val;
			}
			return total / sortedList.size();
		}
	}
	
	/**
	 * Stats entry class for<br>
	 * - avg duration calculation<br>
	 * - keeping sample number<br>
	 * - min/max<br>
	 * - and a count on very poor user experience above a custom threshold (BAD_UX_THRESHOLD_SECS)<br>
	 * <br>
	 * @author Thomas Hilpold
	 *
	 */
	class UxStatsEntry {
		String name;
		double totalDuration;
		long nrOfSamples;
		double minDuration = Double.MAX_VALUE;
		double maxDuration;
		int nrOfBadUx;
		
		/**
		 * Adds one sample duration sec value to this stats entry object. 
		 * @param durationSecs
		 */
		void addSample(double durationSecs) {
			totalDuration += durationSecs;
			nrOfSamples += 1;
			if (durationSecs > maxDuration) {
				maxDuration = durationSecs;
			}
			if (durationSecs < minDuration) {
				minDuration = durationSecs;
			}
			if (durationSecs > BAD_UX_THRESHOLD_SECS) {
				nrOfBadUx ++;
			}
			
		}
		
		/**
		 * Calculates the average duration based on all samples.	
		 * @return
		 */
		Double calcAverage() {
			return (nrOfSamples > 0)? totalDuration/nrOfSamples : -1;
		}
	}

	/**
	 * Runs the UxAnalyzer and prints stats in tab separated format on completion. <br>
	 * Make sure to review/set static variables first.<br>
	 * @param argv ignored
	 */
	public static void main(String[] argv) {
		UxLogAnalyzer x = new UxLogAnalyzer();
		x.run();
	}
}
