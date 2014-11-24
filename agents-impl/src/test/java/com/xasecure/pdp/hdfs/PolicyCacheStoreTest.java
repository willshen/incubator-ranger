package com.xasecure.pdp.hdfs;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.junit.Before;
import org.junit.After;
import org.junit.Test;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.xasecure.pdp.config.ConfigWatcher;
import com.xasecure.pdp.config.PolicyRefresher;
import com.xasecure.pdp.model.Policy;
import com.xasecure.pdp.model.PolicyContainer;
import com.xasecure.pdp.model.RolePermission;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;


public class PolicyCacheStoreTest {
	URLBasedAuthDB authDB = null;
	ConfigWatcher watcherDaemon = null;
	PolicyRefresher pr = null;
	PolicyContainer policyContainer=null;
	String url=null;
	String sslConfigFileName=null;
	String lastStoredFileName=null;
	Long refreshInterval =0L;
	private static final Log LOG = LogFactory.getLog(PolicyCacheStoreTest.class);
	@Before
	public void setup(){
		authDB = URLBasedAuthDB.getInstance();
		
	}
	
	@After
	public void teardown(){
		authDB = null;
		PolicyRefresher pr = null;
	}

	@Test	
	public void testHdfsPolicyCacheStore(){
		//Check if the policy cache gets created when agent get created;
		url="dummyurl";
		refreshInterval=10L;
		sslConfigFileName = "dummyConfigFileName.xml";
		lastStoredFileName = System.getProperty("user.home") +"/"+ "haooopPolicyCache.json";
		policyContainer = buildPolicyContainer(
				"/demo/data", 
				1,
				asList("allow"), 
				asList("guest"), 
				asList("sales"),
				null, // ipAddress
				true, // policyEnabled
				true); // auditEnabled
	    authDB.OnPolicyChange(policyContainer);
		pr = spy(new PolicyRefresher(url,refreshInterval,sslConfigFileName,lastStoredFileName));
		pr.setPolicyContainer(policyContainer);
		pr.setPolicyChangeListener(authDB);
		PolicyContainer newPr = readPolicyCache(lastStoredFileName);
		assertEquals(policyToString(policyContainer),policyToString(newPr));
	}

	private static PolicyContainer buildPolicyContainer(String resource,
		int recursiveInd, List<String> accessTypes, List<String> users,
		List<String> groups, List<String> ipAddresses,
		boolean policyEnabled, boolean auditEnabled) {

		PolicyContainer policyContainer = new PolicyContainer();
		policyContainer.setRepositoryName("hadoopdev");

		List<Policy> policies = new ArrayList<Policy>();

		Policy policy = new Policy();
		policy.setResource(resource);
		policy.setRecursiveInd(recursiveInd);
		policy.setPolicyStatus(policyEnabled ? "Enabled" : "NotEnabled");
		policy.setAuditInd(auditEnabled ? 1 : 0);

		List<RolePermission> rolePermissions = new ArrayList<RolePermission>();
		
		RolePermission rolePermission =  new RolePermission();
		
		rolePermissions.add(rolePermission);
		rolePermission.setAccess(accessTypes);
		rolePermission.setUsers(users);
		rolePermission.setGroups(groups);
		rolePermission.setIpAddress(ipAddresses);
		
		policy.setPermissions(rolePermissions);
		
		policies.add(policy);

		policyContainer.setAcl(policies);

		return policyContainer;
	}

	private static Set<String> asSet(String... a) {
		Set<String> vals = new HashSet<String>();
		for (String s : a) {
			vals.add(s);
		}
		return vals;
	}

	private static List<String> asList(String... a) {
		List<String> vals = new ArrayList<String>();
		for (String s : a) {
			vals.add(s);
		}
		return vals;
	}
	
	
	private PolicyContainer readPolicyCache(String lastStoreFileName) {
		BufferedReader jsonString = null;
		try {
			jsonString = new BufferedReader(new FileReader(lastStoredFileName));
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}	                		
    	Gson gson = new GsonBuilder().create();	                    	
    	PolicyContainer newPolicyContainer = gson.fromJson(jsonString, PolicyContainer.class);	 
    	return newPolicyContainer;
	}
	
	private String policyToString(PolicyContainer pc) {
		Gson gson = new GsonBuilder().create() ;
		String policyAsJson = gson.toJson(policyContainer) ;
		return policyAsJson;
	}
	
	
}

