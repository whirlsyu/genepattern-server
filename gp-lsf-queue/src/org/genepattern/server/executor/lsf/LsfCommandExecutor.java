package org.genepattern.server.executor.lsf;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Map.Entry;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

import org.apache.log4j.Logger;
import org.genepattern.server.domain.JobStatus;
import org.genepattern.server.executor.CommandExecutor;
import org.genepattern.server.executor.CommandExecutorException;
import org.genepattern.server.executor.CommandManagerFactory;
import org.genepattern.webservice.JobInfo;
import org.hibernate.Query;
import org.hibernate.Session;

import edu.mit.broad.core.Main;
import edu.mit.broad.core.lsf.LocalLsfJob;
import edu.mit.broad.core.lsf.LsfJob;

public class LsfCommandExecutor implements CommandExecutor {
    private static Logger log = Logger.getLogger(LsfCommandExecutor.class);

    //for submitting jobs to the LSF queue
    private Main broadCore = null;
    private static ExecutorService executor = null;
    private Properties configurationProperties = new Properties();
    
    public void setConfigurationFilename(String filename) {
        log.error("method not implemented, setConfigurationFilename( "+filename+" )");
    }
    
    public void setConfigurationProperties(Properties properties) {
        this.configurationProperties.putAll(properties);
    }
    
    public void start() {
        log.info("Initializing LsfCommandExecSvc ...");
        executor = Executors.newCachedThreadPool();
        try {
            //initialize the GAP_SERVER_ID column of the LSF_JOB table
            String broadCoreEnv = this.configurationProperties.getProperty("broadcore.env", "prod");
            String broadCoreServerName = this.configurationProperties.getProperty("broadcore.server.name");
            if (broadCoreServerName == null) {
                broadCoreServerName = System.getProperty("jboss.server.name");
            }
            if (broadCoreServerName == null) {
                broadCoreServerName = System.getProperty("fqHostName", "localhost");
            }
            System.setProperty("jboss.server.name", broadCoreServerName);
            broadCore = Main.getInstance();
            broadCore.setEnvironment(broadCoreEnv); 
            
            String dataSourceName = this.configurationProperties.getProperty("hibernate.connection.datasource", "java:comp/env/jdbc/gpdb");
            log.info("using hibernate.connection.datasource="+dataSourceName);
            broadCore.setDataSourceName(dataSourceName);
            log.info("setting hibernate options...");
            for(Entry<?,?> entry : configurationProperties.entrySet()) {
                log.info(""+entry.getKey()+": "+entry.getValue());
            }
            broadCore.setHibernateOptions(configurationProperties);

            int lsfCheckFrequency = 60;
            String lsfCheckFrequencyProp = configurationProperties.getProperty("lsf.check.frequency");
            if (lsfCheckFrequencyProp != null) {
                try {
                    lsfCheckFrequency = Integer.parseInt(lsfCheckFrequencyProp);
                }
                catch (NumberFormatException e) {
                    log.error("Invalid value, lsf.check.frequency="+lsfCheckFrequencyProp,e);
                    log.error("Using lsf.check.frequency="+lsfCheckFrequency+" instead");
                }
            }
            log.info("broadCore.setLsfCheckFrequency="+lsfCheckFrequency);
            broadCore.setLsfCheckFrequency(lsfCheckFrequency);

            broadCore.start();
        }
        catch (Throwable t) {
            log.error("Error starting BroadCore: "+t.getLocalizedMessage(), t);
        }
        log.info("done!");
    }

