package org.sharegov.cirm.rdb;

import java.net.URL;

import mjson.Json;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.sharegov.cirm.BOntology;
import org.sharegov.cirm.rest.OperationService;
import org.sharegov.cirm.utils.GenUtils;

public class T004_StoreClassificationTest
{
	public final static String SR_FILE = "SRForStoreTest.json";
	static Json boJson;

	@BeforeClass
	public static void setUpBeforeClass() throws Exception
	{
		URL f1 = T004_StoreClassificationTest.class.getResource(SR_FILE);
		boJson = Json.read(GenUtils.readAsStringUTF8(f1));
	}

	@AfterClass
	public static void tearDownAfterClass() throws Exception
	{
	}

	@Before
	public void setUp() throws Exception
	{
	}

	@After
	public void tearDown() throws Exception
	{
	}

	@Test
	public void test()
	{
		RelationalOWLPersister p = OperationService.getPersister();
		BOntology bo = BOntology.makeRuntimeBOntology(boJson);
		OntologyTransformer.DBGX = true;
		p.saveBusinessObjectOntology(bo.getOntology());
		// it already exists, therefore no additional row in classification table expected.
		try
		{
			Thread.sleep(10000);
		} catch (InterruptedException e)
		{
		}
		boJson.set("type", "legacy:ASBITE");
		bo = BOntology.makeRuntimeBOntology(boJson);
		p.saveBusinessObjectOntology(bo.getOntology());
		try
		{
			Thread.sleep(10000);
		} catch (InterruptedException e)
		{
		}
		//No db change should occur here:
		bo = BOntology.makeRuntimeBOntology(boJson);
		p.saveBusinessObjectOntology(bo.getOntology());
		try
		{
			Thread.sleep(10000);
		} catch (InterruptedException e)
		{
		}
		boJson.set("type", "legacy:ASDEATH");
		bo = BOntology.makeRuntimeBOntology(boJson);
		p.saveBusinessObjectOntology(bo.getOntology());
		
		
	}

}
