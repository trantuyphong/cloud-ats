/**
 * 
 */
package controllers;

import org.ats.services.OrganizationContext;
import org.ats.services.organization.base.AuthenticationService;
import org.ats.services.organization.entity.User;

import play.data.DynamicForm;
import play.data.Form;
import play.libs.Json;
import play.mvc.Controller;
import play.mvc.Result;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.inject.Inject;

/**
 * @author <a href="mailto:haithanh0809@gmail.com">Nguyen Thanh Hai</a>
 *
 * Mar 20, 2015
 */
public class AuthenticationController extends Controller {
  
  @Inject
  private AuthenticationService<User> authenService;
  
  @Inject
  private OrganizationContext context;
  
  public Result register() {
    return ok();
  }

  public Result login() {
    
    DynamicForm form = Form.form().bindFromRequest();
    String email = form.get("email");
    String password = form.get("password");
    String token = authenService.logIn(email, password);
    if (token == null) return unauthorized();
    
    ObjectNode authTokenJson = Json.newObject();
    authTokenJson.put(AuthenticationService.AUTH_TOKEN, token);
    response().setCookie(AuthenticationService.AUTH_TOKEN, token);
    return ok(authTokenJson);
  }
  
  public Result logout() {
    authenService.logOut();
    return ok();
  }
  
  public Result current() {
    ObjectNode json = Json.newObject();
    
    if (context.getUser() != null)
      json.put("user", Json.parse(context.getUser().toString()));
    
    if (context.getTenant() != null) 
      json.put("tenant", Json.parse(context.getTenant().toString()));
    
    if (context.getSpace() != null)
      json.put("space", Json.parse(context.getSpace().toString()));
    return ok(json);
  }
}
