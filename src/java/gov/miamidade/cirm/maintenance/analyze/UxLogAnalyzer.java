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
 * 2. Modify logPaths<br>
 * 3. Modify BAD_UX_THRESHOLD_SECS<br>
 * Run as java program.<br>
 * <br>
 * @author Thomas Hilpold
 *
 */
class UxLogAnalyzer {
	
	static final boolean DBG_MATCHES = false;
	
	static final double BAD_UX_THRESHOLD_SECS = 10;	
	
	static String logMatch = "END createNewKOSR";
			
	static String[] logPaths = new String[] {
			"\\\\s2030050\\cirmservices\\logs\\wrapper.log",
			"\\\\s2030050\\cirmservices\\logs\\wrapper.log.1",
			"\\\\s2030050\\cirmservices\\logs\\wrapper.log.2",
			"\\\\s2030050\\cirmservices\\logs\\wrapper.log.3",
			"\\\\s2030050\\cirmservices\\logs\\wrapper.log.4",
			"\\\\s2030050\\cirmservices\\logs\\wrapper.log.5",
			"\\\\s2030050\\cirmservices\\logs\\wrapper.log.6",
			"\\\\s2030051\\cirmservices\\logs\\wrapper.log",
			"\\\\s2030051\\cirmservices\\logs\\wrapper.log.1",
			"\\\\s2030051\\cirmservices\\logs\\wrapper.log.2",
			"\\\\s2030051\\cirmservices\\logs\\wrapper.log.3",
			"\\\\s2030051\\cirmservices\\logs\\wrapper.log.4",
			"\\\\s2030051\\cirmservices\\logs\\wrapper.log.5",
			"\\\\s2030051\\cirmservices\\logs\\wrapper.log.6",
			"\\\\s2030057\\cirmservices\\logs\\wrapper.log",
			"\\\\s2030057\\cirmservices\\logs\\wrapper.log.1",
			"\\\\s2030057\\cirmservices\\logs\\wrapper.log.2",
			"\\\\s2030057\\cirmservices\\logs\\wrapper.log.3",
			"\\\\s2030057\\cirmservices\\logs\\wrapper.log.4",
			"\\\\s2030057\\cirmservices\\logs\\wrapper.log.5",
			"\\\\s2030057\\cirmservices\\logs\\wrapper.log.6",
			"\\\\s2030059\\cirmservices\\logs\\wrapper.log",
			"\\\\s2030059\\cirmservices\\logs\\wrapper.log.1",
			"\\\\s2030059\\cirmservices\\logs\\wrapper.log.2",
			"\\\\s2030059\\cirmservices\\logs\\wrapper.log.3",
			"\\\\s2030059\\cirmservices\\logs\\wrapper.log.4",
			"\\\\s2030059\\cirmservices\\logs\\wrapper.log.5",
			"\\\\s2030059\\cirmservices\\logs\\wrapper.log.6",
			"\\\\s2030060\\cirmservices\\logs\\wrapper.log",
			"\\\\s2030060\\cirmservices\\logs\\wrapper.log.1",
			"\\\\s2030060\\cirmservices\\logs\\wrapper.log.2",
			"\\\\s2030060\\cirmservices\\logs\\wrapper.log.3",
			"\\\\s2030060\\cirmservices\\logs\\wrapper.log.4",
			"\\\\s2030060\\cirmservices\\logs\\wrapper.log.5",
			"\\\\s2030060\\cirmservices\\logs\\wrapper.log.6"
	};
	
	private final static DateFormat logDf = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
	private final static NumberFormat avgDf = new DecimalFormat("#0.0");

	private UxStats stats = new UxStats();
	
	public UxLogAnalyzer() {
	}
	
	void run() {
		System.out.println("***** UX Log analyzer started *****");
		System.out.println("Files to analyze: " + logPaths.length);
		System.out.println("Line Match: " + logMatch);
		for (String logFile : logPaths) {
			File cur = new File(logFile);
			if (cur.isFile() && cur.canRead()) {
				System.out.print("Analyzing " + cur + "...");
				analyzeFile(cur, logMatch);
				System.out.println("Complete.");
			}
		}
		stats.printHourly();
		stats.printDayPercentiles();
	}
	
	void analyzeFile(File file, String lineMatch) {
		try (BufferedReader br = new BufferedReader(new FileReader(file))) {
		    String line;
		    while ((line = br.readLine()) != null) {
		       analyzeLine(line, lineMatch);
		    }
		    br.close();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	/**
	 * Analyzes a line by matching and then finding dateTime and duration for that sample.
	 * Modify and test the pattern if the log file format changes.
	 * 
	 * @param line
	 * @param lineMatch a string expected between the last | and the duration second double value.
	 */
	void analyzeLine(String line, String lineMatch) {
		Pattern p = Pattern.compile("(^.+\\|.+\\|([\\d\\/\\s:]+)\\|.+" + lineMatch + "([\\s\\d.]+)sec.*)");
		Matcher m = p.matcher(line);
		if (m.find()) {
			String dateTimeStr = m.group(2).trim();
			String durationStr = m.group(3).trim();
			Date dateTime;
			try {
				dateTime = logDf.parse(dateTimeStr);
				Double durationSecs = Double.parseDouble(durationStr);
				stats.addSample(dateTime, lineMatch, durationSecs);
				if (DBG_MATCHES) System.out.println(line + " >> Found: " + durationSecs + " DT: " + dateTimeStr);
			} catch (ParseException e) {
				System.out.println("Ignoring line: " + line);
			}
		}
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
			System.out.println("Date            \t10th\t20th\t30th\t40th\t50th\t60th\t70th\t80th\t90th\t100th\tNrOfSamples");
			for (Map.Entry<Date, List<Double>> e : durationsByDay.entrySet()) {
				List<Double> sortedList = e.getValue();
				Collections.sort(sortedList);
				double valuesPer10Percent = sortedList.size() / 10.0;
				//System.out.println(sortedList.size() + " per10: " + valuesPer10Percent);
				System.out.print(logDf.format(e.getKey())  );
				int start = (int)Math.round(valuesPer10Percent);
				if (valuesPer10Percent == 0) valuesPer10Percent = 1;
				for (int i = 0; i < 10; i++) {
					int index = (int)Math.round(i * valuesPer10Percent + start);
					if (index >= sortedList.size()) {
						index = sortedList.size() - 1;
					}
					System.out.print("\t" + avgDf.format(sortedList.get(index)));
				}
				System.out.print("\t" + sortedList.size());
				System.out.println();
			}
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
		double nrOfBadUx;
		
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
