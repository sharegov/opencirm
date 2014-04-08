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

import java.awt.BorderLayout;
import java.awt.Container;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.DateFormat;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JToggleButton;

import oracle.jdbc.pool.OracleDataSource;


/**
 * UI based lightweight Cirm Monitor tracking Cirm DB stats.
 * Currently tracking numer of SRs and their insert speed.
 * Just set the DB params and start as Java app.
 * 
 * @author Thomas Hilpold
 *
 */
@SuppressWarnings("serial")
public class CirmDWMonitor extends JFrame
{
	static final String[] DWDB_TABLES = {
		"CIRM_OWL_DATA_VAL_CLOB",
		"CIRM_OWL_DATA_VAL_DATE",
		"CIRM_OWL_DATA_VAL_DOUBLE",
		"CIRM_OWL_DATA_VAL_INTEGER",
		"CIRM_OWL_DATA_VAL_STRING",
		"CIRM_IRI",
		"CIRM_CLASSIFICATION",
		"CIRM_OWL_OBJECT_PROPERTY",
		"CIRM_OWL_DATA_PROPERTY",
		"CIRM_GIS_INFO",
		"CIRM_MDC_ADDRESS",
		"CIRM_SR_REQUESTS",
		"CIRM_SR_ACTOR",
		"CIRM_SRREQ_SRACTOR",
		"CIRM_SR_ACTIVITY",
		//"CIRM_SR_STATUS_HISTORY",
		"CIRM_SERVICE_CALL",
		"CIRM_SERVICE_ACTION",
	"CIRM_IRI_TYPE",
	"CIRM_SR_TYPE_QUESTION",
	"CIRM_SR_TYPE",
	"CIRM_USER",
	"CIRM_ORG_UNIT",
	"CIRM_SR_ACTIVITY_TYPE",
	"CIRM_SR_INTAKE_METHOD",
	"CIRM_SR_OUTCOME",
	"CIRM_SR_PRIORITY",
	"CIRM_SR_STATUS"
	};

	//TEST DATAWAREHOUSE
	static final String TDB_ID  = "TEST DW 11.2 - tcirmdw";
	//static final String TDB_URL = "jdbc:oracle:thin:@(DESCRIPTION =(ADDRESS = (PROTOCOL = TCP)(HOST = s0142084.miamidade.gov)(PORT = 1521))(ADDRESS = (PROTOCOL = TCP)(HOST = s0142085.miamidade.gov)(PORT = 1521))(LOAD_BALANCE = yes)(CONNECT_DATA =(SERVER = DEDICATED)(SERVICE_NAME = tcirmdw.miamidade.gov)))";
	static final String TDB_URL = "jdbc:oracle:thin:@(DESCRIPTION=(ADDRESS_LIST=(ADDRESS=(PROTOCOL=TCP)(HOST=tstgrid-scan.miamidade.gov)(PORT=1521)))(CONNECT_DATA=(SERVICE_NAME=tcirmdw.miamidade.gov)))";
	static final String TDB_USR = "cirmdwschm";
	//.set("reportingDBPwd","cirmrepschm")
	
	//PROD DATAWAREHOUSE
	static final String PDB_ID = "PRODUCTION DW 11.2 - pcirmdw.miamidade.gov";
	static final String PDB_URL = "jdbc:oracle:thin:@(DESCRIPTION=(ADDRESS_LIST=(ADDRESS=(PROTOCOL=TCP)(HOST=prodgrid2-scan.miamidade.gov)(PORT=1521)))(CONNECT_DATA=(SERVICE_NAME=pcirmdw.miamidade.gov)))";
	static final String PDB_USR = "cirmdwuser";

	static final int HISTORY_SIZE = 15000;
	static final int BEEP_BELOW_SR_PER_SEC = -1; //180;
	
	volatile String selectedDB_ID;
	volatile String selectedDB_URL;
	volatile String selectedDB_USR;
	
	JToggleButton startStopMonitoringBt;
	JTextArea monitorTA;
	JComboBox intervalSecsCombo;
	JComboBox databaseSelectCombo;
	JLabel panelLabel;
	OracleDataSource datasource;
	volatile Thread monitor;
	volatile int selectedInterval = 10;
	
