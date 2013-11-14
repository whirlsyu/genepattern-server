package org.genepattern.server.webapp.rest.api.v1.task;

import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;

import org.apache.log4j.Logger;
import org.genepattern.server.cm.CategoryManager;
import org.genepattern.server.config.ServerConfiguration;
import org.genepattern.server.database.HibernateUtil;
import org.genepattern.server.dm.UrlUtil;
import org.genepattern.server.job.input.choice.ChoiceInfo;
import org.genepattern.server.job.input.choice.ChoiceInfoHelper;
import org.genepattern.server.rest.ParameterInfoRecord;
import org.genepattern.server.user.UserDAO;
import org.genepattern.server.user.UserPropKey;
import org.genepattern.server.webapp.rest.api.v1.Util;
import org.genepattern.server.webservice.server.dao.AdminDAO;
import org.genepattern.server.webservice.server.dao.AdminDAO.TaskNameComparator;
import org.genepattern.server.webservice.server.local.LocalAdminClient;
import org.genepattern.util.LSID;
import org.genepattern.webservice.ParameterInfo;
import org.genepattern.webservice.SuiteInfo;
import org.genepattern.webservice.TaskInfo;
import org.genepattern.webservice.WebServiceException;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.sun.jersey.api.Responses;

/**
 * RESTful implementation of the /task resource.
 * 
 * Example usage, via curl command line.
   <p>To get the task_info for a given task name:
   <pre>
   curl -u test:test http://127.0.0.1:8080/gp/rest/v1/tasks/ComparativeMarkerSelection
   </pre>
   <p>Or by task lsid:
   <pre>
   curl -u test:test http://127.0.0.1:8080/gp/rest/v1/tasks/urn:lsid:broad.mit.edu:cancer.software.genepattern.module.analysis:00044:9
   </pre>
   
   This returns a JSON representation of the task, for example,
   <pre>
{
"href":"http://127.0.0.1:8080/gp/rest/v1/tasks/urn:lsid:broad.mit.edu:cancer.software.genepattern.module.test.analysis:00007:0.1",
"name":"TestJavaWrapper",
"lsid":"urn:lsid:broad.mit.edu:cancer.software.genepattern.module.test.analysis:00007:0.1",
"params":[
  {"text": {
     "attributes": {
       "default_value":"",
       "optional":"on",
       "prefix_when_specified":"--text=",
       "type":"java.lang.String",
       "fileFormat":""}}},
       
   ...
   
  {"file":{
    "attributes":{
      "default_value":"",
      "optional":"on",
      "prefix_when_specified":"--file=",
      "MODE":"IN",
      "type":"java.io.File",
      "TYPE":"FILE",
      "fileFormat":""}}}
]
}

   </pre>
 *
 * The value of the lower-case 'type' attribute should be used when testing for the type of input parameter.
 * Here are some example values:
   <pre>
"type":"java.io.File"
"type":"java.lang.String"
"type":"java.lang.Integer"
"type":"java.lang.Float"
"type":"DIRECTORY"
"type":"PASSWORD"
   </pre>
 *   
 * 
 * @author pcarr
 *
 */
@Path("/"+TasksResource.URI_PATH)
public class TasksResource {
    final static private Logger log = Logger.getLogger(TasksResource.class);
    final static public String URI_PATH="v1/tasks";
    
    public static String getTaskInfoPath(final HttpServletRequest request, final TaskInfo taskInfo) {
        String rootPath=UrlUtil.getGpUrl(request);
        if (!rootPath.endsWith("/")) {
            rootPath += "/";
        }
        rootPath += "rest/";
        rootPath += URI_PATH + "/" + taskInfo.getLsid();
        return rootPath;
    }
    
