package utilities;

import java.io.File;
import java.io.FileReader;
import java.util.Properties;

public class PiquePropertiesTest {

    public static Properties getProperties(){

        Properties prop = new Properties();
        try {
            File propertiesFile = new File("src/test/resources/piqueVendorTest.properties");
            prop.load(new FileReader(propertiesFile));

        }catch(Exception e){
            e.printStackTrace();
        }
        return prop;
    }

    public static boolean saveBenchmarkResults(){
        switch (PiqueProperties.getProperties().getProperty("save.benchmark.results").toLowerCase()){
            case "true":
            case "yes":
                return true;
            default:
                return false;
        }
    }
}