	public CirmDWMonitor() 
	{
		init();
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
	
	public void init() 
	{
		Container c = getContentPane();
		c.setLayout(new BorderLayout(10, 10));
		monitorTA = new JTextArea(40,300 );
		JScrollPane scrollPane = new JScrollPane(monitorTA);
		startStopMonitoringBt = new JToggleButton("Start Monitoring", false);
		startStopMonitoringBt.addActionListener(new ActionListener()
		{
			@Override
			public void actionPerformed(ActionEvent e)
			{
				startStop();
			}
		});
		databaseSelectCombo = new JComboBox();
		databaseSelectCombo.addItem("TEST DW");
		databaseSelectCombo.addItem("PROD DW");		
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
		panelLabel = new JLabel("Miami Dade CirmDW Monitor: " + TDB_ID + "\r\n "); 
		c.add(panelLabel, BorderLayout.NORTH);
		monitorTA.setText("Welcome to Cirm DW Monitor. \r\n\r\n Just select an interval and \r\n press Start Monitoring. \r\n\r\n " 
						+ "A high interval means less \r\n load on the DB! \r\n\r\n"
						+ "A history of " + "unlimited" + " values is kept.");
		c.add(scrollPane, BorderLayout.CENTER);

		JPanel p = new JPanel();
		p.add(startStopMonitoringBt, BorderLayout.SOUTH);
		p.add(intervalSecsCombo);
		p.add(databaseSelectCombo);
		c.add(p, BorderLayout.SOUTH);
		databaseSelectCombo.addActionListener(new ActionListener()
		{
			
			@Override
			public void actionPerformed(ActionEvent e)
			{
				stopMonitoring();
				if (databaseSelectCombo.getSelectedIndex() == 1)
				{
					selectedDB_ID = PDB_ID;
					selectedDB_URL = PDB_URL;
					selectedDB_USR = PDB_USR;
					panelLabel.setText("Miami Dade CirmDW Monitor: " + PDB_ID + "\r\n ");
				}
				else
				{
					selectedDB_ID = TDB_ID;
					selectedDB_URL = TDB_URL;
					selectedDB_USR = TDB_USR;
					panelLabel.setText("Miami Dade CirmDW Monitor: " + TDB_ID + "\r\n ");
				}
			}
		});
		databaseSelectCombo.setSelectedIndex(0);
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
		this.setTitle("Miami Dade CiRM DW Monitor");
		this.setSize(800, 300);
		this.setVisible(true);
	}
	
	private void startStop() 
	{
		if(startStopMonitoringBt.isSelected())
			startMonitoring();
		else
			stopMonitoring();
	}

	private void startMonitoring() 
	{
		if (monitor != null) return;
		if (datasource !=null || ensureDatasource()) 
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
	
	private boolean ensureDatasource() 
	{		
		Connection conn = null;
		String message = "<html>Please Enter Password for: " + selectedDB_ID + " <br/>" 
				+ " User: " + selectedDB_USR + "</html>";
		int okCancel = -1;
		do 
		{
			JPanel p = new JPanel(new BorderLayout(5,5));
			JLabel l = new JLabel(message);
			JPasswordField passf = new JPasswordField(30);
			p.add(l, BorderLayout.NORTH);
			p.add(passf, BorderLayout.CENTER);
			okCancel = JOptionPane.showConfirmDialog(this, p, "Cirm DW Monitor - Connection Pass", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
			if (okCancel == JOptionPane.OK_OPTION) 
			{
			   String password = new String(passf.getPassword());
				try 
				{
					datasource = createDatasource(password); 
					conn = datasource.getConnection();
				} catch (Exception e)
				{   
					if (conn != null)  try { conn.close(); } catch(Exception ec) {};
					JOptionPane.showMessageDialog(this, "Connection failed with: \r\n" + e.toString());
					datasource = null;
				}
			} 
			else
				datasource = null;
			//String passw = JOptionPane.showInputDialog(this, message, "Cirm Monitor - Connection Pass", JOptionPane.WARNING_MESSAGE);
		} while (conn == null && okCancel == JOptionPane.OK_OPTION);
		try {
			if (conn != null) conn.close();
		} catch(Exception e) {}; 
		return datasource != null;
	}

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
		datasource = null;
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
				Connection conn = null;
				try 
				{
					CirmStatistics stats = new CirmStatistics();
					stats.setInterval(selectedInterval);
					monitorTA.setText("Connecting...");
					monitorTA.repaint();
					conn = datasource.getConnection();
					do 
					{
						stats.retrieveDataFrom(conn);
						monitorTA.setText(stats.toString());
						monitorTA.repaint();
						int sleep = 0;
						do {
							Thread.sleep(1000);
							sleep += 1;
						} while (monitor != null && sleep < stats.getIntervalSecs());
					} while (monitor != null);
				} catch (Exception e)
				{	String oldText = monitorTA.getText();
					monitorTA.setText(e.toString() + "\r\n" + oldText);
					monitorTA.repaint();
					throw new RuntimeException(e);
				}
				finally
				{
					if (conn !=null)
						try
						{
							conn.close();
						} catch (SQLException e)
						{
							throw new RuntimeException(e);
						}
				}
			}
		};
	}
	private OracleDataSource createDatasource(String passwd) throws SQLException
	{
		OracleDataSource ods = new OracleDataSource();
		ods.setURL(selectedDB_URL);
		ods.setUser(selectedDB_USR);
		ods.setPassword(passwd);
		// FOR DEBUGGING DB ods.setLogWriter(new PrintWriter(System.out));
		// hilpold maybe use: ods.setConnectionCachingEnabled(arg0);
		// ods.setExplicitCachingEnabled(arg0);
		// ods.setConnectionCacheProperties(arg0);;
		// ods.setImplicitCachingEnabled(arg0);
		// ods.setConnectionProperties(arg0);
		System.out.println("Oracle Datasource created : ");
		System.out.println("ConnectionCachingEnabled  : "
				+ ods.getConnectionCachingEnabled());
		System.out.println("ConnectionCacheProperties : "
				+ ods.getConnectionCacheProperties());
		System.out.println("ImplicitCachingEnabled    : "
				+ ods.getImplicitCachingEnabled());
		System.out.println("ExplicitCachingEnabled    : "
				+ ods.getExplicitCachingEnabled());
		System.out.println("MaxStatements             : "
				+ ods.getMaxStatements());
		return ods;
	}

