package org.opencds.cqf.r4.schedule;

import ca.uhn.fhir.jpa.model.sched.HapiJob;

import java.util.Optional;
import java.util.Map.Entry;

import org.hl7.fhir.r4.model.Duration;
import org.hl7.fhir.r4.model.Extension;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Task;
import org.hl7.fhir.r4.model.Task.TaskIntent;
import org.hl7.fhir.r4.model.Task.TaskPriority;
import org.hl7.fhir.r4.model.Task.TaskStatus;
import org.opencds.cqf.common.helpers.DateHelper;
import org.opencds.cqf.r4.processors.TaskProcessor;
import org.hl7.fhir.r4.model.Timing;
import java.util.Date;
import org.hl7.fhir.r4.model.Type;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class BaseTaskJob implements HapiJob {

    private static final Logger logger = LoggerFactory.getLogger(BaseTaskJob.class);

    private Task task;

    private Timing timing = null;
    private Duration duration = null;

    private Date relativeStartDate = null;
    private Date relativeEndDate = null;
    private Date startDate = null;
    private Date endDate = null;

    private RulerScheduler rulerScheduler;

    private String id = "";

    public BaseTaskJob(Task task) {
        this.task = task;
        this.id = task.getId();
        initTiming();
        initRelativePeriod();
    }

    public BaseTaskJob() {
    }

    private void initTiming() {
        logger.info("Checking for timing extension.");
        if (task.hasExtension("http://hl7.org/fhir/aphl/StructureDefinition/timing")) {
            // if(task.hasExtension()){
            timing = new Timing();
            Extension extension = task.getExtensionByUrl("http://hl7.org/fhir/aphl/StructureDefinition/timing");
            if (extension != null) {
                Type timingType = extension.getValue();
                if (timingType instanceof Timing) {
                    timing = (Timing) timingType;
                    logger.info("Timing is set.");
                    System.out.println("Timing is set.");
                }
            }
        } else {
            logger.info("No extension.");
        }
    }

    private void initRelativePeriod() {
        if (task.hasExtension("http://hl7.org/fhir/aphl/StructureDefinition/offset")) {
            // if(task.hasExtension()){
            duration = new Duration();
            Extension offsetExtension = task.getExtensionByUrl("http://hl7.org/fhir/aphl/StructureDefinition/offset");
            if (offsetExtension != null) {
                Type durationType = offsetExtension.getValue();
                if (durationType instanceof Duration) {
                    duration = (Duration) durationType;
                    logger.info("Duration offset is set.");
                    Extension relativeDateTimeExtension = task.getExecutionPeriod()
                    .getExtensionByUrl("http://hl7.org/fhir/extension-cqf-relativedatetime.html");
                    if (relativeDateTimeExtension != null) {
                        Type extensionValueType = relativeDateTimeExtension.getValue();
                        if (extensionValueType instanceof Reference) {
                            Reference ref = (Reference) extensionValueType;
                            if (ref.hasReference()) {
                                Optional<Entry<String, BaseTaskJob>> foundJobOptional = RulerScheduler.jobs.entrySet().stream()
                                        .filter(entry -> entry.getKey().equals("job-" + ref.getReference().replace("Task/", "")))
                                        .findFirst();
                                if (foundJobOptional.isPresent()) {
                                    //Need to check for before or after
                                    BaseTaskJob relativeJob = foundJobOptional.get().getValue();                                    
                                    relativeStartDate = DateHelper.increaseDate(duration.getUnit(), duration.getValue(), relativeJob.getStartDate());
                                }
                            }
                        }
                    }
                }
            }
        } else {
            logger.info("No extension.");
        }
    }

    public void setTask(Task task) {
        this.task = task;
        this.id = task.getId();
    }
    public Task getTask() {
        return task;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public RulerScheduler getRulerScheduler() {
        return rulerScheduler;
    }

    public void setRulerScheduler(RulerScheduler rulerScheduler) {
        this.rulerScheduler = rulerScheduler;
    }

    public Timing getTiming() {
        return timing;
    }

    public void setTiming(Timing timing) {
        this.timing = timing;
    }

    //https://www.hl7.org/fhir/valueset-task-status.html
    public void setStatus(String statusString) {

        TaskProcessor.updateStatus(this.task, TaskStatus.fromCode(statusString));
    }

    public void setStatus(TaskStatus status) {

        TaskProcessor.updateStatus(this.task, status);
    }


    public TaskStatus getStatus() {

        return this.task.getStatus();
    }

    // cap_small :routine, urgent, asap, stat,
    public void setPriority(String priority) {

        this.task.setPriority(TaskPriority.fromCode(priority));
    }

    public void setPriority(TaskPriority priority) {

        this.task.setPriority(priority);
    }

    public TaskPriority getPriority() {

        return task.getPriority();
    }

    // unknown,  proposal, plan, order, originalorder, reflexorder, fillerorder, instanceorder, option,
    public void setIntent(String intent) {

        this.task.setIntent(TaskIntent.fromCode(intent));
    }

    public void setIntent(TaskIntent intent) {

        this.task.setIntent(intent);
    }

    public TaskIntent getIntent() {

        return task.getIntent();
    }

    public Date getRelativeStartDate() {
        return relativeStartDate;
    }

    public void setRelativeStartDate(Date relativeStartDate) {
        this.relativeStartDate = relativeStartDate;
    }

    @Override
    public abstract void execute(JobExecutionContext jobExecutionContext) throws JobExecutionException;

    public Date getStartDate() {
        return startDate;
    }

    public void setStartDate(Date startDate) {
        this.startDate = startDate;
    }

    public Date getEndDate() {
        return endDate;
    }

    public void setEndDate(Date endDate) {
        this.endDate = endDate;
    }
}

