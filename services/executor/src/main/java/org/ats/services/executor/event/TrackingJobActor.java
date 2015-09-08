/**
 * 
 */
package org.ats.services.executor.event;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.ats.common.ssh.SSHClient;
import org.ats.jenkins.JenkinsMaster;
import org.ats.jenkins.JenkinsMavenJob;
import org.ats.services.event.Event;
import org.ats.services.event.EventFactory;
import org.ats.services.executor.ExecutorService;
import org.ats.services.executor.job.AbstractJob;
import org.ats.services.executor.job.AbstractJob.Status;
import org.ats.services.executor.job.KeywordJob;
import org.ats.services.executor.job.PerformanceJob;
import org.ats.services.generator.GeneratorService;
import org.ats.services.iaas.openstack.OpenStackService;
import org.ats.services.keyword.KeywordProject;
import org.ats.services.keyword.KeywordProjectService;
import org.ats.services.organization.entity.reference.SpaceReference;
import org.ats.services.organization.entity.reference.TenantReference;
import org.ats.services.performance.JMeterScriptReference;
import org.ats.services.performance.PerformanceProject;
import org.ats.services.performance.PerformanceProjectService;
import org.ats.services.vmachine.VMachine;
import org.ats.services.vmachine.VMachineService;

import akka.actor.UntypedActor;

import com.google.inject.Inject;
import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;

/**
 * @author <a href="mailto:haithanh0809@gmail.com">Nguyen Thanh Hai</a>
 *
 * Jul 23, 2015
 */
public class TrackingJobActor extends UntypedActor {

  @Inject ExecutorService executorService;
  
  @Inject GeneratorService generatorService;
  
  @Inject KeywordProjectService keywordService;
  
  @Inject PerformanceProjectService perfService;
  
  @Inject VMachineService vmachineService;
  
  @Inject OpenStackService openstackService;
  
  @Inject EventFactory eventFactory;
  
  @Inject Logger logger;
  
  ConcurrentHashMap<String, JenkinsMavenJob> cache = new ConcurrentHashMap<String, JenkinsMavenJob>();
  
  @Override
  public void onReceive(Object obj) throws Exception {
    if (obj instanceof Event) {
      Event event = (Event) obj;
      if ("keyword-job-tracking".equals(event.getName())) {
        KeywordJob job = (KeywordJob) event.getSource();
        processKeywordJob(job);
      } else if ("performance-job-tracking".equals(event.getName())) {
        PerformanceJob job = (PerformanceJob) event.getSource();
        processPerformanceJob(job);
      }
    }
  }

  private void processPerformanceJob(PerformanceJob job) throws Exception {
    try {
      PerformanceProject project = perfService.get(job.getProjectId());
      switch (job.getStatus()) {
      case Queued:
        doExecutePerformanceJob(job, project);
      case Running:
        doTrackingPerformanceJob(job, project);
        break;
      default:
        break;
      }
    } catch(Exception e) {
      job.setStatus(Status.Completed);
      executorService.update(job);
      
      VMachine vm = vmachineService.get(job.getTestVMachineId());
      if (vm != null) {
        vm.setStatus(VMachine.Status.Started);
        vmachineService.update(vm);
      }
      
      PerformanceProject project = perfService.get(job.getProjectId());
      project.setStatus(PerformanceProject.Status.READY);
      perfService.update(project);
      
      //bubble event
      Event event = eventFactory.create(job, "performance-job-tracking");
      event.broadcast();
      throw e;
    }
  }
  
