package org.opencds.cqf.r4.schedule;

import org.hl7.fhir.r4.model.Extension;
import org.hl7.fhir.r4.model.Task;
import org.hl7.fhir.r4.model.Timing;
import org.quartz.SchedulerException;

public class Main {

    public static void main(String[] args){
        Task task = new Task();
        task.setStatus(Task.TaskStatus.DRAFT);
        task.setId("testID101");


        Timing timing = new Timing();



        // Extension ext = new Extension();
        // ext.setUrl("http://example.com/extensions#someext");
        // ext.setValue(timing);


        // task.addExtension(ext);

        // TaskJob job = new TaskJob(task);
        // job.setId("testID101");


        // try {
        //     RulerScheduler scheduler = new RulerScheduler(job, "TEST");

        //     scheduler.start();
        // }catch (SchedulerException e){

        //     e.printStackTrace();
        // }
    }
}
