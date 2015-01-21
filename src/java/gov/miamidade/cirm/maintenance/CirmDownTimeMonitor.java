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

import gov.miamidade.cirm.maintenance.CirmDownTimeMonitor.ProbeResult.ProbeResultType;

import java.awt.BorderLayout;
import java.awt.Container;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.DateFormat;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JToggleButton;
import javax.swing.SwingUtilities;

import mjson.Json;
import oracle.jdbc.pool.OracleDataSource;


/**
 * UI based lightweight Cirm Downtime Monitor tracking Down time of various servers / systems.
 * results are appended to a log file in user dir if one or more probes measure that a system is down.
 * @author Thomas Hilpold
 *
 */
@SuppressWarnings("serial")
public class CirmDownTimeMonitor extends JFrame
{
	static final String DEFAULT_CONFIG_JSON = "CirmDownTimeMonitorConfig.json";
	static final String DEFAULT_LOG_FILE_NAME = "C:\\logs\\CirmDownTimeLog.log";
	static final boolean DEFAULT_APPEND = true;
	static final boolean DEFAULT_LOG_UP = true;
	static final int URL_CONNECT_TIMEOUT_MS = 1000;
	
	static final int HISTORY_SIZE = 15000;
	
	JToggleButton startStopMonitoringBt;
	JScrollPane scrollPane;
	JTextArea monitorTA;
	JComboBox intervalSecsCombo;
	JLabel panelLabel;
	static List<MonitorProbe> probes;
	volatile Thread monitor;
	volatile int selectedInterval = 600;
	
	
	public CirmDownTimeMonitor() 
	{
		try
		{
			initProbes();
		} catch (IOException e1)
		{
			throw new RuntimeException(e1);
		}
		new AllowAnySSL().installPermissiveTrustmanager();
		initGUI();
		this.addWindowListener(new WindowAdapter()
		{
			@Override
			public void windowClosing(WindowEvent e)
			{
				stopMonitoring();
				super.windowClosing(e);
			}
		});
		this.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
	}
	
	public void initProbes() throws IOException {
		BufferedReader bfr = new BufferedReader(new FileReader(this.getClass().getResource(DEFAULT_CONFIG_JSON).getFile()));
		String line;
		StringBuffer json = new StringBuffer(1000);
		while ((line = bfr.readLine()) != null) 
		{
			json.append(line + "\r\n");
		}
		bfr.close();
		List<Json> config = Json.read(json.toString()).asJsonList();
		probes = new ArrayList<CirmDownTimeMonitor.MonitorProbe>(config.size());
		for (Json probeConfig : config) 
		{	String type = probeConfig.at("TYPE").asString();
			if ("URL".equalsIgnoreCase(type)) { 
				if (probeConfig.has("REGEX")) {
					probes.add(new HTTPURLProbe(probeConfig.at("ID").asString(), probeConfig.at("URL").asString(), probeConfig.at("REGEX").asString()));
				} else {
					probes.add(new HTTPURLProbe(probeConfig.at("ID").asString(), probeConfig.at("URL").asString()));
				}
			} else if ("ORA".equalsIgnoreCase(type)) {
				probes.add(
						new ORAProbe(probeConfig.at("ID").asString(), 
						probeConfig.at("URL").asString(),
						probeConfig.at("USER").asString(),
						probeConfig.at("PASS").asString(),
						probeConfig.at("QUERY").asString()
						)
				);
			}
		}
	}
	