	/**
	 * Easily extensible synced Statistics that retrieves data from a Connection to a CiRM DB.
	 * @author Thomas Hilpold
	 *
	 */
	public static class CirmStatistics
	{
	    int intervalSecs = 10;
		List<Date> probeTimes;  
		List<long[]> valueHistory;  
		List<double[]> valuePerSecHistory;
		
		NumberFormat nf = new DecimalFormat("#######0.00");
		Date startTime;
		DateFormat dateFormat = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM);
		DateFormat timeFormat = DateFormat.getTimeInstance(DateFormat.MEDIUM);
		
		public CirmStatistics() 
		{
			init();
		}
		
		private void init()
		{
			valueHistory  = new ArrayList<long[]>(10000);
			valuePerSecHistory  = new ArrayList<double[]>(10000);
			probeTimes = new ArrayList<Date>(10000);
		}

		public synchronized int getIntervalSecs() 
		{
			return intervalSecs;
		}

		public synchronized void addProbe(long[] values)
		{
			Date lastProbeTime;
			Date curProbeTime = new Date();
			valueHistory.add(values);
			probeTimes.add(curProbeTime);
			if (probeTimes.size() > 1) {
				lastProbeTime = probeTimes.get(probeTimes.size() - 2);
				double intervalSecs = (curProbeTime.getTime() - lastProbeTime.getTime()) / 1000.0;
				long[] lastValues = valueHistory.get(valueHistory.size() - 2);
				double[] valuesPerSec = new double[DWDB_TABLES.length];
				for (int i = 0; i < DWDB_TABLES.length; i++) 
				{
					long delta = values[i] - lastValues[i];
					double valuePerSec = delta / intervalSecs;
					valuesPerSec[i] = valuePerSec;
				}
				valuePerSecHistory.add(valuesPerSec);
			} 
			else
			{
				startTime = new Date();
				valuePerSecHistory.add(new double[DWDB_TABLES.length]);
			}
		}


		public synchronized long[] getTotalAdded() 
		{
			if (probeTimes.size() >= 2)
			{
				long[] firstValues = valueHistory.get(0); 
				long[] lastValues = valueHistory.get(valueHistory.size() - 1);
				long[] totals = new long[DWDB_TABLES.length];
				for (int i = 0; i < DWDB_TABLES.length; i++)
				{
					totals[i] = lastValues[i] - firstValues[i];
				}
				return totals;
			}
			else 
				return new long[DWDB_TABLES.length];
		}
		