  private void doTrackingPerformanceJob(PerformanceJob job, PerformanceProject project) throws Exception {
    
    TenantReference tenant = project.getTenant();
    SpaceReference space = project.getSpace();
    
    VMachine systemVM = vmachineService.getSystemVM(tenant, space);
    JenkinsMaster jkMaster = new JenkinsMaster(systemVM.getPublicIp(), "http", "jenkins", 8080);
    
    JenkinsMavenJob jkJob = cache.get(job.getId());
    if (jkJob == null) {
      jkJob = new JenkinsMavenJob(jkMaster, job.getId(), null, null, null);
      cache.put(job.getId(), jkJob);
    }
    
    boolean isBuilding = jkJob.isBuilding(1, System.currentTimeMillis(), 60 * 1000);
    
    if (isBuilding) {
      
      updateLog(job, jkJob);
      
      Thread.sleep(15000L);
      
      Event event  = eventFactory.create(job, "performance-job-tracking");
      event.broadcast();
      
    } else {
      
      updateLog(job, jkJob);
      
      //Download result
      BasicDBList list = new BasicDBList();
      for (JMeterScriptReference ref : job.getScripts()) {
        
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        
        SSHClient.getFile(systemVM.getPublicIp(), 22, "cloudats", "#CloudATS", 
            "/home/cloudats/projects/" + job.getId() + "/target/" + ref.getId() + ".jtl",  bos);
        
        if (bos.size() > 0)
          list.add(new BasicDBObject("_id", ref.getId()).append("content", new String(bos.toByteArray())));
      }
      job.put("report", list);
      //End download result

      job.setStatus(AbstractJob.Status.Completed);
      executorService.update(job);

      //Reset test vm status and release floating ip
      VMachine testVM = vmachineService.get(job.getTestVMachineId());
      testVM.setStatus(VMachine.Status.Started);
      vmachineService.update(testVM);
      
      project.setStatus(PerformanceProject.Status.READY);
      perfService.update(project);
      
      jkJob.delete();
      cache.remove(job.getId());
      
      Event event  = eventFactory.create(job, "performance-job-tracking");
      event.broadcast();
    }
  }
  
  private void doExecutePerformanceJob(PerformanceJob job, PerformanceProject project) throws Exception {
    
    VMachine jenkinsVM = vmachineService.getSystemVM(project.getTenant(), project.getSpace());
    VMachine testVM = vmachineService.getTestVMAvailabel(project.getTenant(), project.getSpace(), false);
    
    if (testVM == null) {
      testVM = openstackService.createTestVM(project.getTenant(), project.getSpace(), false);
      testVM = openstackService.deallocateFloatingIp(testVM);
      //Sleep 15s after creating new vm to make sure system be stable
      Thread.sleep(15 * 1000);
    }

    String path = generatorService.generatePerformance("/tmp", job.getId(), true, job.getScripts());
//    String projectHash = project.getId().substring(0, 8);

    SSHClient.sendFile(jenkinsVM.getPublicIp(), 22, "cloudats", "#CloudATS", "/home/cloudats/projects", job.getId() + ".zip", new File(path));
    
    Thread.sleep(3000);
    
    SSHClient.execCommand(jenkinsVM.getPublicIp(), 22, "cloudats", "#CloudATS", 
        "cd /home/cloudats/projects && unzip " +  job.getId() + ".zip", null, null);

    StringBuilder goalsBuilder = new StringBuilder("clean test ").append(" -Djmeter.hosts=").append(testVM.getPrivateIp());

    JenkinsMaster jenkinsMaster = new JenkinsMaster(jenkinsVM.getPublicIp(), "http", "/jenkins", 8080);
    JenkinsMavenJob jenkinsJob = new JenkinsMavenJob(jenkinsMaster, job.getId(), 
        "master" , "/home/cloudats/projects/" + job.getId() + "/pom.xml", goalsBuilder.toString());

    jenkinsJob.submit();

    testVM.setStatus(VMachine.Status.InProgress);
    vmachineService.update(testVM);
    
    job.setStatus(Status.Running);
    job.setVMachineId(testVM.getId());
    executorService.update(job);
    
    Event event  = eventFactory.create(job, "performance-job-tracking");
    event.broadcast();
  }

  private void processKeywordJob(KeywordJob job) throws Exception {
    try {
      KeywordProject project = keywordService.get(job.getProjectId());
      switch (job.getStatus()) {
      case Queued:
        doExecuteKeywordJob(job, project);
      case Running:
        doTrackingKeywordJob(job, project);
        break;
      default:
        break;
      }
    } catch (Exception e) {
      job.setStatus(Status.Completed);
      executorService.update(job);
      
      VMachine vm = vmachineService.get(job.getTestVMachineId());
      if (vm != null) {
        vm.setStatus(VMachine.Status.Started);
        if (vm.getPublicIp() != null) openstackService.deallocateFloatingIp(vm);
        else vmachineService.update(vm);
      }
      
      KeywordProject project = keywordService.get(job.getProjectId());
      project.setStatus(KeywordProject.Status.READY);
      keywordService.update(project);
      
      //bubble event
      Event event = eventFactory.create(job, "keyword-job-tracking");
      event.broadcast();
      throw e;
    }
  }
  
