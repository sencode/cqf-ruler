package org.opencds.cqf.r4.schedule;

import org.hl7.fhir.r4.model.Task;
import org.opencds.cqf.r4.processors.TaskProcessor;
import org.opencds.cqf.r4.providers.TaskProvider;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TaskJob extends BaseTaskJob{

    private static final Logger logger = LoggerFactory.getLogger(TaskJob.class);

    public TaskJob(Task task) {
        super(task);
    }

    public TaskJob() {
    }

    @Override
    public void execute(JobExecutionContext jobExecutionContext) throws JobExecutionException {
        logger.info("Task job started");
        boolean executionFailed = false;

        logger.info(jobExecutionContext.getJobDetail().getDescription());
        BaseTaskJob job = RulerScheduler.jobs.get(jobExecutionContext.getJobDetail().getDescription());

        //execute task
        if(job.getTask().getStatus().equals(Task.TaskStatus.COMPLETED)){
           job.getRulerScheduler().shutdown();
            return;
        }

//        logger.info(RulerScheduler.jobs.get(jobExecutionContext.getJobDetail().getDescription()).getTask().getStatus().toCode());
//        job.setStatus(Task.TaskStatus.COMPLETED);



        try{
            RulerScheduler.taskProcessor.execute(job.getTask());
            //write implementation details here

        }catch(Exception jex){
            executionFailed = true;
           // getTask().setStatus(Task.TaskStatus.FAILED);
            if(jex instanceof JobExecutionException){
                throw jex;
            }
        }

        if(!executionFailed){
          //  getTask().setStatus(Task.TaskStatus.COMPLETED);
        }

        logger.info("Task job finished");

    }


}
