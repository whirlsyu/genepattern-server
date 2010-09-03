package org.genepattern.server;

import org.apache.log4j.Logger;
import org.genepattern.server.webservice.server.dao.AnalysisDAO;
import org.genepattern.webservice.JobInfo;
import org.genepattern.webservice.OmnigeneException;
import org.genepattern.webservice.ParameterFormatConverter;
import org.genepattern.webservice.ParameterInfo;

/**
 * Submit jobs for execution. Consolidates duplicate code invoked via the web client and the soap client.
 * 
 * @author pcarr
 */
public class JobManager {
    private static Logger log = Logger.getLogger(JobManager.class);
    
    /**
     * thrown when a job is not added to the queue.
     * @author pcarr
     */
    public static class JobSubmissionException extends Exception {
        public JobSubmissionException(Throwable t) {
            this("Error adding job to the queue", t);
        }
        public JobSubmissionException(String message, Throwable t) {
            super(message, t);
        }
    }

    static public JobInfo addJobToQueue(int taskID, String userID, ParameterInfo[] parameterInfoArray, Integer parentJobID) 
    throws JobSubmissionException
    {
        JobInfo jobInfo = null;
        try {
            jobInfo = executeRequest(taskID, userID, parameterInfoArray, parentJobID);
            return jobInfo;
        }
        catch (Throwable t) {
            throw new JobSubmissionException(t);
        }
    }

    /**
     * Creates job. Call this fun. if you need JobInfo object
     *
     * @throws TaskIDNotFoundException
     *             TaskIDNotFoundException
     * @throws OmnigeneException
     * @return <CODE>JobIndo</CODE>
     */
    static private JobInfo executeRequest(int taskID, String userID, ParameterInfo[] parameterInfoArray, Integer parentJobID) throws TaskIDNotFoundException {
        JobInfo ji = null;
        String parameter_info = ParameterFormatConverter.getJaxbString(parameterInfoArray);
        AnalysisDAO ds = new AnalysisDAO();
        boolean hasParent = parentJobID != null;
        if (hasParent) {
            ji = ds.addNewJob(taskID, userID, parameter_info, parentJobID);
        } 
        else {
            ji = ds.addNewJob(taskID, userID, parameter_info, -1);
        }

        // Checking for null
        if (ji == null) {
            throw new OmnigeneException(
            "AddNewJobRequest:executeRequest Operation failed, null value returned for JobInfo");
        }

        //TODO: should wakeup the job queue from the calling method
        //CommandManagerFactory.getCommandManager().wakeupJobQueue();

        // Reparse parameter_info before sending to client
        ji.setParameterInfoArray(ParameterFormatConverter.getParameterInfoArray(parameter_info));
        //} 
        //catch (TaskIDNotFoundException taskEx) {
            //HibernateUtil.rollbackTransaction();
            //log.error("AddNewJob(executeRequest) " + taskID, taskEx);
            //throw taskEx;
        //} 
        //catch (Exception ex) {
        //    HibernateUtil.rollbackTransaction();
        //    log.error("AddNewJob(executeRequest): Error ",  ex);
        //    throw new OmnigeneException(ex.getMessage());
        //}
        
        return ji;
    }

}