  private void doTrackingKeywordJob(KeywordJob job, KeywordProject project) throws Exception {
    
    TenantReference tenant = project.getTenant();
    SpaceReference space = project.getSpace();
    
    VMachine systemVM = vmachineService.getSystemVM(tenant, space);
    VMachine testVM = vmachineService.get(job.getTestVMachineId());
    
    JenkinsMaster jkMaster = new JenkinsMaster(systemVM.getPublicIp(), "http", "jenkins", 8080);
    JenkinsMavenJob jkJob = cache.get(job.getId());
    if (jkJob == null) {
      jkJob = new JenkinsMavenJob(jkMaster, job.getId(), null, null, null);
      cache.put(job.getId(), jkJob);
    }
    
    boolean isBuilding = jkJob.isBuilding(1, System.currentTimeMillis(), 60 * 1000);
    if (isBuilding) {
      
      updateLog(job, jkJob);
      
      Thread.sleep(5000L);
      
      Event event  = eventFactory.create(job, "keyword-job-tracking");
      event.broadcast();
      
    } else {
      updateLog(job, jkJob);
      
      //Download result
      ByteArrayOutputStream bos = new ByteArrayOutputStream();
      SSHClient.getFile(testVM.getPublicIp(), 22, "cloudats", "#CloudATS", 
          "/home/cloudats/projects/" + job.getId() + "/target/surefire-reports/testng-results.xml",  bos);
      
      if (bos.size() > 0)
        job.put("report", new String(bos.toByteArray()));
      //End download result

      job.setStatus(AbstractJob.Status.Completed);
      executorService.update(job);
      
      //Reset vm status and release floating ip
      testVM.setStatus(VMachine.Status.Started);
      vmachineService.update(testVM);
      
      //TODO:Need review it
      // testVM = openstackService.deallocateFloatingIp(testVM);
      
      project.setStatus(KeywordProject.Status.READY);
      keywordService.update(project);
      
      jkJob.delete();
      cache.remove(job.getId());
      
      Event event  = eventFactory.create(job, "keyword-job-tracking");
      event.broadcast();
    }
  }
  
  private void doExecuteKeywordJob(KeywordJob job, KeywordProject project) throws Exception {

    VMachine jenkinsVM = vmachineService.getSystemVM(project.getTenant(), project.getSpace());
    VMachine testVM = vmachineService.getTestVMAvailabel(project.getTenant(), project.getSpace(), true);

    if (testVM == null) {
      testVM = openstackService.createTestVM(project.getTenant(), project.getSpace(), true);
      //Sleep 15s after creating new vm to make sure system be stable
      Thread.sleep(15 * 1000);
    }
    
    if (testVM.getPublicIp() == null) {
      testVM = openstackService.allocateFloatingIp(testVM);
      Thread.sleep(15 * 1000);
      SSHClient.checkEstablished(testVM.getPublicIp(), 22, 300);
      logger.log(Level.INFO, "Connection to  " + testVM.getPublicIp() + " is established");
    }

    String path = generatorService.generateKeyword("/tmp", job.getId(), true, job.getSuites());

    SSHClient.sendFile(testVM.getPublicIp(), 22, "cloudats", "#CloudATS", "/home/cloudats/projects", job.getId() + ".zip", new File(path));
    
    Thread.sleep(3000);
    
    SSHClient.execCommand(testVM.getPublicIp(), 22, "cloudats", "#CloudATS", 
        "cd /home/cloudats/projects && unzip " +  job.getId() + ".zip", null, null);

    StringBuilder goalsBuilder = new StringBuilder("clean test");

    JenkinsMaster jenkinsMaster = new JenkinsMaster(jenkinsVM.getPublicIp(), "http", "/jenkins", 8080);
    JenkinsMavenJob jenkinsJob = new JenkinsMavenJob(jenkinsMaster, job.getId(), 
        testVM.getPrivateIp() , "/home/cloudats/projects/" + job.getId() + "/pom.xml", goalsBuilder.toString());

    jenkinsJob.submit();

    testVM.setStatus(VMachine.Status.InProgress);
    vmachineService.update(testVM);

    job.setStatus(Status.Running);
    job.setVMachineId(testVM.getId());
    executorService.update(job);
    
    Event event  = eventFactory.create(job, "keyword-job-tracking");
    event.broadcast();
  }
  
  private void updateLog(AbstractJob<?> job, JenkinsMavenJob jkJob) throws IOException {
    int start = job.getLog() == null ? 0 : job.getLog().length();
    byte[] bytes = jkJob.getConsoleOutput(1, start); 
    byte[] next = new byte[bytes.length - start];
    System.arraycopy(bytes, start, next, 0, next.length);
    if (next.length > 0) {
      job.appendLog(new String(next));
      executorService.update(job);
    }
  }
}
