/**
 * MIT License
 * Copyright (c) 2019 Montana State University Software Engineering Labs
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
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.io.FilenameUtils;

import pique.analysis.ITool;
import pique.evaluation.Project;
import pique.model.Diagnostic;
import pique.model.QualityModel;
import pique.model.QualityModelImport;
import tool.CPPCheckToolWrapper;
import tool.FlawfinderToolWrapper;
import utilities.PiqueProperties;

/**
 * Behavioral class responsible for running TQI evaluation of a single project
 * in a language agnostic way.  It is the responsibility of extending projects
 * (e.g. qatch-csharp) to provide the language specific tools.
 */
// TODO (1.0): turn into static methods (maybe unless logger problems)
public class ProjectsEvaluator {

    public static void main(String[] args){
        new ProjectsEvaluator();
    }

    private Project project;


    public ProjectsEvaluator(){
        Properties prop = PiqueProperties.getProperties();

        Path projectRoot = Paths.get(prop.getProperty("project.root"));
        Path resultsDir = Paths.get(prop.getProperty("results.directory"));

        // Initialize objects
        String projectRootFlag = prop.getProperty("tool.filepath");
        String projectRootFlag2 = prop.getProperty("tool2.filepath");
        Path toolLocation = Paths.get(projectRootFlag);
        Path toolLocation2 = Paths.get(projectRootFlag2);

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

        double tqiValue = project.evaluateTqi();

        // Create a file of the results and return its path
        return project.exportToJson(resultsDir);
    }


    /**
     * Assert input parameters are valid and create the output folder
     *
     * @param projectDir
     *      Path to directory holding the project to be evaluated. Must exist.
     * @param resultsDir
     *      Directory to place the analysis results in. Does not need to exist initially.
     * @param qmLocation
     *      Path to the quality model file. Must exist.
     */
    private void initialize(Path projectDir, Path resultsDir, Path qmLocation) {
        if (!projectDir.toFile().exists()) {
            throw new IllegalArgumentException("Invalid projectDir path given.");
        }
        if (!qmLocation.toFile().exists() || !qmLocation.toFile().isFile()) {
            throw new IllegalArgumentException("Invalid qmLocation path given.");
        }

        resultsDir.toFile().mkdirs();
    }


    /**
     * Run static analysis tool evaluation process:
     *   (1) run static analysis tool
     *   (2) parse: get object representation of the diagnostics described by the QM
     *   (3) make collection of diagnostic objects
     *
     * @param projectDir
     *      Path to root directory of project to be analyzed.
     * @param tool
     *      Analyzer provided by language-specific instance necessary to find findings of the project.
     * @return
     *      A mapping of (Key: property name, Value: measure object) where the measure objects contain the
     *      static analysis findings for that measure.
     */
    private Map<String, Diagnostic> runTool(Path projectDir, ITool tool) {

        // (1) run static analysis tool
        // TODO: turn this into a temp file that always deletes on/before program exit
        Path analysisOutput = tool.analyze(projectDir);

        // (2) prase output: make collection of {Key: diagnostic name, Value: diagnostic objects}
        return tool.parseAnalysis(analysisOutput);
    }


    /**
     * Sequence of state checks of the project's quality model before running evaluation.
     * Throws runtime error if any expected state is not achieved.
     *
     * @param project
     *      The project under evaluation. This project should have a contained qualityModel with
     *      weight and threshold instances.
     */
    // TODO (1.0) Update once basic tests passing
    private void validatePreEvaluationState(Project project) {
        QualityModel projectQM = project.getQualityModel();

        if (projectQM.getTqi().getWeights() == null) {
            throw new RuntimeException("The project's quality model does not have any weights instantiated to its TQI node");
        }

        projectQM.getQualityAspects().values().forEach(characteristic -> {

            if (characteristic.getWeights() == null) {
                throw new RuntimeException("The project's quality model does not have any weights instantiated to its characteristic node");
            }
        });
    }
}
