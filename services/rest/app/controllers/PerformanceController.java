/**
 * TrinhTV3@fsoft.com.vn
 */
package controllers;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;

import org.ats.common.MapBuilder;
import org.ats.common.PageList;
import org.ats.service.report.Report;
import org.ats.service.report.ReportService;
import org.ats.service.report.ReportService.Type;
import org.ats.services.OrganizationContext;
import org.ats.services.executor.ExecutorService;
import org.ats.services.executor.job.AbstractJob;
import org.ats.services.executor.job.PerformanceJob;
import org.ats.services.organization.acl.Authenticated;
import org.ats.services.organization.entity.fatory.ReferenceFactory;
import org.ats.services.performance.JMeterScript;
import org.ats.services.performance.JMeterScriptReference;
import org.ats.services.performance.JMeterScriptService;
import org.ats.services.performance.PerformanceProject;
import org.ats.services.performance.PerformanceProjectFactory;
import org.ats.services.performance.PerformanceProjectService;

import play.Logger;
import play.libs.Json;
import play.mvc.Controller;
import play.mvc.Result;
import actions.CorsComposition;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.inject.Inject;
import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;

/**
 * @author TrinhTV3
 *
 */
@CorsComposition.Cors
@Authenticated
public class PerformanceController extends Controller {
  
  @Inject
  private JMeterScriptService jmeterService;
  
  @Inject
  private PerformanceProjectFactory projectFactory;
  
  @Inject
  private PerformanceProjectService projectService;
  
  @Inject
  private ReferenceFactory<JMeterScriptReference> jmeterReferenceFactory;
  
  @Inject OrganizationContext context;
  
  @Inject ExecutorService executorService;
  
  @Inject ReportService reportService;
  
  private SimpleDateFormat formater = new SimpleDateFormat("dd/MM/yyyy HH:mm");
  
  public Result list() {
    PageList<PerformanceProject> list = projectService.list();
    ArrayNode array = Json.newObject().arrayNode();
    
    
    while(list.hasNext()) {
      for (PerformanceProject project : list.next()) {
        project.put("type", "performance");
        project.put("totalScripts", jmeterService.getJmeterScripts(project.getId()).count());
        
        PageList<AbstractJob<?>> pages = executorService.query(new BasicDBObject("project_id", project.getId()).append("status", AbstractJob.Status.Completed.toString()));
        pages.setSortable(new MapBuilder<String, Boolean>("created_date", false).build());
        if (pages.count() > 0) {
          
          AbstractJob<?> lastJob = pages.next().get(0);
          
          project.put("lastRunning", formater.format(lastJob.getCreatedDate()));
          project.put("log", lastJob.getLog());
          project.put("lastJobId", lastJob.getId());
          List<JMeterScriptReference> scripts = ((PerformanceJob) lastJob).getScripts();
          if (scripts.size() > 0) {
            BasicDBList lastScripts = new BasicDBList();
            for (JMeterScriptReference script : scripts) lastScripts.add(script.toJSon());
            project.put("lastScripts", lastScripts);
          }
        }
        array.add(Json.parse(project.toString()));
      }
    }
    
    return ok(array);
  }
  
  public Result create() {
    
    JsonNode json = request().body().asJson();
    String name = json.get("name").asText();
    
    PerformanceProject project = projectFactory.create(name);
    projectService.create(project);
    return ok(project.getId());
  }
  
  public Result get(String projectId) {
    
    PerformanceProject project = projectService.get(projectId);
    if (project == null) return status(404);
    
    PageList<JMeterScript> pages = jmeterService.getJmeterScripts(projectId);
    ArrayNode arrayScript = Json.newObject().arrayNode();
    List<JMeterScript> list;
    
    while (pages.hasNext()) {
      
      list = pages.next();
      for (JMeterScript script : list) {
        
        arrayScript.add(Json.parse(script.toString()));
      }
    }
    
    PageList<AbstractJob<?>> pageJobs = executorService.query(new BasicDBObject("project_id", projectId));
    pageJobs.setSortable(new MapBuilder<String, Boolean>("created_date", false).build());
    
    ArrayNode arrayJob = Json.newObject().arrayNode();
    while (pageJobs.hasNext()) {
      List<AbstractJob<?>> listJobs = pageJobs.next();
      
      for (AbstractJob<?> job : listJobs) {
        
        job.put("created_date", formater.format(job.getCreatedDate()));
        job.put("creator", project.getCreator().getId());
        arrayJob.add(Json.parse(job.toString()));
      }
    }
    
    PageList<AbstractJob<?>> jobFirstList = executorService.query(new BasicDBObject("project_id", project.getId()), 1);
    jobFirstList.setSortable(new MapBuilder<String, Boolean>("created_date", false).build());
    
    if (jobFirstList.count() > 0) {
      AbstractJob<?> lastRunningJob = jobFirstList.next().get(0);
      project.put("last_running", formater.format(lastRunningJob.getCreatedDate()));
      project.put("jobs", arrayJob.toString());
      project.put("lastScripts", lastRunningJob.get("scripts"));
      project.put("log", lastRunningJob.getLog());
    }
    
    project.put("type", "performance");
    project.put("totalScripts", jmeterService.getJmeterScripts(projectId).count());
    return ok(Json.parse(project.toString()));
  }
  
