package tool;

import org.junit.Test;

import java.nio.file.Paths;
import java.util.Properties;
import utilities.PiquePropertiesTest;

public class FlawfinderToolWrapperTest {

    private Properties prop;

    public FlawfinderToolWrapperTest(){
        prop = PiquePropertiesTest.getProperties();
    }

    @Test
    public void testToolRun(){
        FlawfinderToolWrapper flawfinderToolWrapper = new FlawfinderToolWrapper(Paths.get(prop.getProperty("tool.flawfinder.filepath")));

        flawfinderToolWrapper.analyze(Paths.get("src/test/resources/benchmark/console.c"));

    }
}
