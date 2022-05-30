/**
 * MIT License
 *
 * Copyright (c) 2021 Montana State University Software Engineering Labs
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package piqueVendor.runnable;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pique.analysis.ITool;
import pique.model.QualityModel;
import pique.model.QualityModelExport;
import pique.model.QualityModelImport;
import pique.runnable.AQualityModelDeriver;
import tool.CPPCheckToolWrapper;
import tool.FlawfinderToolWrapper;
import utilities.PiqueProperties;

/**
 * Utility driver class responsible for running the calibration module's procedure.
 * This uses a benchmark repository, quality model description, directory of comparison matrices,
 * instances of language-specific analysis tools, and the RInvoker as input to perform a 3 step process of
 * (1) Derive thresholds
 * (2) Elicitate weights
 * (3) Apply these results to the quality model to generate a fully derived quality model
 */
public class QualityModelDeriver extends AQualityModelDeriver {

    private static final Logger LOGGER = LoggerFactory.getLogger(QualityModelDeriver.class);


    public QualityModelDeriver(String propertiesPath){
        init(propertiesPath);
    }

    public QualityModelDeriver(){
        init(null);
    }

    private void init(String propertiesLocation){
        LOGGER.info("Beginning deriver");

        Properties prop = propertiesLocation==null ? PiqueProperties.getPropertiesDefault() : PiqueProperties.getProperties(propertiesLocation);

        Path blankqmFilePath = Paths.get(prop.getProperty("blankqm.filepath"));
        Path derivedModelFilePath = Paths.get(prop.getProperty("results.directory"));

        // Initialize objects
        //why do we have projectRootFlag? TODO
        String projectRootFlag = "";
        Path cppCheckLocation = Paths.get(prop.getProperty("tool.cppcheck.filepath"));

        //remove leading slashes from
        Path flawFinderLocation = Paths.get(prop.getProperty("tool.flawfinder.filepath"));
        Path benchmarkRepo = Paths.get(prop.getProperty("benchmark.repo"));
        Path comparisonMatrices = Paths.get(prop.getProperty("comparisons.directory"));

        LOGGER.info("Loaded Properties");

        ITool cppCheckToolWrapper = new CPPCheckToolWrapper(cppCheckLocation);
        LOGGER.info("Initialized CPPCheck Tool Wrapper");

        ITool flawfinderToolWrapper = new FlawfinderToolWrapper(flawFinderLocation);
        LOGGER.info("Initialized Flawfinder Tool Wrapper");

        Set<ITool> tools = Stream.of(cppCheckToolWrapper).collect(Collectors.toSet());
        tools.addAll(Stream.of(flawfinderToolWrapper).collect(Collectors.toSet()));

        QualityModelImport qmImport = new QualityModelImport(blankqmFilePath);
        QualityModel qmDescription = qmImport.importQualityModel();

        QualityModel derivedQualityModel = deriveModel(qmDescription, tools, benchmarkRepo, projectRootFlag);

        Path jsonOutput = new QualityModelExport(derivedQualityModel).exportToJson(derivedQualityModel.getName(), derivedModelFilePath);

        LOGGER.info("Quality Model derivation finished. You can find the file at " + jsonOutput.toAbsolutePath().toString());
    }

}
