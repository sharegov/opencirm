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
import java.sql.PreparedStatement;
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
public class CirmMonitor extends JFrame
{
	static final String [][] DBs = {
				{"local"
					,"local XE"
					,"jdbc:oracle:thin:@localhost:1521:XE"
					,"cirmschm"}
				,{"test"
					,"tcirm.miamidade.gov"
					,"jdbc:oracle:thin:@(DESCRIPTION=(ADDRESS_LIST=(ADDRESS=(PROTOCOL=TCP)(HOST=tstgrid-scan.miamidade.gov)(PORT=1521)))(CONNECT_DATA=(SERVICE_NAME=tcirm.miamidade.gov)))"
					,"cirmschm"}
				,{"prod"
					,"pcirm.miamidade.gov"
					,"jdbc:oracle:thin:@(DESCRIPTION=(ADDRESS_LIST=(ADDRESS=(PROTOCOL=TCP)(HOST=prodgrid2-scan.miamidade.gov)(PORT=1521)))(CONNECT_DATA=(SERVICE_NAME=pcirm.miamidade.gov)))"
					, "cirmschm"}
	};
	
	static final String DB_ID = "pcirm.miamidade.gov";
	//static final String DB_URL = "jdbc:oracle:thin:@(DESCRIPTION =(ADDRESS = (PROTOCOL = TCP)(HOST = s0141409.miamidade.gov)(PORT = 1521))(ADDRESS = (PROTOCOL = TCP)(HOST = s0141734.miamidade.gov)(PORT = 1521))(ADDRESS = (PROTOCOL = TCP)(HOST = s0141872.miamidade.gov)(PORT = 1521))(LOAD_BALANCE = yes)(CONNECT_DATA =(SERVER = DEDICATED)(SERVICE_NAME = pcirm.miamidade.gov)))";
	static final String DB_URL = "jdbc:oracle:thin:@(DESCRIPTION=(ADDRESS_LIST=(ADDRESS=(PROTOCOL=TCP)(HOST=prodgrid2-scan.miamidade.gov)(PORT=1521)))(CONNECT_DATA=(SERVICE_NAME=pcirm.miamidade.gov)))";
	static final String DB_USR = "cirmschm";
//	static final String DB_ID = "tcirm.miamidade.gov";
//	static final String DB_URL = "jdbc:oracle:thin:@(DESCRIPTION =(ADDRESS = (PROTOCOL = TCP)(HOST = s0142084.miamidade.gov)(PORT = 1521))(ADDRESS = (PROTOCOL = TCP)(HOST = s0142085.miamidade.gov)(PORT = 1521))(LOAD_BALANCE = yes)(CONNECT_DATA =(SERVER = DEDICATED)(SERVICE_NAME = tcirm.miamidade.gov)))";
//	static final String DB_USR = "cirmschm";
//	static final String DB_ID = "local XE";
//	static final String DB_URL = "jdbc:oracle:thin:@localhost:1521:XE";
//	static final String DB_USR = "cirmschm";

	static final int HISTORY_SIZE = 15000;
	static final int BEEP_BELOW_SR_PER_MIN = -1; //180;
	
	
	JToggleButton startStopMonitoringBt;
	JTextArea monitorTA;
	JComboBox intervalSecsCombo;
	OracleDataSource datasource;
	volatile Thread monitor;
	volatile int selectedInterval = 10;
	
