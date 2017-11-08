package gov.miamidade.cirm.maintenance.analyze;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * A simple log search utility that can be used to find all log lines matching a string in multiple log file series.<br>
 * <br>
 * Expected log format:<br>
 * <br>
 * INFO   | jvm 1    | 2016/09/27 17:23:50 | T444-END createNewKOSR 3.635 sec<br>
 * <br>
 * Usage:<br>
 * 1. Modify logMatch<br>
 * 2. Modify ANALYSIS_DAYS_HISTORY_CUTOFF_DEFAULT<br>
 * 3. Modify rotatingLogSeriesBaseFiles<br>
 * Run as java program.<br>
 * <br>
 * @author Thomas Hilpold
 *
 */
class LogSearch {
	
	static final int ANALYSIS_DAYS_HISTORY_CUTOFF_DEFAULT = 30;	
	
	static final boolean DBG_MATCHES = false;
	
	static final double BAD_UX_THRESHOLD_SECS = 10;	
	
	static String logMatch = "Caused by: java.sql.BatchUpdateException: ORA-00060"; 
	
	
	static String[] rotatingLogSeriesBaseFiles = new String[] {
			"\\\\s2030050\\cirmservices\\logs\\wrapper.log",
			"\\\\s2030051\\cirmservices\\logs\\wrapper.log",
			"\\\\s2030057\\cirmservices\\logs\\wrapper.log",
			"\\\\s2030059\\cirmservices\\logs\\wrapper.log",
			"\\\\s2030060\\cirmservices\\logs\\wrapper.log"
	};
	
	private final static DateFormat logDf = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");

	public LogSearch() {
	}
	
	void run() {
		System.out.println("***** Log search started *****");
		System.out.println("Rotating base log files to analyze: " + rotatingLogSeriesBaseFiles.length);
		System.out.println("Line Match: " + logMatch);
		Date cutOffDate = determineCutoffDay(ANALYSIS_DAYS_HISTORY_CUTOFF_DEFAULT);
		System.out.println("Start day for analysis: " + cutOffDate);		
		for (String logFile : rotatingLogSeriesBaseFiles) {
			File cur = new File(logFile);
			analyzeRotatingLogFileSeries(cur, logMatch, cutOffDate);
		}
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
		    System.out.println(curLogFile.getAbsolutePath());	    	
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
	 * Analyzes a line by matching with lineMatch.
	 * Modify and test the pattern if the log file format changes.
	 * 
	 * @param line
	 * @param lineMatch a string expected between the last | and the duration second double value.
	 * @return lineDateTime or null if not determined
	 */
	Date analyzeLine(String line, String lineMatch, Date cutoff) {
		//Pattern p = Pattern.compile("(^.+\\|.+\\|([\\d\\/\\s:]+)\\|.+" + lineMatch + "([\\s\\d.]+)sec.*)");
		Pattern p = Pattern.compile("(^.+\\|.+\\|([\\d\\/\\s:]+)\\|.+" + lineMatch + ".*)");
		Matcher m = p.matcher(line);
		Date lineDateTime = null;
		if (m.find()) {
			String dateTimeStr = m.group(2).trim();
			try {
				lineDateTime = logDf.parse(dateTimeStr);
				if (lineDateTime.after(cutoff)) {
					System.out.println(line);
				}
			} catch (ParseException e) {
				System.out.println("Ignoring line: " + line);
			}
		}
		return lineDateTime;
	}
	
	/**
	 * Runs the LogSearch and prints all matching lines. <br>
	 * Make sure to review/set static variables first.<br>
	 * @param argv ignored
	 */
	public static void main(String[] argv) {
		LogSearch x = new LogSearch();
		x.run();
	}
}
