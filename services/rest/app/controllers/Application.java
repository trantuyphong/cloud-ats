package controllers;

import org.ats.services.OrganizationContext;
import org.ats.services.data.MongoDBService;
import org.ats.services.organization.SpaceService;
import org.ats.services.organization.TenantService;
import org.ats.services.organization.UserService;
import org.ats.services.organization.base.AuthenticationService;
import org.ats.services.organization.entity.Space;
import org.ats.services.organization.entity.Tenant;
import org.ats.services.organization.entity.User;
import org.ats.services.organization.entity.fatory.ReferenceFactory;
import org.ats.services.organization.entity.fatory.SpaceFactory;
import org.ats.services.organization.entity.fatory.TenantFactory;
import org.ats.services.organization.entity.fatory.UserFactory;
import org.ats.services.organization.entity.reference.SpaceReference;
import org.ats.services.organization.entity.reference.TenantReference;

import play.libs.Json;
import play.mvc.Controller;
import play.mvc.Result;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.inject.Inject;

public class Application extends Controller {
  
  @Inject
  private TenantService tenantService;
  
  @Inject
  private TenantFactory tenantFactory;
  
  @Inject
  private ReferenceFactory<TenantReference> tenantRefFactory;
  
  @Inject
  private SpaceService spaceService;
  
  @Inject
  private SpaceFactory spaceFactory;
  
  @Inject
  private ReferenceFactory<SpaceReference> spaceRefFactory;
  
  @Inject
  private UserService userService;
  
  @Inject
  private UserFactory userFactory;
  
  @Inject
  private OrganizationContext context;
  
  @Inject
  private AuthenticationService<User> auth;
  
  @Inject
  private MongoDBService service;
  
  public Result index() {
    return ok("Hello REST service");
  }

  public Result createUser() {
    
    service.dropDatabase();
    Tenant tenant = tenantFactory.create("Fsoft");
    this.tenantService.create(tenant);
    
    Space space = spaceFactory.create("FSU1.BU11");
    space.setTenant(tenantRefFactory.create(tenant.getId()));
    this.spaceService.create(space);
    
    User user = userFactory.create("haint@cloud-ats.net", "Hai", "Nguyen");
    user.setTenant(tenantRefFactory.create(tenant.getId()));
    user.joinSpace(spaceRefFactory.create(space.getId()));
    user.setPassword("12345");
    userService.create(user);
    
    user = userFactory.create("tuanhq@cloud-ats.net", "Tuan", "Hoang");
    user.setTenant(tenantRefFactory.create(tenant.getId()));
    user.joinSpace(spaceRefFactory.create(space.getId()));
    user.setPassword("12345");
    userService.create(user);
    
    user = userFactory.create("trinhtv@cloud-ats.net", "Trinh", "Tran");
    user.setTenant(tenantRefFactory.create(tenant.getId()));
    user.joinSpace(spaceRefFactory.create(space.getId()));
    user.setPassword("12345");
    userService.create(user);
    
    user = userFactory.create("nambv@cloud-ats.net", "Nam", "Bui");
    user.setTenant(tenantRefFactory.create(tenant.getId()));
    user.joinSpace(spaceRefFactory.create(space.getId()));
    user.setPassword("12345");
    userService.create(user);
    
    return status(200);
  }
  
  public Result currentWithNotUser() {
    StringBuilder sb = new StringBuilder();
    sb.append(context).append("\n");
    sb.append(context.getUser()).append("\n");
    sb.append(context.getTenant()).append("\n");
    sb.append(context.getSpace()).append("\n");
    return ok(sb.toString());
  }
  
  public Result current() {
    
    service.dropDatabase();
    Tenant tenant = tenantFactory.create("Fsoft");
    this.tenantService.create(tenant);
    
    Space space = spaceFactory.create("FSU1.BU11");
    space.setTenant(tenantRefFactory.create(tenant.getId()));
    this.spaceService.create(space);
    
    User user = userFactory.create("haint@cloud-ats.net", "Hai", "Nguyen");
    user.setTenant(tenantRefFactory.create(tenant.getId()));
    user.joinSpace(spaceRefFactory.create(space.getId()));
    user.setPassword("12345");
    userService.create(user);
    
    user = userFactory.create("tuanhq@cloud-ats.net", "Tuan", "Hoang");
    user.setTenant(tenantRefFactory.create(tenant.getId()));
    user.joinSpace(spaceRefFactory.create(space.getId()));
    user.setPassword("12345");
    userService.create(user);
    
    user = userFactory.create("trinhtv@cloud-ats.net", "Trinh", "Tran");
    user.setTenant(tenantRefFactory.create(tenant.getId()));
    user.joinSpace(spaceRefFactory.create(space.getId()));
    user.setPassword("12345");
    userService.create(user);
    
    user = userFactory.create("nambv@cloud-ats.net", "Nam", "Bui");
    user.setTenant(tenantRefFactory.create(tenant.getId()));
    user.joinSpace(spaceRefFactory.create(space.getId()));
    user.setPassword("12345");
    userService.create(user);
    
    auth.logIn("trinhtv@cloud-ats.net", "12345");
    
    StringBuilder sb = new StringBuilder();
    sb.append(context).append("\n");
    sb.append(context.getUser()).append("\n");
    sb.append(context.getTenant()).append("\n");
    sb.append(context.getSpace()).append("\n");
    return ok(sb.toString());
  }
  
  public Result login() {
    
    service.dropDatabase();
    Tenant tenant = tenantFactory.create("Fsoft");
    this.tenantService.create(tenant);
    
    Space space = spaceFactory.create("FSU1.BU11");
    space.setTenant(tenantRefFactory.create(tenant.getId()));
    this.spaceService.create(space);
    
    User user = userFactory.create("trinhtv3", "trinh", "tran");
    user.setTenant(tenantRefFactory.create(tenant.getId()));
    user.joinSpace(spaceRefFactory.create(space.getId()));
    user.setPassword("12345");
    
    userService.create(user);
    
    String email = "trinhtv3";
    String password = "12345";
    String token = auth.logIn(email, password);
    if (token == null) return unauthorized();
    
    ObjectNode authTokenJson = Json.newObject();
    authTokenJson.put(AuthenticationService.AUTH_TOKEN, token);
    response().setCookie(AuthenticationService.AUTH_TOKEN, token);
    
    return ok(authTokenJson);
  }
  
  public Result logout() {
    service.dropDatabase();
    Tenant tenant = tenantFactory.create("Fsoft");
    this.tenantService.create(tenant);
    
    Space space = spaceFactory.create("FSU1.BU11");
    space.setTenant(tenantRefFactory.create(tenant.getId()));
    this.spaceService.create(space);
    
    User user = userFactory.create("trinhtv3", "trinh", "tran");
    user.setTenant(tenantRefFactory.create(tenant.getId()));
    user.joinSpace(spaceRefFactory.create(space.getId()));
    user.setPassword("12345");
    
    userService.create(user);
    
    String email = "trinhtv3";
    String password = "12345";
    String token = auth.logIn(email, password);
    if (token == null) return unauthorized();
    
    auth.logOut();
    return ok();
  }
  
}
