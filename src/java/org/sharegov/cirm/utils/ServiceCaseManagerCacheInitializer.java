package org.sharegov.cirm.utils;

import org.sharegov.cirm.MetaOntology;
import org.sharegov.cirm.legacy.ServiceCaseManager;

public class ServiceCaseManagerCacheInitializer extends Thread {
	private static ServiceCaseManagerCacheInitializer instance = null;
	private static ServiceCaseManager scm = null;
	private static boolean restartReasoner = false;
	private ServiceCaseManagerCacheInitializer (){};
	public void run (){
		System.out.println("Started Service Case Manager Caching task");
		scm.getSRTypes(true, true);
		if (restartReasoner){
			MetaOntology.clearCacheAndSynchronizeReasoner();
			restartReasoner = false;
		}
		System.out.println("Finished Service Case Manager Caching task");
	}
	public synchronized static void startCaching (ServiceCaseManager aScm){
		if (instance == null){
			scm = aScm;
			ServiceCaseManagerCache.getInstance().clear();
			instance = new ServiceCaseManagerCacheInitializer();
			instance.start();
			
		}
	}
	
	public synchronized static void forceRestartCaching (ServiceCaseManager aScm, boolean isRestartReasoner){
		restartReasoner = isRestartReasoner;
		if (instance == null) startCaching (aScm);
		else {
			if (instance.isAlive()){
				try {
					instance.interrupt();
				} catch (Exception e) {
					System.out.println("Faliure to Stop the Service Case Manager Caching task");
					e.printStackTrace();
				} finally {				
					ServiceCaseManagerCache.getInstance().clear();
					if (scm == null) scm = aScm;
					instance.start();
				}
			}
		}
	}
}