  public Result update() {
    
    JsonNode data = request().body().asJson();
    String id = data.get("id").asText();
    String name = data.get("name").asText();
    
    PerformanceProject project = projectService.get(id);
    
    if (name.equals(project.getName())) {
      return status(304);
    }
    
    project.put("name", name);
    projectService.update(project);

    return status(202, id);
  }
  
  public Result delete() {
    
    String id = request().body().asText();
    
    PerformanceProject project = projectService.get(id);
    
    if (project == null) {
      return status(404);
    }
    
    PageList<AbstractJob<?>> listJob = executorService.query(new BasicDBObject("project_id", id));
    
    while (listJob.hasNext()) {
      List<AbstractJob<?>> list = listJob.next();
      for (AbstractJob<?> job : list) {
        reportService.deleteBy(new BasicDBObject("performane_job_id", job.getId()));
      }
    }
    
    executorService.deleteBy(new BasicDBObject("project_id", id));
    jmeterService.deleteBy(new BasicDBObject("project_id", id));
    projectService.delete(id);
    
    return status(200);
  }
  
  public Result run(String projectId) throws Exception {
    
    JsonNode data = request().body().asJson();
    
    List<JMeterScriptReference> scripts = new ArrayList<JMeterScriptReference>(); 
    JMeterScriptReference ref;
    for (JsonNode json : data) {
      
      ref = jmeterReferenceFactory.create(json.asText());
      scripts.add(ref);
    }
    
    PerformanceProject project = projectService.get(projectId);
    if (project == null) {
      return status(404);
    }
    
    if (project.getStatus() == PerformanceProject.Status.RUNNING) {
      return status(204);
    }
    
    PerformanceJob job = executorService.execute(project, scripts);
    
    ObjectNode node = (ObjectNode) Json.parse(job.toString());
    node.put("created_date", formater.format(job.getCreatedDate()));
    
    return status(200, node);
  }
  
  public Result report(String projectId, String jobId) {
    
    PerformanceJob job = (PerformanceJob) executorService.get(jobId);
    
    ArrayNode totals = Json.newObject().arrayNode();
    
    for (JMeterScriptReference script : job.getScripts()) {
      ArrayNode array = Json.newObject().arrayNode();
      
      try {
        PageList<Report> pages = reportService.getList(jobId, Type.PERFORMANCE, script.getId());
        while (pages.hasNext()) {
          List<Report> list = pages.next();
          
          for (Report report : list) {         
            array.add(Json.parse(report.toString()));         
          }
        }
        totals.add(array);
      } catch (Exception e) {
        
        Logger.info("Can not read reports with this job");
        
        return status(404, jobId);
      }
    }
    
    return status(200, totals);
  }
  
  public Result getReport(String id) {
    
    Report report = reportService.get(id);
    return status(200, Json.parse(report.toString()));
  }
  
  public Result getLastestRun(String projectId) throws Exception {
    BasicDBObject query = new BasicDBObject("project_id", projectId)
    .append("status", AbstractJob.Status.Completed.toString());
    
    PageList<AbstractJob<?>> jobFirstList = executorService.query(query, 1);
    jobFirstList.setSortable(new MapBuilder<String, Boolean>("created_date", false).build());
    
    AbstractJob<?> lastRunningJob = jobFirstList.next().get(0);
    
    String jobId = lastRunningJob.getId();
    
    PerformanceJob job = (PerformanceJob) executorService.get(jobId);
    
    ArrayNode total = Json.newObject().arrayNode();
    ObjectNode object = Json.newObject();
    for (JMeterScriptReference scriptRef : job.getScripts()) {
      
      ArrayNode reports = Json.newObject().arrayNode();
      PageList<Report> listReport = reportService.getList(jobId, Type.PERFORMANCE, scriptRef.getId());
      
      while (listReport.hasNext()) {
        List<Report> list = listReport.next();
        
        for (Report report : list) {
          
          reports.add(Json.parse(report.toString()));
        }
      }
      
      total.add(reports);
      
    }
    object.put("jobId", jobId);
    object.put("total", total);
    
    return status(200, object);
  }
  
}