    /**
     * Get the relative path, relative to the root REST API end point, to GET the choiceInfo for the given parameter for the given task.
     * 
     * @param taskInfo
     * @param pname
     * @return
     */
    public static String getChoiceInfoPath(final HttpServletRequest request, final TaskInfo taskInfo, final String pname) { 
        // at the moment, (circa GP 3.7.0), the task LSID and the parameter name will be valid URI path components
        // if this ever changes we should encode them
        //String path = URI_PATH + "/" + UrlUtil.encodeURIcomponent( taskInfo.getLsid() ) + "/" + UrlUtil.encodeURIcomponent( pname ) + "/choiceInfo.json";
        String path = getTaskInfoPath(request, taskInfo) + "/" + pname  + "/choiceInfo.json";
        return path;
    }
    
    /**
     * Rapid prototype method to get the latest version of all installed tasks in json format,
     * for use by the new Modules & Pipelines search panel.
     * 
     * Example usage:
     * <pre>
     * curl -u test:test http://127.0.0.1:8080/gp/rest/v1/tasks/all.json >> all_modules.json
     * </pre>
     * 
     *  
     * @param request
     * @return
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("all.json")
    public Response getAllTasks(final @Context HttpServletRequest request) {
        ServerConfiguration.Context userContext = Util.getUserContext(request);
        final String userId = userContext.getUserId();
        
        final boolean isInTransaction = HibernateUtil.isInTransaction();
        try {
            // Get the map of the latest tasks
            final AdminDAO adminDao = new AdminDAO();
            TaskInfo[] allTasks = adminDao.getAllTasksForUser(userId);
            final Map<String, TaskInfo> latestTasks = adminDao.getLatestTasks(allTasks);
            
            // Apply tags to the taskInfos
            applyTaskTags(latestTasks, userContext);
            
            // Transform the latest task map to an array and sort it
            TaskInfo[] tasksArray = (TaskInfo[]) latestTasks.values().toArray(new TaskInfo[0]);
            Arrays.sort(tasksArray, new AdminDAO.TaskNameComparator());
            
            // Return the JSON object
            JSONArray jsonArray = new JSONArray();
            for(final TaskInfo taskInfo : tasksArray) {
                JSONObject jsonObj = asJson(taskInfo);
                jsonArray.put(jsonObj);
            }
            return Response.ok().entity(jsonArray.toString()).build();
        }
        catch (Throwable t) {
            log.error(t);
            final String errorMessage="Error constructing json response for all.json: "+
                    t.getLocalizedMessage();
            return Response.serverError().entity(errorMessage).build();
        }
        finally {
            if (!isInTransaction) {
                HibernateUtil.closeCurrentSession();
            }
        }
    }
    
    /**
     * Prototype method to get a json array of suites
     * May belong in this servlet or may later be moved elsewhere?
     * Is a suite a task like a module or pipeline? It is a resource with an LSID.
     * @param request
     * @return
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("suites.json")
    public Response getAllSuites(@Context HttpServletRequest request) {
        final boolean isInTransaction = HibernateUtil.isInTransaction();
        try {
            // Get the latest suites
            final AdminDAO adminDao = new AdminDAO();
            SuiteInfo[] allSuites = adminDao.getLatestSuites();
            
            // Return the JSON object
            JSONArray jsonArray = new JSONArray();
            for (SuiteInfo suiteInfo : allSuites) {
                JSONObject jsonObj = asJson(suiteInfo);
                jsonArray.put(jsonObj);
            }
            return Response.ok().entity(jsonArray.toString()).build();
        }
        catch (Throwable t) {
            log.error(t);
            final String errorMessage="Error constructing json response for suites.json: " + t.getLocalizedMessage();
            return Response.serverError().entity(errorMessage).build();
        }
        finally {
            if (!isInTransaction) {
                HibernateUtil.closeCurrentSession();
            }
        }
    }
    
    /**
     * Prototype call to get a list of all categories
     * @param request
     * @return
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("categories.json")
    public Response getAllCategories(@Context HttpServletRequest request) {
        final boolean isInTransaction = HibernateUtil.isInTransaction();
        try {
            // Get the map of the latest suites
            List<String> categories = CategoryManager.getAllCategories();
            
            // Return the JSON object
            JSONArray jsonArray = new JSONArray();
            for (String category : categories) {
                JSONObject jsonObj = asJson(category);
                jsonArray.put(jsonObj);
            }
            return Response.ok().entity(jsonArray.toString()).build();
        }
        catch (Throwable t) {
            log.error(t);
            final String errorMessage="Error constructing json response for categories.json: " + t.getLocalizedMessage();
            return Response.serverError().entity(errorMessage).build();
        }
        finally {
            if (!isInTransaction) {
                HibernateUtil.closeCurrentSession();
            }
        }
    }
    
    /**
     * 
     * <pre>
     * {
        "lsid": "the full lsid of the module should be here",
        "name": "TheModuleNameGoesHere",
        "description": "The description of the module should go here",
        "version": "14.1.2",
        "documentation": "http://www.google.com",
        "categories": ["yyy", "zzz", "www"],
        "tags": ["xxx", "xxx"]
      }
     * </pre>
     * @param taskInfo
     * @return
     */
    private JSONObject asJson(final TaskInfo taskInfo) throws JSONException {
        JSONObject jsonObj = new JSONObject();
        jsonObj.put("lsid", taskInfo.getLsid());
        jsonObj.put("name", taskInfo.getName());
        jsonObj.put("description", taskInfo.getDescription());
        try {
            final LSID lsid=new LSID(taskInfo.getLsid());
            jsonObj.put("version", lsid.getVersion());
        }
        catch (MalformedURLException e) {
            log.error("Error getting lsid for task.name="+taskInfo.getName(), e);
        }
        jsonObj.put("documentation", getDocLink(taskInfo));
        jsonObj.put("categories", getCategories(taskInfo));
        jsonObj.put("tags", getTags(taskInfo));
        return jsonObj;
    }
    