		public synchronized double[] getTotalAddedPerSec() 
		{
			if (probeTimes.size() >= 2)
			{
				long firstProbeTime = probeTimes.get(0).getTime();
				long lastProbeTime = probeTimes.get(probeTimes.size() - 1).getTime();
				long[] firstValues = valueHistory.get(0); 
				long[] lastValues = valueHistory.get(valueHistory.size() - 1);
				double[] totals = new double[DWDB_TABLES.length];
				for (int i = 0; i < DWDB_TABLES.length; i++)
				{
					totals[i] = (lastValues[i] - firstValues[i]) / ((double)(lastProbeTime - firstProbeTime) / 1000.0);
				}
				return totals;
			}
			else 
				return new double[DWDB_TABLES.length];
		}

		public synchronized String toString() 
		{
			StringBuffer s = new StringBuffer(2000);
			s.append("Cirm DW Statistics at :" + dateFormat.format(new Date()) + "\r\n\r\n");
			s.append("Total Counts: \r\n");
			long[] totals = getTotalAdded();
			for (int i = 0; i < DWDB_TABLES.length; i++)
			{
				s.append(DWDB_TABLES[i] + " " + totals[i] + " ,");
			}
			s.append("\r\n");
			s.append("Total Inserts/sec: \r\n");
			double[] totalsPerSec = getTotalAddedPerSec();
			for (int i = 0; i < DWDB_TABLES.length; i++)
			{
				s.append(DWDB_TABLES[i] + " " + nf.format(totalsPerSec[i]) + ", ");
			}
			s.append("\r\n");
			if (startTime != null) 
			{
				s.append(" since: " + dateFormat.format(startTime) + "\r\n");
			}
			s.append("Time \t ");
			for (int i = 0; i < DWDB_TABLES.length; i++)
			{
				s.append(getEqualLen(DWDB_TABLES[i]) + " \t ");
			}
			s.append("\r\n");
			for (int p = probeTimes.size() - 1; p >= 0; p--)
			{
				//Data line 1
				s.append("" + timeFormat.format(probeTimes.get(p)) + " \t ");
				long[] values = valueHistory.get(p);
				double[] valuesPerSec = valuePerSecHistory.get(p);
				for (int i = 0; i < DWDB_TABLES.length; i++)
				{
					s.append(values[i] + " \t ");
					s.append(nf.format(valuesPerSec[i]) + " \t ");
				}
				s.append("\r\n");
//				//Data line 2
//				s.append("  /min:  "+ " \t ");
//				double[] valuesPerMin = valuePerMinHistory.get(p);
//				for (int i = 0; i < DWDB_TABLES.length; i++)
//				{
//					s.append(nf.format(valuesPerMin[i]) + " \t ");
//				}
				//s.append("\r\n");
			}
			return s.toString();
		}
		
		private String getEqualLen(String string)
		{
			int len = 20;
			if (string.length() <= len) 
				string = "                    " + string;
			return string.substring(string.length() - len, string.length());
		}

		public synchronized void retrieveDataFrom(Connection conn) throws SQLException
		{
			long start = System.currentTimeMillis();
			java.sql.Statement s = conn.createStatement();
			long[] tableCounts = new long[DWDB_TABLES.length];
			for (int i = 0; i < DWDB_TABLES.length; i ++)
			{
				try {
					ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM CIRMDWSCHM." + DWDB_TABLES[i] + " ");
					rs.next();
					tableCounts[i] = rs.getInt(1);
					rs.close();
				} catch(SQLException e)
				{
					System.err.println("SQLException querying table counts: " + e);
					e.printStackTrace();
				}
			}
			s.close();
			long end = System.currentTimeMillis();
			System.out.println(new Date().toString() + "\t DbProbe took \t" + nf.format((end - start)/1000.0) + " \t secs");
			addProbe(tableCounts);
			
			//java.sql.Timestamp now = new java.sql.Timestamp(System.currentTimeMillis());
//			java.sql.Timestamp nowMinusInterval = new java.sql.Timestamp(now.getTime() - getIntervalSecs() * 1000); 
//			PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM CIRMSCHM.CIRM_SR_REQUESTS WHERE UPDATED_DATE > ? AND UPDATED_DATE <= ? ");
//			ps.setTimestamp(1, nowMinusInterval);
//			ps.setTimestamp(2, now);
//			rs = ps.executeQuery();
//			rs.next();
//			int nrUpdatedInInterval = rs.getInt(1);
//			rs.close();
//			ps.close();
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
		new CirmDWMonitor();
	}

}
