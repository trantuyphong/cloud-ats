/**
 * 
 */
package org.ats.services.keyword.action;

import java.io.IOException;

import org.ats.services.keyword.locator.AbstractLocator;
import org.rythmengine.Rythm;

/**
 * @author <a href="mailto:haithanh0809@gmail.com">Nguyen Thanh Hai</a>
 *
 * Apr 10, 2015
 */
@SuppressWarnings("serial")
public class ClickAndHoldElement extends AbstractAction {
  
  private AbstractLocator locator;
  
  public ClickAndHoldElement(AbstractLocator locator) {
    this.locator = locator;
  }

  public String transform() throws IOException {
    String template = "new Actions(wd).clickAndHold(wd.findElement(@locator)).build().perform();\n";
    return Rythm.render(template, locator.transform());
  }

  public String getAction() {
    return "clickAndHoldElement";
  }

}