    /**
     * Wrap suites for the JSON call
     * @param suiteInfo
     * @return
     * @throws JSONException
     */
    private JSONObject asJson(SuiteInfo suiteInfo) throws JSONException {
        JSONObject jsonObj = new JSONObject();
        jsonObj.put("lsid", suiteInfo.getLsid());
        jsonObj.put("name", suiteInfo.getName());
        jsonObj.put("description", suiteInfo.getDescription());
        try {
            final LSID lsid=new LSID(suiteInfo.getLsid());
            jsonObj.put("version", lsid.getVersion());
        }
        catch (MalformedURLException e) {
            log.error("Error getting lsid for suite.name=" + suiteInfo.getName(), e);
        }
        jsonObj.put("documentation", "");
        jsonObj.put("categories", new JSONArray());
        jsonObj.put("tags", new JSONArray());
        return jsonObj;
    }
    
    /**
     * Wrap a single string as a JSON object to be returned.
     * Currently used for wrapping module categories
     * @param string
     * @return
     * @throws JSONException
     */
    private JSONObject asJson(String category) throws JSONException {
        JSONObject jsonObj = new JSONObject();
        jsonObj.put("lsid", "");
        jsonObj.put("name", category);
        jsonObj.put("description", "");
        jsonObj.put("version", "");
        jsonObj.put("documentation", "");
        jsonObj.put("categories", new JSONArray());
        jsonObj.put("tags", new JSONArray());
        return jsonObj;
    }

    private JSONArray getCategories(final TaskInfo taskInfo) {
        ServerConfiguration.Context userContext=null;
        List<String> categories=CategoryManager.getCategoriesForTask(userContext, taskInfo);
        JSONArray json=new JSONArray();
        for(final String cat : categories) {
            json.put(cat);
        }
        return json;
    }
    
    private JSONArray getTags(final TaskInfo taskInfo) {
        List<String> tags = (List<String>) taskInfo.getAttributes().get("tags");
        if (tags != null) {
            return new JSONArray(tags);
        }
        else {
            return new JSONArray();
        }
    }
    
