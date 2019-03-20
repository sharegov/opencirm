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
package org.sharegov.cirm.utils;

import java.lang.reflect.Method;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import org.hypergraphdb.util.RefResolver;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLObject;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.SWRLArgument;
import org.semanticweb.owlapi.model.SWRLAtom;
import org.semanticweb.owlapi.model.SWRLBuiltInAtom;
import org.semanticweb.owlapi.model.SWRLIndividualArgument;
import org.semanticweb.owlapi.model.SWRLVariable;
import org.sharegov.cirm.Refs;
import org.sharegov.cirm.rules.EvaluateAtom;
import org.sharegov.cirm.workflows.AppliedRule;
import org.sharegov.cirm.workflows.AtomValue;

public class EvalUtils
{
	static AtomicLong implicationCount = new AtomicLong(0);
	
	public static IRI newImplicationVar()
	{
		return IRI.create(Refs.nameBase.resolve() + "/variable/implication#" + 
				implicationCount.incrementAndGet());	
	}
	
	public static boolean isImplicationVar(IRI var)
	{
		return var.toString().startsWith(Refs.nameBase.resolve() + "/variable/implication#");
	}
	
	/**
	 * Global variables are identified as such by a naming convention: they start
	 * with a capital letter.
	 */
	public static boolean isVarGlobal(IRI var)
	{
		return Character.isUpperCase(var.getFragment().charAt(0));
	}
	
	public static SWRLVariable getDependableVar(SWRLAtom atom, Set<SWRLVariable> knownVars)
	{
		if (atom instanceof SWRLBuiltInAtom)
		{
			SWRLArgument arg = null;
			for (SWRLArgument a : atom.getAllArguments()) { arg = a;}
			if (arg instanceof SWRLVariable)
				return (SWRLVariable)arg;
			else
				return null;
		}
		if (atom.getAllArguments().size() != 2)
			return null;
		Iterator<SWRLArgument> i = atom.getAllArguments().iterator();
		SWRLArgument arg = i.next();
		if (arg instanceof SWRLVariable)
		{
			if (!knownVars.contains(arg))
				return null;
		}
		else if (! (arg instanceof SWRLIndividualArgument) )
			return null;
		arg = i.next();
		if (! (arg instanceof SWRLVariable))
			return null;
		return (SWRLVariable)arg;
	}
	
	public static Method findMethod(Class<?> implClass, Class argClass)
	{
		Method m = null;
		try 
		{
			m = implClass.getMethod("apply", argClass); 
		}
		catch (NoSuchMethodException ex)
		{ 
			for (Class inter : argClass.getInterfaces())
			{
				m = findMethod(implClass, inter);
				if (m != null) break;
			}
			if (m == null && argClass.getSuperclass() != null)
				m = findMethod(implClass, argClass.getSuperclass());
		}
		return m;
	}

	/**
	 * Implements dynamic dispatching to a method of a single argument. 
	 * The impl parameter must be an instance of class with a method
	 * called 'apply' taking a single argument and possibly overloaded
	 * for several different concrete types of that argument. This will
	 * to call the overloaded method the matches the actual argument's type
	 * most closely, as per the 'findMethod' function above. An exception
	 * is throw if no appropriate method could be found at all.
	 */
	public static <T> T dispatch(Object impl, Object arg)
	{
		Method m = null;
		if (arg == null)
			m = findMethod(impl.getClass(), Object.class);
		else
			m = findMethod(impl.getClass(), arg.getClass());
		if (m != null)
		{
			try { return (T)m.invoke(impl, arg); }
			catch (Exception ex) { throw new RuntimeException(ex); }
		}
		else
		{
			throw new RuntimeException("Can't find appropriate apply() method for "  +impl.getClass() 
					+ " and " + arg);
		}
	}	
}