	public void initGUI() 
	{
		Container c = getContentPane();
		c.setLayout(new BorderLayout(10, 10));
		monitorTA = new JTextArea(40,300 );
	    scrollPane = new JScrollPane(monitorTA);
		startStopMonitoringBt = new JToggleButton("Start Monitoring", false);
		startStopMonitoringBt.addActionListener(new ActionListener()
		{
			@Override
			public void actionPerformed(ActionEvent e)
			{
				startStop();
			}
		});
		intervalSecsCombo = new JComboBox();
		intervalSecsCombo.addItem(60);
		intervalSecsCombo.addItem(120);
		intervalSecsCombo.addItem(180);
		intervalSecsCombo.addItem(300);
		intervalSecsCombo.addItem(600);
		intervalSecsCombo.addItem(900);
		intervalSecsCombo.addItem(1800);
		intervalSecsCombo.addItem(3600);
		intervalSecsCombo.addItem(7200);
		panelLabel = new JLabel("Miami Dade Cirm DownTime Monitor " + "\r\n "); 
		c.add(panelLabel, BorderLayout.NORTH);
		monitorTA.setText("Welcome to Cirm DW Monitor. \r\n\r\n Just select an interval and \r\n press Start Monitoring. \r\n\r\n " 
						+ "A high interval means less \r\n load on the DB! \r\n\r\n"
						+ "The log " + getLogFile().toString() + " will be used. Append mode: " + DEFAULT_APPEND);
		c.add(scrollPane, BorderLayout.CENTER);

		JPanel p = new JPanel();
		p.add(startStopMonitoringBt, BorderLayout.SOUTH);
		p.add(intervalSecsCombo);
		c.add(p, BorderLayout.SOUTH);
		intervalSecsCombo.addActionListener(new ActionListener()
		{
			@Override
			public void actionPerformed(ActionEvent e)
			{
				Object newIntervalObj = intervalSecsCombo.getSelectedItem();
				if (newIntervalObj instanceof Integer) 
				{
					int newInterVal = (Integer)newIntervalObj;
					selectedInterval = newInterVal;
					stopMonitoring();
				}
			}
		});
		this.setAlwaysOnTop(true);
		this.setTitle("Miami Dade CiRM DownTime Monitor 1.0 (" + getLogFile().toString() + ")");
		this.setSize(900, 300);
		this.setVisible(true);
	}
	
	private void startStop() 
	{
		if(monitor == null)			
			startMonitoring();
		else
			stopMonitoring();
	}

	private void startMonitoring() 
	{
		if (monitor != null) return;
		if (true /*ensureDatasources() */) 
		{			
			monitor = new Thread(getMonitorRunnable(), "CirmDWMonitor");
			monitor.start();
			beep();
			startStopMonitoringBt.setText("Stop Monitoring");
			if (!startStopMonitoringBt.isSelected()) startStopMonitoringBt.setSelected(true);
		} 
		else
			startStopMonitoringBt.setSelected(false);
	}
	
	private File getLogFile() 
	{
		//String userDir = System.getProperty("user.home");
		//return  new File(userDir + File.separator + DEFAULT_LOG_FILE_NAME);
		return new File(DEFAULT_LOG_FILE_NAME);
	}
	
	private void appendToLog(String line) 
	{
		try {
			FileWriter logw = new FileWriter(getLogFile(), true);
			logw.append(line);
			logw.close();
		} catch (IOException e) {
			System.err.println("ERROR WRITING TO LOG" + e.toString());
			e.printStackTrace();
		}
	}
	
//	private boolean ensureDatasources() 
//	{		
//		Connection conn = null;
//		String message = "<html>Please Enter Password for: " + selectedDB_ID + " <br/>" 
//				+ " User: " + selectedDB_USR + "</html>";
//		int okCancel = -1;
//		do 
//		{
//			JPanel p = new JPanel(new BorderLayout(5,5));
//			JLabel l = new JLabel(message);
//			JPasswordField passf = new JPasswordField(30);
//			p.add(l, BorderLayout.NORTH);
//			p.add(passf, BorderLayout.CENTER);
//			okCancel = JOptionPane.showConfirmDialog(this, p, "Cirm DW Monitor - Connection Pass", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
//			if (okCancel == JOptionPane.OK_OPTION) 
//			{
//			   String password = new String(passf.getPassword());
//				try 
//				{
//					datasource = createDatasource(password); 
//					conn = datasource.getConnection();
//				} catch (Exception e)
//				{   
//					if (conn != null)  try { conn.close(); } catch(Exception ec) {};
//					JOptionPane.showMessageDialog(this, "Connection failed with: \r\n" + e.toString());
//					datasource = null;
//				}
//			} 
//			else
//				datasource = null;
//			//String passw = JOptionPane.showInputDialog(this, message, "Cirm Monitor - Connection Pass", JOptionPane.WARNING_MESSAGE);
//		} while (conn == null && okCancel == JOptionPane.OK_OPTION);
//		try {
//			if (conn != null) conn.close();
//		} catch(Exception e) {}; 
//		return datasource != null;
//	}