    private void applyTaskTags(Map<String, TaskInfo> tasks, ServerConfiguration.Context context) {
        AdminDAO adminDao = new AdminDAO();
        int recentJobsToShow = Integer.parseInt(new UserDAO().getPropertyValue(context.getUserId(), UserPropKey.RECENT_JOBS_TO_SHOW, "4"));
        TaskInfo[] recentModules = adminDao.getRecentlyRunTasksForUser(context.getUserId(), recentJobsToShow);
        
        for (TaskInfo recent : recentModules) {
            try {
                String baseLsid = new LSID(recent.getLsid()).toStringNoVersion();
                List<String> tagList = new ArrayList<String>();
                tagList.add("recent");
                tasks.get(baseLsid).getAttributes().put("tags", tagList);
            }
            catch (MalformedURLException e) {
                log.error("Error getting an LSID object for: " + recent.getLsid());
            }
        }
    }

    private String getDocLink(final TaskInfo taskInfo) {
        try {
            return "/gp/getTaskDoc.jsp?name=" + URLEncoder.encode(taskInfo.getLsid(), "UTF-8");
        }
        catch (UnsupportedEncodingException e) {
            log.error("Error encoding lsid: " + taskInfo.getLsid());
            return "/gp/getTaskDoc.jsp?name=" + taskInfo.getLsid();
        }
    }
    

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("{taskNameOrLsid}")
    public Response getTask(
            final @Context UriInfo uriInfo,
            final @PathParam("taskNameOrLsid") String taskNameOrLsid,
            final @Context HttpServletRequest request
            ) {
        ServerConfiguration.Context userContext=Util.getUserContext(request);
        final String userId=userContext.getUserId();
        TaskInfo taskInfo = null;
        try {
            taskInfo=getTaskInfo(taskNameOrLsid, userId);
        }
        catch (Throwable t) {
            return Responses.notFound().entity(t.getLocalizedMessage()).build();
        }
        if(taskInfo == null) {
            String errorMessage="No task with task id: " + taskNameOrLsid + " found " + "for user " + userId;
            return Responses.notFound().entity(errorMessage).build();
        }
        
        //form a JSON response, from the given taskInfo
        JSONObject jsonObj=null;
        try {
            jsonObj=new JSONObject();
            String href=getTaskInfoPath(request, taskInfo);
            jsonObj.put("href", href);
            jsonObj.put("name", taskInfo.getName());
            jsonObj.put("lsid", taskInfo.getLsid());
            JSONArray paramsJson=new JSONArray();
            for(ParameterInfo pinfo : taskInfo.getParameterInfoArray()) {
                final JSONObject attributesJson = new JSONObject();
                for(final Object key : pinfo.getAttributes().keySet()) {
                    final Object value = pinfo.getAttributes().get(key);
                    if (value != null) {
                        attributesJson.put(key.toString(), value.toString());
                    }
                }
                final JSONObject attrObj = new JSONObject();
                attrObj.put("attributes", attributesJson);
                final JSONObject paramJson = new JSONObject();
                paramJson.put(pinfo.getName(), attrObj);
                paramsJson.put(paramJson);
            }
            jsonObj.put("params", paramsJson);
        }
        catch (Throwable t) {
            final String errorMessage="Error constructing json response for task="+taskNameOrLsid+": "+
                    t.getLocalizedMessage();
            return Response.serverError().entity(errorMessage).build();
        }
        return Response.ok().entity(jsonObj.toString()).build();        
    }