	public CirmMonitor() 
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
		monitorTA = new JTextArea(40,80 + HISTORY_SIZE);
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
		intervalSecsCombo = new JComboBox();
		intervalSecsCombo.addItem(10);
		intervalSecsCombo.addItem(20);
		intervalSecsCombo.addItem(30);
		intervalSecsCombo.addItem(60);
		intervalSecsCombo.addItem(300);
		intervalSecsCombo.addItem(600);
		intervalSecsCombo.addItem(900);
		intervalSecsCombo.addItem(1800);
		intervalSecsCombo.addItem(3600);
		intervalSecsCombo.addItem(7200);
		c.add(new JLabel("Cirm Monitor: \r\n " + DB_ID), BorderLayout.NORTH);
		monitorTA.setText("Welcome to Cirm Monitor. \r\n\r\n Just select an interval and \r\n press Start Monitoring. \r\n\r\n " 
						+ "A high interval means less \r\n load on the DB! \r\n\r\n"
						+ "A history of " + HISTORY_SIZE + " values is kept.");
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
		this.setTitle("Miami Dade CiRM Monitor");
		this.setSize(250, 300);
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
			monitor = new Thread(getMonitorRunnable(), "CirmMonitor");
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
		String message = "<html>Please Enter Password for: " + DB_ID + " <br/>" 
				+ " User: " + DB_USR + "</html>";
		int okCancel = -1;
		do 
		{
			JPanel p = new JPanel(new BorderLayout(5,5));
			JLabel l = new JLabel(message);
			JPasswordField passf = new JPasswordField(30);
			p.add(l, BorderLayout.NORTH);
			p.add(passf, BorderLayout.CENTER);
			okCancel = JOptionPane.showConfirmDialog(this, p, "Cirm Monitor - Connection Pass", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
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
		ods.setURL(DB_URL);
		ods.setUser(DB_USR);
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
		List<Integer> serviceRequestCounts;  
		List<String> serviceRequestMaxCaseNumber;
		List<Double> serviceRequestPerMin;
		List<Double> serviceRequestUpdatesPerMin;
		
		double serviceRequestPerMinTotal = 0; 

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
			serviceRequestCounts  = new ArrayList<Integer>();
			serviceRequestPerMin  = new ArrayList<Double>();
			serviceRequestUpdatesPerMin  = new ArrayList<Double>();
			serviceRequestMaxCaseNumber = new ArrayList<String>();
			probeTimes = new ArrayList<Date>();
			serviceRequestPerMinTotal = 0; 
		}

		public synchronized int getIntervalSecs() 
		{
			return intervalSecs;
		}

		public synchronized void addProbe(int srCount, String maxCaseNumber, int updatedSrsInInterval)
		{
			int prevServiceRequestCount = srCount;
			int firstServiceRequestCount = srCount;
			int intervals = serviceRequestCounts.size();
			serviceRequestCounts.add(srCount);
			if (intervals > 0) {
				prevServiceRequestCount = serviceRequestCounts.get(intervals -1);
				firstServiceRequestCount = serviceRequestCounts.get(0);
			} 
			else
				startTime = new Date();
			double curServiceRequestPerMin = (srCount - prevServiceRequestCount) * (60.0 / intervalSecs); 
			serviceRequestPerMin.add(curServiceRequestPerMin);
			probeTimes.add(new Date());
			serviceRequestPerMinTotal = (srCount - firstServiceRequestCount) / (double)(intervals > 0? intervals : 1) * (60.0 / intervalSecs);
			if (intervals > 0) beep(curServiceRequestPerMin);
			serviceRequestMaxCaseNumber.add(maxCaseNumber);
			serviceRequestUpdatesPerMin.add(updatedSrsInInterval * (60.0 / intervalSecs));
		}

		public void beep(double serviceRequestsPerMin) 
		{
			if (serviceRequestsPerMin < BEEP_BELOW_SR_PER_MIN) CirmMonitor.beep();
		}

		public synchronized int getServiceRequestTotalAdded() 
		{
			if (serviceRequestCounts.size() >= 2)
				return serviceRequestCounts.get(serviceRequestCounts.size() - 1) - serviceRequestCounts.get(0);
			else 
				return 0;
		}
		
		public synchronized String toString() 
		{
			StringBuffer s = new StringBuffer(2000);
			s.append("Cirm Statistics at :" + dateFormat.format(new Date()) + "\r\n\r\n");
			s.append("Total SRs: " + serviceRequestCounts.get(serviceRequestCounts.size() - 1) 
					+ " Added SRs: " + getServiceRequestTotalAdded() + " \r\n");
			s.append("Total SRs/min: " + nf.format(serviceRequestPerMinTotal) + " SR/min "
					+ nf.format(serviceRequestPerMinTotal * 60 ) + " SRs/h \r\n");
			if (startTime != null) 
			{
				s.append(" since: " + dateFormat.format(startTime) + "\r\n");
			}
			s.append("Time \tSRs \tSRs/Min \tSRUpds/Min \tMaxCase\r\n");
			for (int i = serviceRequestCounts.size() - 1; (i > serviceRequestCounts.size() - HISTORY_SIZE && i >= 0); i--)
			{
				s.append("" + timeFormat.format(probeTimes.get(i)) + "\t" + serviceRequestCounts.get(i)
						+ "\t" + nf.format(serviceRequestPerMin.get(i))
						+ "\t" + nf.format(serviceRequestUpdatesPerMin.get(i))
						+ "\t" + serviceRequestMaxCaseNumber.get(i) + "\r\n");
			}
			return s.toString();
		}
		
		public synchronized void retrieveDataFrom(Connection conn) throws SQLException
		{
			java.sql.Statement s = conn.createStatement();
			ResultSet rs = s.executeQuery("SELECT COUNT(*), MAX(SR_REQUEST_ID) FROM CIRMSCHM.CIRM_SR_REQUESTS ");
			rs.next();
			int i = rs.getInt(1);
			String cnum = rs.getString(2);
			rs.close();
			s.close();
			java.sql.Timestamp now = new java.sql.Timestamp(System.currentTimeMillis());
			java.sql.Timestamp nowMinusInterval = new java.sql.Timestamp(now.getTime() - getIntervalSecs() * 1000); 
			PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM CIRMSCHM.CIRM_SR_REQUESTS WHERE UPDATED_DATE > ? AND UPDATED_DATE <= ? ");
			ps.setTimestamp(1, nowMinusInterval);
			ps.setTimestamp(2, now);
			rs = ps.executeQuery();
			rs.next();
			int nrUpdatedInInterval = rs.getInt(1);
			addProbe(i, cnum, nrUpdatedInInterval);
			rs.close();
			ps.close();
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
		new CirmMonitor();
	}

}
