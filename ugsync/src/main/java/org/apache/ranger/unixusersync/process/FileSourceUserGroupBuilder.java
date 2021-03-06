/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.ranger.unixusersync.process;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.log4j.Logger;
import org.apache.ranger.unixusersync.config.UserGroupSyncConfig;
import org.apache.ranger.usergroupsync.UserGroupSink;
import org.apache.ranger.usergroupsync.UserGroupSource;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.stream.JsonReader;

public class FileSourceUserGroupBuilder  implements UserGroupSource {
	private static final Logger LOG = Logger.getLogger(FileSourceUserGroupBuilder.class) ;

	private Map<String,List<String>> user2GroupListMap     = new HashMap<String,List<String>>();
	private UserGroupSyncConfig      config                = UserGroupSyncConfig.getInstance();
	private String                   userGroupFilename     = null;
	private long                     usergroupFileModified = 0 ;


	public static void main(String[] args) throws Throwable {
		FileSourceUserGroupBuilder filesourceUGBuilder = new FileSourceUserGroupBuilder() ;

		if (args.length > 0) {
			filesourceUGBuilder.setUserGroupFilename(args[0]);
		}
		
		filesourceUGBuilder.init();

		UserGroupSink ugSink = UserGroupSyncConfig.getInstance().getUserGroupSink();
		LOG.info("initializing sink: " + ugSink.getClass().getName());
		ugSink.init();

		filesourceUGBuilder.updateSink(ugSink);
		
		if ( LOG.isDebugEnabled()) {
			filesourceUGBuilder.print(); 
		}
	}
	
	@Override
	public void init() throws Throwable {
		if(userGroupFilename == null) {
			userGroupFilename = config.getUserSyncFileSource();
		}

		buildUserGroupInfo();
	}
	
	@Override
	public boolean isChanged() {
		long TempUserGroupFileModifedAt = new File(userGroupFilename).lastModified() ;
		if (usergroupFileModified != TempUserGroupFileModifedAt) {
			return true ;
		}
		return false;
	}

	@Override
	public void updateSink(UserGroupSink sink) throws Throwable {
		buildUserGroupInfo();

		for (Map.Entry<String, List<String>> entry : user2GroupListMap.entrySet()) {
		    String       user   = entry.getKey();
		    List<String> groups = entry.getValue();

		    sink.addOrUpdateUser(user, groups);
		}
	}

	private void setUserGroupFilename(String filename) {
		userGroupFilename = filename;
	}

	private void print() {
		for(String user : user2GroupListMap.keySet()) {
			LOG.debug("USER:" + user) ;
			List<String> groups = user2GroupListMap.get(user) ;
			if (groups != null) {
				for(String group : groups) {
					LOG.debug("\tGROUP: " + group) ;
				}
			}
		}
	}

	public void buildUserGroupInfo() throws Throwable {
		buildUserGroupList();
		if ( LOG.isDebugEnabled()) {
			print(); 
		}
	}
	
	public void buildUserGroupList() throws Throwable {
		if (userGroupFilename == null){
			throw new Exception("User Group Source File is not Configured. Please maintain in unixauthservice.properties or pass it as command line argument for org.apache.ranger.unixusersync.process.FileSourceUserGroupBuilder");
		}
	
		File f = new File(userGroupFilename);
		
		if (f.exists() && f.canRead()) {
			Map<String,List<String>> tmpUser2GroupListMap = null;
			
			if ( isJsonFile(userGroupFilename) ) {
				tmpUser2GroupListMap = readJSONfile(f);
			} else {
				tmpUser2GroupListMap = readTextFile(f);
			}

			if(tmpUser2GroupListMap != null) {
				user2GroupListMap     = tmpUser2GroupListMap;
				
				usergroupFileModified = f.lastModified() ;
			} else {
				LOG.info("No new UserGroup to sync at this time");
			}
		} else {
			throw new Exception("User Group Source File " + userGroupFilename + "doesn't not exist or readable");
		}
	}
	
	public boolean isJsonFile(String userGroupFilename) {
		boolean ret = false;

		if ( userGroupFilename.toLowerCase().endsWith(".json")) {
			ret = true;
		}

		return ret;
	}
	
	public 	Map<String, List<String>> readJSONfile(File jsonFile) throws Exception {
		Map<String, List<String>> ret = new HashMap<String, List<String>>();

		JsonReader jsonReader = new JsonReader(new BufferedReader(new FileReader(jsonFile)));
		
		Gson gson = new GsonBuilder().create() ;

		ret = gson.fromJson(jsonReader, ret.getClass());
		
		return ret;

	}
	
	public Map<String, List<String>> readTextFile(File textFile) throws Exception {
		
		Map<String, List<String>> ret = new HashMap<String, List<String>>();
		
		String delimiter = config.getUserSyncFileSourceDelimiter();
		 
		CSVFormat csvFormat = CSVFormat.newFormat(delimiter.charAt(0));
		
		CSVParser csvParser = new CSVParser(new BufferedReader(new FileReader(textFile)), csvFormat);
		
		List<CSVRecord> csvRecordList = csvParser.getRecords();
		
		if ( csvRecordList != null) {
			for(CSVRecord csvRecord : csvRecordList) {
				List<String> groups = new ArrayList<String>();
				String user = csvRecord.get(0);
				
				user = user.replaceAll("^\"|\"$", "");
					
				int i = csvRecord.size();
				
				for (int j = 1; j < i; j ++) {
					String group = csvRecord.get(j);
					if ( group != null && !group.isEmpty()) {
						 group = group.replaceAll("^\"|\"$", ""); 
						 groups.add(group);
					}
				}
				ret.put(user,groups);
			 } 
		}

		csvParser.close();

		return ret;
	}

}