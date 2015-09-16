/**
 * 
 */
package controllers;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

import org.ats.common.MapBuilder;
import org.ats.common.PageList;
import org.ats.service.report.Report;
import org.ats.service.report.ReportService;
import org.ats.service.report.ReportService.Type;
import org.ats.service.report.function.SuiteReport;
import org.ats.services.OrganizationContext;
import org.ats.services.executor.ExecutorService;
import org.ats.services.executor.job.AbstractJob;
import org.ats.services.executor.job.KeywordJob;
import org.ats.services.keyword.CaseFactory;
import org.ats.services.keyword.CaseReference;
import org.ats.services.keyword.CaseService;
import org.ats.services.keyword.CustomKeywordService;
import org.ats.services.keyword.KeywordProject;
import org.ats.services.keyword.KeywordProjectFactory;
import org.ats.services.keyword.KeywordProjectService;
import org.ats.services.keyword.SuiteReference;
import org.ats.services.keyword.SuiteService;
import org.ats.services.organization.acl.Authenticated;
import org.ats.services.organization.entity.fatory.ReferenceFactory;

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
 * @author <a href="mailto:haithanh0809@gmail.com">Nguyen Thanh Hai</a>
 *
 * Jul 28, 2015
 */
@CorsComposition.Cors
@Authenticated
public class KeywordController extends Controller {

  @Inject CaseService caseService;
  
  @Inject CaseFactory caseFactory;
  
  @Inject ReferenceFactory<CaseReference> caseRefFactory;
  
  @Inject SuiteService suiteService;
  
  @Inject KeywordProjectFactory keywordProjectFactory;
  
  @Inject OrganizationContext context;
  
  @Inject ReferenceFactory<SuiteReference> suiteRefFactory;
  
  @Inject KeywordProjectService keywordProjectService;
  
  @Inject ExecutorService executorService;
  
  @Inject ReportService reportService;
  
  @Inject CustomKeywordService customKeywordService;
  
  private SimpleDateFormat formater = new SimpleDateFormat("dd/MM/yyyy HH:mm");
  
  public Result list() {
    PageList<KeywordProject> list = keywordProjectService.list();
    ArrayNode array = Json.newObject().arrayNode();
    
    while(list.hasNext()) {
      for (KeywordProject project : list.next()) {
        project.put("type", "keyword");
        project.put("totalSuites", suiteService.getSuites(project.getId()).count());
        project.put("totalCases", caseService.getCases(project.getId()).count());
        
        BasicDBObject query = new BasicDBObject("project_id", project.getId()).append("status", AbstractJob.Status.Completed.toString());
        PageList<AbstractJob<?>> jobList = executorService.query(query, 1);
        jobList.setSortable(new MapBuilder<String, Boolean>("created_date", false).build());
        
        if (jobList.totalPage() > 0) {
          AbstractJob<?> lastJob = jobList.next().get(0);
          project.put("lastRunning", formater.format(lastJob.getCreatedDate()));
          project.put("lastJobId", lastJob.getId());
          project.put("log", lastJob.getLog());
          
          List<SuiteReference> suites = ((KeywordJob) lastJob).getSuites();
          if (suites.size() > 0) {
            BasicDBList lastSuites = new BasicDBList();
            for (SuiteReference suite : suites) lastSuites.add(suite.toJSon());
            project.put("lastSuites", lastSuites);
          }
        }
        array.add(Json.parse(project.toString()));
      }
    }
    return ok(array);
  }
  
  public Result get(String projectId) {
    KeywordProject project = keywordProjectService.get(projectId);
    
    if (project == null) return status(404);
    
    project.put("type", "keyword");
    project.put("totalSuites", suiteService.getSuites(project.getId()).count());
    project.put("totalCases", caseService.getCases(project.getId()).count());
    
    PageList<AbstractJob<?>> jobList = executorService.query(new BasicDBObject("project_id", projectId), 1);
    jobList.setSortable(new MapBuilder<String, Boolean>("created_date", false).build());
    
    if (jobList.totalPage() > 0) {
      AbstractJob<?> lastJob = jobList.next().get(0);
      project.put("lastRunning", formater.format(lastJob.getCreatedDate()));
      project.put("log", lastJob.getLog());
      project.put("lastSuites", lastJob.get("suites"));
      project.put("lastJobId", lastJob.getId());
    }
    
    return ok(Json.parse(project.toString()));
  }