	private void stopMonitoring() 
	{
		Thread curMonitor = monitor;
		monitor = null;
		while (curMonitor !=null && curMonitor.isAlive()) {
			try
			{
				Thread.sleep(100);
			} catch (InterruptedException e)
			{
				e.printStackTrace();
			}
		}
		startStopMonitoringBt.setText("Start Monitoring");
		if (startStopMonitoringBt.isSelected()) startStopMonitoringBt.setSelected(false);
	}
	
	private static void beep() 
	{
		Toolkit.getDefaultToolkit().beep();
	}
	
	private Runnable getMonitorRunnable() {
		return new Runnable()
		{
			@Override
			public void run()
			{
				do {
					try 
					{
						CirmStatistics stats = new CirmStatistics();
						stats.setInterval(selectedInterval);
						monitorTA.setText("measuring...");
						do 
						{
							boolean error = false;
							List<ProbeResult> results = new ArrayList<CirmDownTimeMonitor.ProbeResult>(probes.size());
							for (MonitorProbe p : probes) {
								ProbeResult r = p.test();
								if (!error) error = r.type != ProbeResultType.OK; 
								results.add(r);
							}
							if (error || DEFAULT_LOG_UP) 
							{
								stats.addCurrentResults(results);
								appendToLog(stats.lastLine());
							}
							final String newMonitorTATxt = stats.toString();
							SwingUtilities.invokeAndWait(new Runnable()
							{
								
								@Override
								public void run()
								{
									monitorTA.setText(newMonitorTATxt);
									monitorTA.setCaretPosition(0);
									scrollPane.repaint();
								}
							});
							int sleep = 0;
							do {
								Thread.sleep(1000);
								sleep += 1;
							} while (monitor != null && sleep < stats.getIntervalSecs());
						} while (monitor != null);
					} catch (Exception e) {
						String oldText = monitorTA.getText();
						final String newMonitorTATxt = e.toString() + "\r\n" + oldText;
						try {
							SwingUtilities.invokeAndWait(new Runnable()
							{
								
								@Override
								public void run()
								{
									monitorTA.setText(newMonitorTATxt);
									scrollPane.repaint();
								}
							});
						} catch (Exception ex) {
							throw new RuntimeException(ex);
						}
						throw new RuntimeException(e);
					}
				} while (monitor != null);				
			}
		};
	}

	/**
	 * Easily extensible synced Statistics that retrieves data from a Connection to a CiRM DB.
	 * @author Thomas Hilpold
	 *
	 */
	public static class CirmStatistics
	{
	    int intervalSecs = 60;
		List<Date> probeTimes;  
		List<List<ProbeResult>> resultHistory;  
		List<ProbeResult> lastFailure;
		
		NumberFormat nf = new DecimalFormat("#######0.00");
		Date startTime;
		DateFormat dateFormat = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM);
		DateFormat timeFormat = DateFormat.getTimeInstance(DateFormat.MEDIUM);
		
		public CirmStatistics() 
		{
			init();
		}
		
		public void addCurrentResults(List<ProbeResult> results)
		{
			probeTimes.add(new Date());
			resultHistory.add(results);
		}

		private void init()
		{
			resultHistory  = new ArrayList<List<ProbeResult>>();
			probeTimes = new ArrayList<Date>(10000);
		}

		public synchronized int getIntervalSecs() 
		{
			return intervalSecs;
		}
		public synchronized String lastLine()
		{
			StringBuffer s = new StringBuffer(2000);
			s.append(toRow(probeTimes.get(probeTimes.size() - 1), resultHistory.get(probeTimes.size() - 1)));
			s.append("\r\n");
			return s.toString();
		}
		
		public synchronized String toString() 
		{
			StringBuffer s = new StringBuffer(2000);
			s.append("Cirm DW Statistics at :" + dateFormat.format(new Date()) + "\r\n\r\n");
//			if (lastFailure != null) {
//				s.append("LAST FAILURE : \r\n" + dateFormat.format(probeTimes.get(probeTimes.size())));
			s.append("Started at :" + dateFormat.format(probeTimes.get(0)));
			s.append("\r\n");
			s.append(getHeaderRow());
			s.append("\r\n");
			for (int p = probeTimes.size() - 1; p >= 0; p--)
			{
				s.append(toRow(probeTimes.get(p), resultHistory.get(p)));
				s.append("\r\n");
			}
			return s.toString();
		}
		
