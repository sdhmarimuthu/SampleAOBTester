package com.dupont.myaccess;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import sailpoint.server.InternalContext;
import sailpoint.api.ObjectUtil;
import sailpoint.object.Identity;
import sailpoint.tools.GeneralException;
import org.apache.commons.logging.impl.Log4JLogger;

/**
 * Custom IIQ java class that creates a new workgroup from the given identities
 * @author gregory.callegari@accenture.com
 *
 */


public class WorkgroupCreator extends SuperCreator{

	private ArrayList<String> identities;
	private String owner;
	private String groupEmail;
	private String groupType;
	private String description;

	/**
	 * Constructor for when the workgroup has an owner
	 * @param identities
	 * @param context
	 * @param appName
	 * @param owner
	 * @param log
	 * @param isOwnerGroup
	 */
	public WorkgroupCreator(ArrayList<String> identities, InternalContext context, String appName, String owner, Log4JLogger log, String groupEmail, String groupType){
		super(context,log, appName);
		this.identities=identities;
		this.owner = owner;
		this.groupEmail = groupEmail;
		this.groupType = groupType;
		this.description = null;
	}

	/**
	 * Constructor for when the workgroup does not have an owner
	 * @param identities
	 * @param context
	 * @param appName
	 * @param log
	 * @param isOwnerGroup
	 */
	public WorkgroupCreator(ArrayList<String> identities, InternalContext context, String appName, Log4JLogger log, String groupEmail, String groupType){
		super(context,log, appName);
		this.identities=identities;
		this.owner = identities.get(0);
		this.groupEmail = groupEmail;
		this.groupType = groupType;
		this.description = null;
	}
	
	/**
	 * Constructor for when there is a description
	 * @param constructor
	 * @param identities
	 * @param context
	 * @param appName
	 * @param owner
	 * @param log
	 * @param groupEmail
	 * @param groupType
	 */
	public WorkgroupCreator(ArrayList<String> identities, InternalContext context, String appName, Log4JLogger log, String groupEmail, String groupType, String description){
		super(context,log, appName);
		this.identities=identities;
		this.owner = identities.get(0);
		this.groupEmail = groupEmail;
		this.groupType = groupType;
		this.description = description;
	}


	/**
	 * 
	 * @return the created workgroup
	 * @throws GeneralException
	 */
	public Identity createWorkgroup() throws GeneralException{
		String workgroupName = "";
		if(this.groupType.equals("owner")) {
			workgroupName = this.appName + "-Owners-Workgroup";
		}else if(this.groupType.equals("implementer")) {
			workgroupName = this.appName + "-Implementers-Workgroup";
		}else if(this.groupType.equals("grantor")) {
			workgroupName = this.appName + "-Grantors-Workgroup";
		}else if(this.groupType.equals("entitlement")) {		
			int i = 1;
			workgroupName = this.appName + "-Asset-Owner-Workgroup-" + i;
			while(this.context.getObjectByName(Identity.class, workgroupName) != null) {
				i++;
				workgroupName = this.appName + "-Asset-Owner-Workgroup-" + i;
			}
		}
		Identity newWorkgroup = null;
		Identity workgroupOwner = null;
		Identity foundWorkgroup = null;
		try{
			foundWorkgroup = this.context.getObjectByName(Identity.class, workgroupName);
			if(foundWorkgroup != null) {
				//workgroup exists so get its member list and diff it with identity list
				ArrayList<String> memberList = new ArrayList<String>();
				Iterator<Object[]> membersIt = ObjectUtil.getWorkgroupMembers(context, foundWorkgroup, Arrays.asList("name"));
				while (membersIt.hasNext()) {
					Object[] thisMemberList = membersIt.next();
					String memberName = (String) thisMemberList[0];
					memberList.add(memberName);
				}
				//diff the lists
				for(String user : memberList) {
					if(!identities.contains(user)) {
						//remove user from the workgroup
						Identity currentUser = context.getObjectByName(Identity.class, user);
						currentUser.remove(foundWorkgroup);
					}
				}
			}
			workgroupOwner = this.context.getObjectByName(Identity.class, owner);
			//if identity does not exist, create a dummy one
			//for reference and workgroup membership purposes
			if(workgroupOwner == null) {
				workgroupOwner = new Identity();
				workgroupOwner.setName(owner);
				workgroupOwner.setAttribute("myAccessID", owner);
				this.context.saveObject(workgroupOwner);
				this.context.commitTransaction();
			}
		}catch(Exception e){
			log.error(e.getMessage());
		}
		newWorkgroup = foundWorkgroup == null ? new Identity() : foundWorkgroup;
		newWorkgroup.setWorkgroup(true);
		newWorkgroup.setName(workgroupName);
		newWorkgroup.setOwner(workgroupOwner);
		if(this.description != null && !this.description.isEmpty()) {
			newWorkgroup.setDescription(this.description);
		}
		if(this.groupEmail != null && !this.groupEmail.isEmpty()) {
			newWorkgroup.setEmail(this.groupEmail);
		}
		this.context.saveObject(newWorkgroup);
		this.context.commitTransaction();
		
		//iterate through list of owners
		for(String owner : identities){
			Identity currentOwner = context.getObjectByName(Identity.class, owner);
			if(currentOwner == null) {
				currentOwner = new Identity();
				currentOwner.setName(owner);
				currentOwner.setAttribute("myAccessID", owner);
				context.saveObject(currentOwner);
				context.commitTransaction();
			}
			currentOwner.add(newWorkgroup);
			context.saveObject(newWorkgroup);
			context.commitTransaction();
		}
		return newWorkgroup;
	}
}