    public void stop() {
        log.info("Stopping LsfCommandExecSvc ...");
        try {
            log.debug("stopping BroadCore...");
            Main.getInstance().stop();
        }
        catch (Throwable t) {
            log.error("Error shutting down BroadCore: "+t.getLocalizedMessage(), t);
        }
        if (executor != null) {
            log.debug("stopping executor...");
            executor.shutdown();
            try {
                if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                    log.error("executor shutdown timed out after 30 seconds.");
                    executor.shutdownNow();
                }
            }
            catch (InterruptedException e) {
                log.error("executor.shutdown was interrupted", e);
                Thread.currentThread().interrupt();
            }
        }
        log.info("done!");
    }

    public void runCommand(String[] commandLine, Map<String, String> environmentVariables, File runDir, File stdoutFile, File stderrFile, JobInfo jobInfo, String stdin) 
    throws CommandExecutorException
    {
        log.debug("Running command for job "+jobInfo.getJobNumber()+". "+jobInfo.getTaskName());
        LsfCommand cmd = new LsfCommand();
        
        Properties lsfProperties = CommandManagerFactory.getCommandManager().getCommandProperties(jobInfo);
        cmd.setLsfProperties(lsfProperties);
        cmd.runCommand(commandLine, environmentVariables, runDir, stdoutFile, stderrFile, jobInfo, stdin);
        LsfJob lsfJob = cmd.getLsfJob();
        lsfJob = submitJob(lsfJob);
        log.debug(jobInfo.getJobNumber()+". "+jobInfo.getTaskName()+" is dispatched.");
    }

    /**
     * Uses the BroadCore library to submit the job to LSF, must handle database transactions in this method.
     * @param lsfJob
     * @return the value returned from LsfWrapper#dispatchLsfJob
     */
    private LsfJob submitJob(final LsfJob lsfJob) throws CommandExecutorException { 
        if (executor == null) {
            log.error("service not started ... ignoring submitJob("+lsfJob.getName()+")");
            return lsfJob;
        }
        Callable<LsfJob> c = new LsfTransactedCallable(lsfJob);
        FutureTask<LsfJob> future = new FutureTask<LsfJob>(c);
        executor.execute(future);
        try {
            LsfJob lsfJobOut = future.get();
            log.debug("submitted job to LSF queue: internalJobId="+lsfJobOut.getInternalJobId()
                    +", lsfId= "+lsfJobOut.getLsfJobId());
        }
        catch (Exception e) {
            throw new CommandExecutorException("Error submitting job to LSF, job #"+lsfJob.getInternalJobId(), e);
        }
        return lsfJob;
    }
    
    public void terminateJob(JobInfo jobInfo) {
        if (jobInfo == null) {
            log.error("Terminating job with null jobInfo!");
            return;
        }
        String jobId = ""+jobInfo.getJobNumber();
        LsfJob lsfJob = getLsfJobByGpJobId(jobId);
        if (lsfJob == null) {
            log.error("Didn't find lsf job for gp job: "+jobId);
            return;
        }
        
        LocalLsfJob localLsfJob = convert(lsfJob);
        boolean waitForExit = true;
        try {
            localLsfJob.cancel(waitForExit);
        }
        catch (InterruptedException e) {
            log.error("Terminating job "+jobId+": InterruptedException", e);
        }
    }
    
    //NOTE: based on assumption that the GenePattern ANALYSIS_JOB.JOB_NO is stored in the LSF_JOB.JOB_NAME column
    private LsfJob getLsfJobByGpJobId(String gpJobId) {
        Session session = this.broadCore.getHibernateSession();
        session.beginTransaction();
        try {
            Query query = session.createQuery("from LsfJob as lsfJob where lsfJob.name = ?");
            query.setString(0, gpJobId);
            List r = query.list();
            if (r.size() == 0) {
                return null;
            }
            if (r.size() == 1) {
                Object obj = r.get(0);
                if (obj instanceof LsfJob) {
                    return (LsfJob) obj;
                }
            }
            return null;
        }
        finally {
            if (session != null) {
                session.close();
            }
        }
    }
    
    //copied private code from BroadCore LsfWrapper.convert
    //    TODO: should update BroadCore
    /**
     * Creates an LocalLsfJob instance from an LsfJob instance. Checks to see what
     * fields are null on the incoming LsfJob, and only sets non-null properties
     * on the LocalLsfJob.
     */
    private LocalLsfJob convert(LsfJob job) {
        LocalLsfJob localJob = new LocalLsfJob();
        localJob.setCommand(job.getCommand());
        localJob.setName(job.getName());
        localJob.setQueue(job.getQueue());
        localJob.setProject(job.getProject());
        localJob.setExtraBsubArgs(job.getExtraBsubArgs());

        if (job.getWorkingDirectory() != null) {
            localJob.setWorkingDir(new File(job.getWorkingDirectory()));
        }

        if (job.getInputFilename() != null) {
            localJob.setInputFile(getAbsoluteFile(job.getWorkingDirectory(),
                    job.getInputFilename()));
        }

        if (job.getOutputFilename() != null) {
            localJob.setOutputFile(getAbsoluteFile(job.getWorkingDirectory(),
                    job.getOutputFilename()));
        }

        if (job.getErrorFileName() != null) {
            localJob.setErrFile(getAbsoluteFile(job.getWorkingDirectory(), job
                    .getErrorFileName()));
        }

        if (job.getLsfJobId() != null) {
            localJob.setBsubJobId(job.getLsfJobId());
        }

        if (job.getStatus() != null) {
            localJob.setLsfStatusCode(job.getStatus());
        }

        return localJob;
    }

    private File getAbsoluteFile(String dir, String filename) {
        if (filename.startsWith(File.separator)) {
            return new File(filename);
        } else {
            return new File(dir, filename);
        }
    }
    
    public int handleRunningJob(JobInfo jobInfo) throws Exception {
        log.debug("handle running job #"+jobInfo.getJobNumber()+" on startup");
        return JobStatus.JOB_PROCESSING;
    }

}


