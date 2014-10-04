package gov.miamidade.cirm.maintenance;

import org.sharegov.cirm.utils.ThreadLocalStopwatch;

/**
 * A Rule consisting of a Condition to be evaluated 
 * and an Action to be executed.
 * 
 * @author SABBAS
 *
 */
public class Rule {
	
	private Condition condition;
	private Action action;
	private String name = "rule";
	
	public String getName()
	{
		return name;
	}

	public void setName(String name)
	{
		this.name = name;
	}

	public Rule(Condition c , Action a)
	{
		this.condition = c;
		this.action = a;
	}
	
	public void ifthen()
	{
		if(condition.eval()){
			action.execute();
		}
	}

	public Condition getCondition()
	{
		return condition;
	}

	public void setCondition(Condition condition)
	{
		this.condition = condition;
	}

	public Action getAction()
	{
		return action;
	}

	public void setAction(Action action)
	{
		this.action = action;
	}
}
