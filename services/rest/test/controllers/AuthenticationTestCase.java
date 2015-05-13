package controllers;
import static org.fest.assertions.Assertions.assertThat;
import static play.test.Helpers.POST;
import static play.test.Helpers.GET;
import static play.test.Helpers.callAction;
import static play.test.Helpers.contentAsString;
import static play.test.Helpers.contentType;
import static play.test.Helpers.fakeApplication;
import static play.test.Helpers.fakeRequest;
import static play.test.Helpers.routeAndCall;
import static play.test.Helpers.running;
import static play.test.Helpers.status;
import static play.test.Helpers.cookies;
import static play.test.Helpers.header;

import org.ats.services.organization.base.AuthenticationService;
import org.junit.Test;

import play.libs.Json;
import play.mvc.Http.Request;
import play.mvc.Http.Response;
import play.mvc.Result;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class AuthenticationTestCase {

  @Test
  public void callIndex() {
    running(fakeApplication(), new Runnable() {
      
      public void run() {
        Result result = callAction(controllers.routes.ref.Application.index());
        
        assertThat(status(result)).isEqualTo(200);
        assertThat(contentAsString(result)).contains("Hello REST service");
      }
    });
  }
  
  @Test
  public void testLogin() {

    running(fakeApplication(), new Runnable() {
      
      @Override
      public void run() {
        ObjectNode object = Json.newObject();
        object.put("email", "trinhtv@cloud-ats.net");
        object.put("password", "12345");
        
        Result result = routeAndCall(fakeRequest(POST, "/api/v1/user/login").withJsonBody(object));
        
        JsonNode json = Json.parse(contentAsString(result));
        
        assertThat(json.get("authToken")).isNotNull();
        assertThat(contentType(result)).isEqualTo("application/json");
        assertThat(contentAsString(result)).contains("authToken");
        assertThat(json.get("authToken").asText()).isEqualTo(cookies(result).get("authToken").value());
      }
    });
  }
  
  @Test
  public void testLogout() {
    
    running(fakeApplication(), new Runnable() {
      
      @Override
      public void run() {

        Result result = routeAndCall(fakeRequest(GET, "/api/v1/user/logout"));
        
        assertThat(status(result)).isEqualTo(200);
      }
    });
  }
  
  @Test
  public void testCurrent() {
    
    running(fakeApplication(), new Runnable() {
      
      @Override
      public void run() {

        Result result = routeAndCall(fakeRequest(GET, "/api/v1/context"));
        assertThat(contentType(result)).isEqualTo("application/json");
        assertThat(contentAsString(result)).isEqualTo("{}");
        
        ObjectNode object = Json.newObject();
        object.put("email", "trinhtv@cloud-ats.net");
        object.put("password", "12345");

        Result login = routeAndCall(fakeRequest(POST, "/api/v1/user/login").withJsonBody(object));
        String cookie = cookies(login).get("authToken").value().toString();
        result = routeAndCall(fakeRequest(GET, "/api/v1/context").withHeader("X-AUTH-TOKEN", cookie));
        
        assertThat(contentAsString(result)).isNotEqualTo("{}");
        assertThat(contentType(result)).isEqualTo("application/json");
        
        JsonNode json = Json.parse(contentAsString(result));
        assertThat(json.get("user").get("_id")).as("trinhtv@cloud-ats.net");
        assertThat(json.get("tenant").get("_id")).as("Fsoft");
        assertThat(json.get("space")).isNull();
        
      }
    });
  }
}
  
  
  
  
  
  
  
  
  
  
  
  
  
  
  
  
  
  
  
  
  
  
  
  
  
  
  
  
  
