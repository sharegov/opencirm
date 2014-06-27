package gov.miamidade.cirm.maintenance;

import java.util.List;

/**
 * Abstract Script class used as a template for 
 * running data scripts against the database
 * @author SABBAS
 *
 */
public abstract class Script
{
	private boolean writeBeforeAndAfterToFile = false;
	private boolean saveToDatabase = false;
	private boolean readAfterSave = false;
	private long sleepMillisAfterFix = 0;
	
	
	
	
	
	public void doScriptForEach(List<Long> srIds)
	{
		for(Long srId: srIds)
		{
			doScript(srId);
		}
	}
	
	public abstract void doScript(Long srId);
	
	public boolean isWriteBeforeAndAfterToFile()
	{
		return writeBeforeAndAfterToFile;
	}
	public void setWriteBeforeAndAfterToFile(boolean writeBeforeAndAfterToFile)
	{
		this.writeBeforeAndAfterToFile = writeBeforeAndAfterToFile;
	}
	public boolean isSaveToDatabase()
	{
		return saveToDatabase;
	}
	public void setSaveToDatabase(boolean saveToDatabase)
	{
		this.saveToDatabase = saveToDatabase;
	}
	public boolean isReadAfterSave()
	{
		return readAfterSave;
	}
	public void setReadAfterSave(boolean readAfterSave)
	{
		this.readAfterSave = readAfterSave;
	}
	public long getSleepMillisAfterFix()
	{
		return sleepMillisAfterFix;
	}
	public void setSleepMillisAfterFix(long sleepMillisAfterFix)
	{
		this.sleepMillisAfterFix = sleepMillisAfterFix;
	}
}