		private String getHeaderRow() 
		{
			String result = "Time \t";
			for (MonitorProbe p : probes) {
				//result += getEqualLen(p.id) + "\t";
				result += p.id + "\t";
			}
			return result;
		}
		
		private String toRow(Date resultTime, List<ProbeResult> l)
		{
			String result = dateFormat.format(resultTime) + "\t";
			for (ProbeResult p : l) {
				result += p.type.toString() + "(" + p.message + ")" + "\t";
			}
			return result;
		}
		
		private String getEqualLen(String string)
		{
			int len = 20;
			if (string.length() <= len) 
				string = "                    " + string;
			return string.substring(string.length() - len, string.length());
		}
		
		public synchronized void setInterval(int intervalSecs)
		{
			if (this.intervalSecs != intervalSecs && intervalSecs > 0) 
			{
				this.intervalSecs = intervalSecs;
				init();
			}
		}
	}
	
	public static void main(String[] argv) 
	{
		new CirmDownTimeMonitor();
	}

	public static class ProbeResult {
		public enum ProbeResultType { OK, REPONSE_ERROR, CONNECTION_ERROR };
		String message;
		ProbeResultType type;
		ProbeResult(ProbeResultType t, String message) {
			type = t;
			this.message = message;
		}
		public boolean isError() 
		{
			return !type.equals(ProbeResultType.OK);
		}
	}

	public static abstract class MonitorProbe {
		String id;
		public abstract ProbeResult test();
	}
	
	public static class HTTPURLProbe extends MonitorProbe {
		java.net.URL target;
		String URL;
		String regex = null; //optional
	
		HTTPURLProbe(String id, String url)	{
			this(id, url, null);
		}
		
		/**
		 * @param regex optional
		 */
		public HTTPURLProbe(String id, String url, String regex)
		{
			this.id = id;
			URL = url;
			this.regex = regex;  
			try {
				target = new java.net.URL(URL);
			} catch (MalformedURLException e) {
				throw new RuntimeException(e);
			}
		}

		public ProbeResult test() {
			try {
				HttpURLConnection c =  (HttpURLConnection)target.openConnection();
				c.setConnectTimeout(URL_CONNECT_TIMEOUT_MS);
				String result;
				try {
					BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream()));
					String curLine;
					StringBuffer str = new StringBuffer(1000);
					while ((curLine = r.readLine()) != null) 
					{
						str.append(curLine);
					}
					result = str.toString();
				} catch (Exception e) {
					System.err.println("Error reading content from: " + target.toString() + e);
					result = "READ ERROR";
				}
				//System.out.println(result);
				if (regex != null) {
					try {
						Matcher m = Pattern.compile(regex).matcher(result); 
						result = (m.find())? m.group(1) : result.substring(0,8);
					} catch (PatternSyntaxException e) {
						System.err.println("Pattern error, check config: " + regex);
					}
				} else {
					result = result.substring(0,8);
				}
				int responsecode = c.getResponseCode();
				if (responsecode != 200) {
					return new ProbeResult(ProbeResultType.REPONSE_ERROR, "httpCode: " + responsecode);
				} else {
					return new ProbeResult(ProbeResultType.OK, result);
				}
			} catch (Exception e) {
				return new ProbeResult(ProbeResultType.CONNECTION_ERROR, e.toString());
			} 
		}
	}
	
	public static class ORAProbe extends MonitorProbe {
		OracleDataSource ods;
		String query;

		public ORAProbe(String id, String url, String user, String pass, String query)	{
			this.id = id;
			try	{
				ods = new OracleDataSource();
			} catch (SQLException e) {
				throw new RuntimeException(e);
			}
			ods.setURL(url);
			ods.setUser(user);
			ods.setPassword(pass);
			this.query = query;
		}

		public ProbeResult test() {
			Connection connection = null;
			try {
				connection = ods.getConnection();
				Statement s = connection.createStatement();
				ResultSet rs = s.executeQuery(query);
				rs.next();
				String result = rs.getObject(1).toString();
				return new ProbeResult(ProbeResultType.OK, "" + result);
			} catch (SQLException sx) {
				return new ProbeResult(ProbeResultType.CONNECTION_ERROR, sx.toString());
			} finally {
				try
				{
					if (connection != null) connection.close();
				} catch (SQLException e)
				{
					e.printStackTrace();
				}
			}
		}
	}
}

