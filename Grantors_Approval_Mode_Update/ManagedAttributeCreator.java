package com.dupont.myaccess;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;

import org.apache.commons.logging.impl.Log4JLogger;
import sailpoint.server.InternalContext;
import sailpoint.object.Application;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.ManagedAttribute;
import sailpoint.object.QueryOptions;
import sailpoint.tools.GeneralException;
import sailpoint.api.Terminator;

/**
 * Custom IIQ java class to create managed attributes in IIQ from the given
 * list of entitlement values in a map format
 * 
 * Map must contain:
 * The application attribute: Attribute
 * The entitlemnt value: Value
 * 
 * In addition, the map may optionally contain:
 * The entitlement description: Description
 * The entitlement display name: DisplayName
 * The entitlement owner's myAccessID: Owner
 * 
 * @author gregory.callegari@accenture.com
 *
 */
@SuppressWarnings("rawtypes")
public class ManagedAttributeCreator extends SuperCreator{

	private ArrayList<HashMap> entitlementValues;
	
	/**
	 * 
	 * @param entitlementValues
	 * @param context
	 * @param log
	 * @param appName
	 */
	public ManagedAttributeCreator(ArrayList<HashMap> entitlementValues, InternalContext context, Log4JLogger log, String appName) {
		super(context, log, appName);
		this.entitlementValues = entitlementValues;
	}
	
	/**
	 * 
	 * @return HashMap<String,String> list of the created managed attributes
	 * @throws GeneralException
	 */
	@SuppressWarnings("unchecked")
	public ArrayList<String> createManagedAttributes() throws GeneralException {
		ArrayList<String> createdObjects = new ArrayList<String>();
		for(HashMap item : this.entitlementValues){
			if(item.get("Attribute") != null && item.get("Value") != null){
				String attr = (String) item.get("Attribute");
				String value = (String) item.get("Value");
				String dName = item.get("DisplayName")!=null? (String) item.get("DisplayName") : (String) item.get("Value");
				String desc =  item.get("Description")!=null ? (String) item.get("Description") :(String) item.get("Value");
				String approvalMode = item.get("Approval_Mode")!=null ? (String) item.get("Approval_Mode") : "Both";
				boolean isRequestable = item.get("Is_Requestable")!=null ? (Boolean) item.get("Is_Requestable"): true;
				ArrayList<String> owner = (ArrayList<String>) item.get("Owner");
				String ownerEmail = (String) item.get("Owner_Email");
				String displayName = !dName.isEmpty() ? dName : this.appName + "-" + attr + "-" + value;
				//check to see if already exists and delete if so
				QueryOptions qo = new QueryOptions();
				ArrayList<Filter> filterList = new ArrayList<Filter>();
				filterList.add(Filter.eq("attribute", attr));
				filterList.add(Filter.eq("value", value));
				Filter allFilter = Filter.and(filterList);
				qo.addFilter(allFilter);
				Iterator<Object[]> it = context.search(ManagedAttribute.class, qo, "id");
				while(it.hasNext()) {
					String id = (String) it.next()[0];
					ManagedAttribute existingMA = context.getObjectById(ManagedAttribute.class, id);
					if(this.appName.equals(existingMA.getApplication().getName())) {
						Identity existingOwnerGroup = existingMA.getOwner();
						if(!existingOwnerGroup.getName().equals(this.appName+ "-Owners-Workgroup")) {
							//this managed attribute has a specific workgroup remove it and
							//we will recreate it to account for modifies
							//TODO: failing here on modifiy
							if(existingOwnerGroup.isWorkgroup()) {
								//set owner before removing the workgroup to avoid a foreign key constraint
								Terminator governor = new Terminator(this.context);
								governor.deleteObject(existingOwnerGroup);
							}
						}
						this.context.removeObject(existingMA);
						this.context.commitTransaction();
					}
				}
				
				ManagedAttribute newManAttr = new ManagedAttribute();
				newManAttr.setApplication(this.context.getObjectByName(Application.class, this.appName));
				newManAttr.setAttribute(attr);
				newManAttr.setValue(value);
				newManAttr.setType("Entitlement");
				newManAttr.setRequestable(isRequestable);
				newManAttr.setDisplayName(displayName);
				newManAttr.setAttribute("approvalMode",approvalMode);				
				if(!desc.isEmpty()){
					newManAttr.addDescription("en_US", desc);
				}
				if(owner!=null && !owner.isEmpty()){
					if(owner.size() == 1) {
						Identity foundOwner = this.context.getObjectByName(Identity.class, owner.get(0));
						newManAttr.setOwner(foundOwner);
					}else{
						//its a workgroup!
						WorkgroupCreator wc = new WorkgroupCreator(owner, this.context, this.appName, this.log, ownerEmail, "entitlement", displayName);
						Identity ownerGroup = wc.createWorkgroup();
						ArrayList<String> ownerGroupName = new ArrayList<String>();
						ownerGroupName.add(ownerGroup.getName());
						createdObjects.add("Workgroup");
						newManAttr.setOwner(ownerGroup);
					}
					
				}else{
					Identity grantorWorkgroup = this.context.getObjectByName(Identity.class, this.appName + "-Grantors-Workgroup");
					if(grantorWorkgroup == null) {		
							Identity ownerWorkgroup = this.context.getObjectByName(Identity.class, this.appName + "-Owners-Workgroup");
							if(ownerWorkgroup == null) {
								Identity admin = this.context.getObjectByName(Identity.class, "spadmin");
								newManAttr.setOwner(admin);
							}else
								newManAttr.setOwner(ownerWorkgroup);													
					}else {
						newManAttr.setOwner(grantorWorkgroup);
					}
				}
				context.saveObject(newManAttr);
				context.commitTransaction();
				}else{
				log.error("Entitlement value " + item + " not valid");
			}
		}
		createdObjects.add("ManagedAttribute");
		return createdObjects;
	}
}
