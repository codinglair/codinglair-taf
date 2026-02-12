package sauce.unit.data;

import com.codinglair.taf.core.AppConfigLoader;
import com.codinglair.taf.core.data.abstraction.TestContext;
import com.codinglair.taf.core.data.factory.TestContextFactory;
import com.codinglair.taf.core.environment.EnvironmentProperties;
import com.codinglair.taf.sauce.data.ProductPojo;
import com.codinglair.taf.sauce.data.WebUser;
import org.junit.*;

import java.util.List;

public class WebUserTest {
    @Test
    public void testGetCreds() {

        TestContext<WebUser, ProductPojo> context =
                TestContextFactory.createContext("data.com.codinglair.taf.sauce.SauceDemoContext",
                        new EnvironmentProperties(
                                AppConfigLoader.loadSubmoduleConfig("config/app.config")
                                        .getProperty("ENV_PROPERTIES_PATH")));

        List<WebUser> users =  context.getTestInputs("TC0001");

        WebUser user = users.getFirst();
        Assert.assertNotNull(user.getUserName());
        System.out.println(user.getUserName());
        Assert.assertNotNull(user.getPwd());
        System.out.println(user.getPwd());
    }
}
