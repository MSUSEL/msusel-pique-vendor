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

import java.io.File;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.io.FilenameUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pique.analysis.ITool;
import pique.evaluation.Project;
import pique.model.Diagnostic;
import pique.model.QualityModel;
import pique.model.QualityModelImport;
import pique.runnable.ASingleProjectEvaluator;
import tool.CPPCheckToolWrapper;
import tool.FlawfinderToolWrapper;
import utilities.PiqueProperties;

/**
 * Behavioral class responsible for running TQI evaluation of a single project
 * in a language agnostic way.  It is the responsibility of extending projects
 * (e.g. qatch-csharp) to provide the language specific tools.
 */
// TODO (1.0): turn into static methods (maybe unless logger problems)
public class SingleProjectEvaluator extends ASingleProjectEvaluator {
    private static final Logger LOGGER = LoggerFactory.getLogger(SingleProjectEvaluator.class);

    private Project project;

    //quick fix, FIXME
    public SingleProjectEvaluator(){
        init(null);
    }

    public SingleProjectEvaluator(String propertiesLocation){
        init(propertiesLocation);
    }

    public void init(String propertiesLocation){
        LOGGER.info("Beginning Evaluation");
        Properties prop = propertiesLocation==null ? PiqueProperties.getPropertiesDefault() : PiqueProperties.getProperties(propertiesLocation);

        Path projectRoot = Paths.get(prop.getProperty("project.root"));
        Path resultsDir = Paths.get(prop.getProperty("results.directory"));

        // Initialize objects
        Path toolLocation = Paths.get("tool.cppcheck.filepath");
        Path toolLocation2 = Paths.get("tool.flawfinder.filepath");
        Path qmLocation = Paths.get("out/CVendorQualityModel.json");

        ITool flawfinderToolWrapper = new FlawfinderToolWrapper(toolLocation);
        Set<ITool> tools = Stream.of(flawfinderToolWrapper).collect(Collectors.toSet());
        ITool cppCheckToolWrapper = new CPPCheckToolWrapper(toolLocation2);
        tools.addAll(Stream.of(cppCheckToolWrapper).collect(Collectors.toSet()));

        Set<Path> projectRoots = new HashSet<>();
        File[] filesToAssess = projectRoot.toFile().listFiles();
        for (File file : filesToAssess) {
            if (file.isFile()) {
                projectRoots.add(file.toPath());
            }
        }
        for (Path projectPath : projectRoots) {
            Path outputPath = runEvaluator(projectPath, resultsDir, qmLocation, tools);
            System.out.println("output: " + outputPath.getFileName());
            System.out.println();
        }

    }


    public Project getEvaluatedProject() {
        return project;
    }

    /**
     * Entry point for running single project evaluation. The library assumes the user has extended Qatch
     * by implementing ITool with language-specific functionality.
     *
     * This method then evaluates the measures, properties, characteristics, and TQI according to the provided
     * quality model.
     *
     * @param projectDir
     *      Path to root directory of projects to be analyzed.
     * @param resultsDir
     *      Directory to place the analysis results in. Does not needy to exist initially.
     * @param qmLocation
     *      Path to a completely derived quality model (likely .json format).
     * @return
     *      The path to the produced quality analysis file on the hard disk.
     */
    public Path runEvaluator(Path projectDir, Path resultsDir, Path qmLocation, Set<ITool> tools) {

        // Initialize data structures
        initialize(projectDir, resultsDir, qmLocation);
        QualityModelImport qmImport = new QualityModelImport(qmLocation);
        QualityModel qualityModel = qmImport.importQualityModel();
        project = new Project(FilenameUtils.getBaseName(projectDir.getFileName().toString()), projectDir, qualityModel);

        // Validate State
        // TODO: validate more objects such as if the quality model has thresholds and weights, are there expected diagnostics, etc
        validatePreEvaluationState(project);

        // Run the static analysis tools process
        Map<String, Diagnostic> allDiagnostics = new HashMap<>();
        tools.forEach(tool -> {
            allDiagnostics.putAll(runTool(projectDir, tool));
        });

        allDiagnostics.forEach((diagnosticName, diagnostic) -> {
            project.getQualityModel().getDiagnostic(diagnosticName).setChildren(diagnostic.getChildren());
            project.getQualityModel().getDiagnostic(diagnosticName).setValue(diagnostic.getValue());
        });

        project.getQualityModel().getMeasures().forEach((measureName, measure) -> {
            project.getQualityModel().getMeasure(measureName).setValue(measure.getValue());
        });

        project.getQualityModel().getProductFactors().forEach((pfName, pf) -> {
            project.getQualityModel().getProductFactor(pfName).setValue(pf.getValue());
        });

        project.getQualityModel().getQualityAspects().forEach((qaName, qa) -> {
            project.getQualityModel().getQualityAspect(qaName).setValue(qa.getValue());
        });

        BigDecimal tqiValue = project.evaluateTqi();

        // Create a file of the results and return its path
        return project.exportToJson(resultsDir);
    }


}