    /**
     * Get the JSON representation of the choices for a given module input parameter.
     *     GET /rest/v1/tasks/{taskNameOrLsid}/{parameterName}/choiceInfo.json
     *     GET /rest/v1/tasks/DemoRNASeQC/annotation.gtf/choiceInfo.json
     *     curl -u test:test http://127.0.0.1:8080/gp/rest/v1/tasks/DemoRNASeQC/annotation.gtf/choiceInfo.json
     * 
     * Example response for a dynamic drop-down,
     * <pre>
       200 OK
       {
         "href":"http://127.0.0.1:8080/gp/rest/v1/tasks/DemoRNASeQC/annotation.gtf/choiceInfo.json",
         "status":{"flag":"OK", "message": "A user message"},
         "choiceDir":"ftp://ftp.broadinstitute.org/pub/genepattern/rna_seq/referenceAnnotation/gtf",
         "choiceAllowCustomValue":"true", 
         "selectedValue": "ftp://ftp.broadinstitute.org/pub/genepattern/rna_seq/referenceAnnotation/gtf/Arabidopsis_thaliana_Ensembl_TAIR10.gtf",
         "choices": [
           {"value":"ftp://ftp.broadinstitute.org/pub/genepattern/rna_seq/referenceAnnotation/gtf/Arabidopsis_thaliana_Ensembl_TAIR10.gtf","label":"Arabidopsis_thaliana_Ensembl_TAIR10.gtf"},
           {"value":"ftp://ftp.broadinstitute.org/pub/genepattern/rna_seq/referenceAnnotation/gtf/Arabidopsis_thaliana_Ensembl_TAIR9.gtf","label":"Arabidopsis_thaliana_Ensembl_TAIR9.gtf"},
           ...
           {"value": "", label: "" }
           ]
       }
     * </pre>
     * 
     * For a static drop-down, the 'choiceDir' will not be set.
     * 
     * Example status messages,
     *     OK, Initialized from values param (old way)
     *     OK, Initialized from choices param (new way, not dynamic)
     *     OK, Initialized from remote server (url=, date=)
     *     WARN, Initialized from cache, problem connecting to remote server
     *     ERROR, Error in module manifest, didn't initialize choices.
     *     ERROR, Connection error to remote server (url)
     *     ERROR, Timeout waiting for listing from remote server (url, timeout)
     * 
     * @param uriInfo
     * @param taskNameOrLsid
     * @param pname
     * @param request
     * @return
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("{taskNameOrLsid}/{pname}/choiceInfo.json")
    public Response getChoiceInfo(
            final @Context UriInfo uriInfo,
            final @PathParam("taskNameOrLsid") String taskNameOrLsid,
            final @PathParam("pname") String pname,
            final @Context HttpServletRequest request
    ) {
        log.debug("taskNameOrLsid="+taskNameOrLsid);
        log.debug("pname="+pname);
        
        ServerConfiguration.Context userContext=Util.getUserContext(request);
        final String userId=userContext.getUserId();
        TaskInfo taskInfo = null;
        try {
            taskInfo=getTaskInfo(taskNameOrLsid, userId);
        }
        catch (Throwable t) {
            return Responses.notFound().entity(t.getLocalizedMessage()).build();
        }
        if(taskInfo == null) {
            String errorMessage="No task with task id: " + taskNameOrLsid + " found " + "for user " + userId;
            return Responses.notFound().entity(errorMessage).build();
        }
        
        final Map<String,ParameterInfoRecord> paramInfoMap=ParameterInfoRecord.initParamInfoMap(taskInfo);
        if (!paramInfoMap.containsKey(pname)) {
            String errorMessage="No parameter with name="+pname;
            return Responses.notFound().entity(errorMessage).build();
        }
        
        ParameterInfoRecord pinfoRecord=paramInfoMap.get(pname);
        if (!ChoiceInfo.hasChoiceInfo(pinfoRecord.getFormal())) {
            return Responses.notFound().entity(taskInfo.getName()+"."+pname + " does not have a choiceInfo").build();
        }
        
        ChoiceInfo choiceInfo=ChoiceInfo.getChoiceInfoParser().initChoiceInfo(pinfoRecord.getFormal());
        
        try {
            final JSONObject choiceInfoJson=ChoiceInfoHelper.initChoiceInfoJson(request, taskInfo, choiceInfo);            
            final String choiceInfoStr=choiceInfoJson.toString();

            //return the JSON representation of the job
            return Response.ok()
                .entity(choiceInfoStr)
                .build();
        }
        catch (Throwable t) {
            return Response.serverError().entity("Error serializing JSON response: "+t.getLocalizedMessage()).build();
        }
    }
    
    private TaskInfo getTaskInfo(final String taskLSID, final String username) 
    throws WebServiceException 
    {
        return new LocalAdminClient(username).getTask(taskLSID);
    }
}
