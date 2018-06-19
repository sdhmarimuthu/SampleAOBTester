package com.dupont.myaccess;
import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;

import org.apache.commons.logging.impl.Log4JLogger;
import sailpoint.server.InternalContext;
import sailpoint.object.Application;
import sailpoint.object.AttributeDefinition;
import sailpoint.object.CorrelationConfig;
import sailpoint.object.Identity;
import sailpoint.object.Schema;
import sailpoint.object.TaskDefinition;
import sailpoint.tools.GeneralException;

/**
 * Custom IIQ java class to create a new application object
 * 
 * @author gregory.callegari@accenture.com
 *
 */
public class ApplicationCreator extends SuperCreator{
	
	//private final String filePath = "/root/Desktop/";
	private final String filePath ="C:/Users/sudha.rani.marimuthu/Documents/11565901/WIP/Exports";
	
	private String appOwner;
	private String appDesc;
	private String correlatingAttribute;
	private String identityAttribute;
	private ArrayList<String> attributes;
	private ArrayList<String> entitlements;
	private ArrayList<String> requestableAttrs;
	private ArrayList<String> appOwners;
	private ArrayList<String> appImplementers;
	private String ownerEmail;
	private String implementerEmail;
	private boolean isSOMConnected;
	private String SOMDetails;
	private ArrayList<String> appGrantors;
	private String appGrantorsEmail;
	
	/**
	 * Constructor
	 * @param context
	 * @param log
	 * @param appName
	 * @param config
	 */
	@SuppressWarnings({ "rawtypes", "unchecked" })
	public ApplicationCreator(InternalContext context, Log4JLogger log, String appName, HashMap config) {
		//TODO: Account for file update frequency
		super(context,log,appName);
		this.appOwner = (String) config.get("App_Owner");
		this.appDesc = (String) config.get("App_Desc");
		this.correlatingAttribute = (String) config.get("App_CorrelatingAttr");
		this.identityAttribute = (String) config.get("App_IdentityAttr");
		this.attributes = (ArrayList<String>) config.get("App_Attrs");
		this.entitlements = (ArrayList<String>) config.get("App_Entitlements");
		this.requestableAttrs = (ArrayList<String>) config.get("App_Requestable_Attrs");
		this.appOwners = (ArrayList<String>) config.get("App_Owners");
		this.appImplementers = (ArrayList<String>) config.get("App_Implementers");
		this.ownerEmail = (String) config.get("App_Owner_Email");
		this.implementerEmail = (String) config.get("App_Implementer_Email");
		this.isSOMConnected = (boolean) config.get("is_SOM_Connected");
		this.SOMDetails =(String) config.get("SOM_Details");	
		this.appGrantors =(ArrayList<String>) config.get("App_Grantors");
		this.appGrantorsEmail =(String) config.get("App_Grantors_Email");
	}
	
	/**
	 * Create a new delimited file application
	 * 
	 * @return map containing the created file names
	 * @throws GeneralException
	 */
	public ArrayList<String> createDelimitedFileApplication() throws GeneralException {
		String fileName = this.deriveFileName(this.appName);
		ArrayList<String> createdObjects = new ArrayList<String>();
		
		//create correlation config
		CorrelationConfigCreator ccc = new CorrelationConfigCreator(this.correlatingAttribute, this.context, this.log, fileName);
		Object[] createdCorrelation = ccc.createCorrelationConfig();
		CorrelationConfig cc = (CorrelationConfig) createdCorrelation[0];
		if((boolean)createdCorrelation[1]) {
			createdObjects.add("CorrelationConfig");
		}
		
		//create owner workgroup
		WorkgroupCreator owc = new WorkgroupCreator(this.appOwners, this.context, this.appName, this.appOwner, this.log, this.ownerEmail, "owner");
		
		Identity ownerWorkgroup = owc.createWorkgroup();;
		createdObjects.add("Workgroup");
		
		
		//create implementer workgroup
		WorkgroupCreator iwc = new WorkgroupCreator(this.appImplementers,this.context,this.appName,this.log,this.implementerEmail, "implementer");
		
		Identity implementerWorkgroup = iwc.createWorkgroup();
		List<Identity> remediators = new ArrayList<Identity>();
		remediators.add(implementerWorkgroup);
		
		//create grantors workgroup
		WorkgroupCreator gwc = new WorkgroupCreator(this.appGrantors,this.context,this.appName,this.log,this.appGrantorsEmail, "grantor");
		
		Identity grantorsWorkgroup = gwc.createWorkgroup();
		List<Identity> grantors = new ArrayList<Identity>();
		grantors.add(grantorsWorkgroup);
		
		Application newApp = this.context.getObjectByName(Application.class, this.appName) == null ? new Application() : this.context.getObjectByName(Application.class, this.appName);
		//set application attributes
		newApp.setName(this.appName);
		newApp.addDescription("en_US",this.appDesc);
		newApp.setAttribute("file",fileName);
		newApp.setAttribute("delimiter",",");
		newApp.setAttribute("filetransport","local");
		newApp.setAttribute("filterEmptyRecords",true);
		newApp.setAttribute("hasHeader",true);
		newApp.setAttribute("isSOMConnected",isSOMConnected);
		newApp.setAttribute("SOMDetails", SOMDetails);
		newApp.setConnector("sailpoint.connector.DelimitedFileConnector");
		newApp.setType("DelimitedFile");
		newApp.setOwner(ownerWorkgroup);
		newApp.setRemediators(remediators);
		newApp.setAccountCorrelationConfig(cc);
		List<Schema> schemaList = this.generateSchema();
		newApp.setSchemas(schemaList);
		this.context.saveObject(newApp);
		this.context.commitTransaction();
		ArrayList<String> appNames = new ArrayList<String>();
		appNames.add(newApp.getName());
		createdObjects.add("Application");
		//create the aggregation task
		AggregationTaskCreator agtsk = new AggregationTaskCreator(this.context, this.log, this.appName);
		TaskDefinition task = agtsk.createAccountAggregationTask();
		if(task != null) {
			ArrayList<String> taskNames = new ArrayList<String>();
			taskNames.add(task.getName());
			createdObjects.add("TaskDefinition");
		}
		
		return createdObjects;
	}
	
	
	/**
	 * given the application name, derive the name of the csv file
	 * @param appName
	 * @return
	 */
	private String deriveFileName(String appName) {
		String fileName = this.filePath;
		String[] appParts = appName.split(" ");
		for(int i = 0; i < appParts.length; i++) {
			if(i > 0) {
				fileName += "-";
			}
			fileName += appParts[i];
		}
		fileName += ".csv";
		return fileName;
	}
	
	/**
	 * 
	 * @return the application schema
	 */
	private List<Schema> generateSchema() {
		Schema newSchema = new Schema();
		for(String attr : this.attributes){
			AttributeDefinition currentAttr = new AttributeDefinition(attr, "string");
			if(this.entitlements.contains(attr)){
				currentAttr.setEntitlement(true);
				currentAttr.setMultiValued(true);
			}
			if(this.requestableAttrs.contains(attr)){
				currentAttr.setManaged(true);
			}
			newSchema.addAttributeDefinition(currentAttr);
		}
		newSchema.setDisplayAttribute(this.correlatingAttribute);
		newSchema.setIdentityAttribute(this.identityAttribute);
		newSchema.setObjectType("account");
		newSchema.setNativeObjectType("account");
		List<Schema> schemaList = new ArrayList<Schema>();
		schemaList.add(newSchema);
		return schemaList;
	}
	
	public static String testImport() {
		return "success";
	}

}
