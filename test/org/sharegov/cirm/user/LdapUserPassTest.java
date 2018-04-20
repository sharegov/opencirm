/*******************************************************************************
 * Copyright 2018 Miami-Dade County
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
package org.sharegov.cirm.user;

import static org.junit.Assert.*;

import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Tests for Ldap Sha, sha2, ssha and ssha2 authentication.
 * 
 * @author Thomas Hilpold
 *
 */
public class LdapUserPassTest {
	
	//TEST DATA to pass
	static String[] testPass = new String[] {
			"test", "{sha}qUqP5cyxm6YcTAhz05Hph5gvu9M=",
			"test", "{sha256}n4bQgYhMfWWaL+qgxVrQFaO/TxsrC4Is0V1sFbDwCgg=",
			"test", "{ssha}3v9BnFIaO3i6I08DNQ2xJa5bXpsZabtMChkNvQ==",
			"test", "{ssha256}178FQQ13u4AbhlqmsApRbg04XCFvcEPlF1oPO2Btmw5ZuLYlvnCv5A==",
			"test", "{ssha384}i5C0jxgshYfQZEeq4ZUxnhwaqc9KyRNuU4T4D0pvgx+fVv7CBUT4fWNYm/Gsm8E75ChWpY7Tq18=",
			"test", "{ssha512}d+9QrAELSolfr5zH80HRrJ+g9zHnI31lRDev3X3EHmMWVfUk0HMjG/aQNZkbo/F6sw7uV4O2ODQeGcKyBOg4dhN9CdRF+MHH",
			"311HubGo", "{ssha256}IawPgd2Z3wJu3bWX9z1g2SJSrRHBaCM8H1bcADGQUqnrJ4jPy710N8TZiePiaO1KqZh+1dgvsnnaRRqaEpe0vQ==" 
	};
	
	
	//TEST DATA to fail
	static String[] testFail = new String[] {
			"test1", "{sha}qUqP5cyxm6YcTAhz05Hph5gvu9M=",
			"test2", "{sha256}n4bQgYhMfWWaL+qgxVrQFaO/TxsrC4Is0V1sFbDwCgg=",
			"test3", "{ssha}3v9BnFIaO3i6I08DNQ2xJa5bXpsZabtMChkNvQ==",
			"test4", "{ssha256}178FQQ13u4AbhlqmsApRbg04XCFvcEPlF1oPO2Btmw5ZuLYlvnCv5A==",
			"test5", "{ssha384}i5C0jxgshYfQZEeq4ZUxnhwaqc9KyRNuU4T4D0pvgx+fVv7CBUT4fWNYm/Gsm8E75ChWpY7Tq18=",
			"test6", "{ssha512}d+9QrAELSolfr5zH80HRrJ+g9zHnI31lRDev3X3EHmMWVfUk0HMjG/aQNZkbo/F6sw7uV4O2ODQeGcKyBOg4dhN9CdRF+MHH",
			"311HubGo", "{ssha256}IawPgd2Z3wJu3bWX9z1g2SJSrRHBaCM8H1bcADGQUqnrJ4jPy710N8TZiePiaO1KqZh+1dgvsnnaRRqaEpe0vR==", // seed length 32
			"311HubGo", "{ssha256}JawPgd2Z3wJu3bWX9z1g2SJSrRHBaCM8H1bcADGQUqnrJ4jPy710N8TZiePiaO1KqZh+1dgvsnnaRRqaEpe0vQ==" // seed length 32
	};

	private static LDAPUserProvider p;
	
	/**
	 * @throws java.lang.Exception
	 */
	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
		p = new LDAPUserProvider();
	}

	@Test
	public void testPass() {
		for (int i = 0; i < testPass.length; i = i + 2) {
			System.out.print("P " + i/2 + " ");
			boolean ok = p.match(testPass[i], testPass[i+1]);
			assertTrue(ok);
			System.out.println("true == " + ok);
		}
	}

	@Test
	public void testFail() {
		for (int i = 0; i < testFail.length; i = i + 2) {
			System.out.print("F " + i/2 + " ");
			boolean ok = p.match(testFail[i], testFail[i+1]);
			assertFalse(ok);
			System.out.println("false == " + ok);
		}
	}

}
