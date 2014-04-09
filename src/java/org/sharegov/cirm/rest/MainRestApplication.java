package org.sharegov.cirm.rest;

import java.util.HashSet;
import java.util.Set;
import javax.ws.rs.core.Application;
import org.semanticweb.owlapi.model.OWLNamedIndividual;
import org.sharegov.cirm.OWL;

public class MainRestApplication extends Application
{
	private ClassLoader loader = null;
	private Set<String> classNames = new HashSet<String>();
	
	public MainRestApplication() { }
	public MainRestApplication(ClassLoader loader) { this.loader = loader; }
	
	public void configure(OWLNamedIndividual app)
	{
		for (OWLNamedIndividual I : OWL.objectProperties(app, "hasImplementation"))
			classNames.add(I.getIRI().getFragment());
	}
	
	@Override
	public Set<Class<?>> getClasses()
	{
		try
		{
			Set<Class<?>> S = new HashSet<Class<?>>();
			for (String name : classNames)
				S.add(loader == null ? Class.forName(name) : loader.loadClass(name));
			return S;
		}
		catch (Exception ex)
		{
			throw new RuntimeException(ex);
		}
	}
}
