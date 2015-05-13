import java.io.IOException;
import java.lang.reflect.Method;
import java.util.Map;

import org.ats.services.OrganizationContext;
import org.ats.services.OrganizationServiceModule;
import org.ats.services.data.DatabaseModule;
import org.ats.services.data.MongoDBService;
import org.ats.services.event.EventModule;
import org.ats.services.event.EventService;
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

import play.Application;
import play.GlobalSettings;
import play.Play;
import play.mvc.Action;
import play.mvc.Http.Request;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.TypeLiteral;

/**
 * 
 */

/**
 * @author <a href="mailto:haithanh0809@gmail.com">Nguyen Thanh Hai</a>
 *
 * Mar 18, 2015
 */
public class Global extends GlobalSettings {
  
  /** .*/
  private Injector injector;

  @Override
  public void onStart(Application app) {
    String dbConf = Play.application().configuration().getString(DatabaseModule.DB_CONF);
    String eventConf = Play.application().configuration().getString(EventModule.EVENT_CONF);
    try {
      injector = Guice.createInjector(new DatabaseModule(dbConf), new EventModule(eventConf), new OrganizationServiceModule());

      TenantFactory tenantFactory = injector.getInstance(TenantFactory.class);
      TenantService tenantService = injector.getInstance(TenantService.class);
      SpaceFactory spaceFactory = injector.getInstance(SpaceFactory.class);
      ReferenceFactory<TenantReference> tenantRefFactory = injector.getInstance(Key.get(new TypeLiteral<ReferenceFactory<TenantReference>>(){}));
      SpaceService spaceService = injector.getInstance(SpaceService.class);
      UserFactory userFactory = injector.getInstance(UserFactory.class);
      UserService userService = injector.getInstance(UserService.class);
      ReferenceFactory<SpaceReference> spaceRefFactory = injector.getInstance(Key.get(new TypeLiteral<ReferenceFactory<SpaceReference>>(){}));
      
      if (userService.get("haint@cloud-ats.net") == null) {
        Tenant tenant = tenantFactory.create("Fsoft");
        tenantService.create(tenant);
        
        Space space = spaceFactory.create("FSU1.BU11");
        space.setTenant(tenantRefFactory.create(tenant.getId()));
        spaceService.create(space);
        
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
      }
      
      
      //start event service
      EventService eventService = injector.getInstance(EventService.class);
      eventService.setInjector(injector);
      eventService.start();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    super.onStart(app);
  }
  
  @Override
  public void onStop(Application app) {
    EventService eventService = injector.getInstance(EventService.class);
    MongoDBService mongoService = injector.getInstance(MongoDBService.class);
    
    eventService.stop();
    mongoService.dropDatabase();
    super.onStop(app);
  }
  
  @Override
  public Action<?> onRequest(Request request, Method actionMethod) {
    
    OrganizationContext context = injector.getInstance(OrganizationContext.class);
    String token = request.getHeader(AuthenticationService.AUTH_TOKEN_HEADER);
    if (token == null) {
      context.setUser(null);
      context.setSpace(null);
      context.setTenant(null);
      return super.onRequest(request, actionMethod);
    }
    
    String space = request.getHeader(AuthenticationService.SPACE_HEADER);
    
    AuthenticationService<User> service = injector.getInstance(Key.get(new TypeLiteral<AuthenticationService<User>>(){}));

    User user = service.findByAuthToken(token);
    if (user == null) {
      context.setUser(null);
      context.setSpace(null);
      context.setTenant(null);
      return super.onRequest(request, actionMethod);
    }

    context.setUser(user);
    context.setTenant(user.getTanent().get());

    if (space != null) {
      SpaceService spaceService = injector.getInstance(SpaceService.class);
      ReferenceFactory<SpaceReference> spaceRefFactory = injector.getInstance(Key.get(new TypeLiteral<ReferenceFactory<SpaceReference>>(){}));
      spaceService.goTo(spaceRefFactory.create(space));
    }

    return super.onRequest(request, actionMethod);
  }
  
  @Override
  public <T> T getControllerInstance(Class<T> aClass) throws Exception {
    return injector.getInstance(aClass);
  }
}