  public Result create() {
    JsonNode json = request().body().asJson();
    String name = json.get("name").asText();
    
    KeywordProject project = keywordProjectFactory.create(context, name);
    keywordProjectService.create(project);
    return status(201, project.getId());
  }
  
  public Result update() {
    JsonNode data = request().body().asJson();
    String id = data.get("id").asText();
    String name = data.get("name").asText();
    
    KeywordProject project = keywordProjectService.get(id);
    
    if (name.equals(project.getString("name"))) {
      return status(304);
    }
    
    project.put("name", name);
    keywordProjectService.update(project);

    return status(202, id);
  }
  
  public Result delete() {
    
    String id = request().body().asText();
    
    KeywordProject project = keywordProjectService.get(id);
    if (project == null) {
      return status(404);
    }
    
    PageList<AbstractJob<?>> pages = executorService.query(new BasicDBObject("project_id", id));
    
    List<AbstractJob<?>> list = null;
    while (pages.hasNext()) {
      list = pages.next();
      for (AbstractJob<?> job : list) {
        reportService.deleteBy(new BasicDBObject("functional_job_id", job.getId()));
      }
    }
    
    executorService.deleteBy(new BasicDBObject("project_id", id));
    suiteService.deleteBy(new BasicDBObject("project_id", id));
    caseService.deleteBy(new BasicDBObject("project_id", id));
    customKeywordService.deleteBy(new BasicDBObject("project_id", id));
    keywordProjectService.delete(id);
    return status(200);
  }
  
  public Result run(String projectId) throws Exception {
    JsonNode data = request().body().asJson();
    List<SuiteReference> suites = new ArrayList<SuiteReference>(data.size());
    for (JsonNode sel : data) {
      suites.add(suiteRefFactory.create(sel.asText()));
    }
    
    KeywordProject project = keywordProjectService.get(projectId);
    if (project == null) return status(404);
    
    if (project.getStatus() == KeywordProject.Status.RUNNING) return status(204);
    
    KeywordJob job = executorService.execute(project, suites);
    return status(201, Json.parse(job.toString()));
  }
  
  public Result report(String projectId,String jobId) throws Exception{                    
    ArrayNode array = Json.newObject().arrayNode();
    Report report = null;
    PageList<Report> pages = null;
    AbstractJob<?> job = executorService.get(jobId);
    if(job.getRawDataOutput() != null) {
      pages = reportService.getList(jobId, Type.FUNCTIONAL, null);
      
      if (pages.totalPage() <= 0) {
        return status(404);
      }
      
      report = pages.next().get(0);
      report.put("created_date", formater.format(job.getCreatedDate()));
      Iterator<SuiteReport> iterator = report.getSuiteReports().values().iterator();
      
      while (iterator.hasNext()) {
        SuiteReport suiteReport = iterator.next();
        Date date = suiteReport.getRunningTime();
        String parseDate = formater.format(date);
        suiteReport.put("running_time", parseDate);
        array.add(Json.parse(suiteReport.toString()));
        
      }
      
      report.put("suite_reports", array.toString());
     
    }
    return status(200, Json.parse(report.toString()));  
  }
  
  public Result listReport(String projectId) throws Exception {
    PageList<AbstractJob<?>> jobtList = executorService.query(new BasicDBObject("project_id", projectId), 1);
    jobtList.setSortable(new MapBuilder<String, Boolean>("created_date", false).build());
    
    ArrayNode array = Json.newObject().arrayNode();
    while(jobtList.hasNext()) {
      
      for(AbstractJob<?> job: jobtList.next()) {
        if(job.getRawDataOutput() != null) {
          ObjectNode obj = Json.newObject();
          
          PageList<Report> pages = reportService.getList(job.getId(), Type.FUNCTIONAL, null);
          Report report = pages.next().get(0);
          
          obj.put("report", Json.parse(report.toString()));
          obj.put("created_date", formater.format(job.getCreatedDate()));
          array.add(obj);
        }
      }
    }

    return ok(array);
  }
  
}
