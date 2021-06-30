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
package calibration;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import pique.analysis.ITool;
import pique.calibration.IBenchmarker;
import pique.evaluation.BenchmarkMeasureEvaluator;
import pique.evaluation.Project;
import pique.model.Diagnostic;
import pique.model.Measure;
import pique.model.QualityModel;

public class BinaryBenchmarker implements IBenchmarker {
    /**
     * Derive thesholds for all {@link Measure} nodes using a naive approach:
     * (1) threshold[0] = the lowest value seen for the {@link Measure}
     * (2) threshold[1] = the highest value seen for the {@link Measure}
     *
     * @param benchmarkRepository The root directory containing the items to be used for benchmarking
     * @param qmDescription       The quality model description file
     * @param tools               The collection of static analysis tools needed to audio the benchmark repository
     * @param projectRootFlag     Option flag to target the static analysis tools, not used in binary case
     * @return A dictionary of [ Key: {@link pique.model.ModelNode} name, Value: thresholds ] where
     * thresholds is a size = 2 array of Double[] containing the lowest and highest value
     * seen for the given measure (after normalization).
     */
    @Override
    public Map<String, Double[]> deriveThresholds(Path benchmarkRepository, QualityModel qmDescription, Set<ITool> tools,
                                                  String projectRootFlag) {

        // Collect benchmark binaries
        Set<Path> projectRoots = new HashSet<>();
        File[] binaryFiles = benchmarkRepository.toFile().listFiles();
        for (File file : binaryFiles) {
            if (file.isFile()) {
                projectRoots.add(file.toPath());
            }
        }
    	ArrayList<Project> projects = new ArrayList<>();

        System.out.println("* Beginning repository benchmark analysis");
        System.out.println(projectRoots.size() + " projects to analyze.\n");

        int totalProjects = projectRoots.size();
        int counter = 0;

        for (Path projectPath : projectRoots) {

            counter++;

            // Clone the QM
            // TODO (1.0): Currently need to use .clone() for benchmark repository quality model sharing. This will be
            //  confusing and problematic to people not using the default benchmarker.
            QualityModel clonedQM = qmDescription.clone();

            // Instantiate new project object
            Project project = new Project(projectPath.getFileName().toString(), projectPath, clonedQM);

            // TODO: temp fix
            // Set measures to not use a utility function during their node evaluation
            project.getQualityModel().getMeasures().values().forEach(measure -> {
                measure.setEvaluatorObject(new BenchmarkMeasureEvaluator());
            });

            // Run the static analysis tools process
            Map<String, Diagnostic> allDiagnostics = new HashMap<>();
            tools.forEach(tool -> {
                Path analysisOutput = tool.analyze(projectPath);
                allDiagnostics.putAll(tool.parseAnalysis(analysisOutput));
            });
            //System.out.println(allDiagnostics.keySet());
            /*[loc, CWE-676, CWE-732, CWE-22, CWE-250, CWE-22, CWE-250, CWE-120/CWE-785!, CWE-134, CWE-377, CVE-CWE-Unknown-Other,
            CWE-362/CWE-367!, CWE-676, CWE-120, CWE-20, CWE-190, CWE-807, CWE-829, CWE-362, CWE-20, CWE-829, CWE-20, CWE-125,
            CWE-785, CWE-807, CWE-20, CWE-126, CWE-78, CWE-788, CWE-327, CWE-120, CWE-20, CWE-120, CWE-676, CWE-120,
            CWE-119!/CWE-120]
             */
            //System.out.println(allDiagnostics.values());
            /*[pique.model.Diagnostic@548ad73b, pique.model.Diagnostic@4c762604, pique.model.Diagnostic@2641e737,
            pique.model.Diagnostic@727803de, pique.model.Diagnostic@704921a5, pique.model.Diagnostic@df27fae,
            pique.model.Diagnostic@24a35978, pique.model.Diagnostic@16f7c8c1, pique.model.Diagnostic@2f0a87b3,
            pique.model.Diagnostic@319b92f3, pique.model.Diagnostic@fcd6521, pique.model.Diagnostic@27d415d9,
            pique.model.Diagnostic@5c18298f, pique.model.Diagnostic@31f924f5, pique.model.Diagnostic@5579bb86,
            pique.model.Diagnostic@5204062d, pique.model.Diagnostic@4fcd19b3, pique.model.Diagnostic@376b4233,
            pique.model.Diagnostic@2fd66ad3, pique.model.Diagnostic@5d11346a, pique.model.Diagnostic@7a36aefa,
            pique.model.Diagnostic@17211155, pique.model.Diagnostic@b3d7190, pique.model.Diagnostic@5fdba6f9,
            pique.model.Diagnostic@10d59286, pique.model.Diagnostic@fe18270, pique.model.Diagnostic@6fb0d3ed,
            pique.model.Diagnostic@6dde5c8c]
             */

            // Would normalize here if we do so in the future
            
            // Apply collected diagnostics (containing findings) to the project
            allDiagnostics.forEach((diagnosticName, diagnostic) -> {
                project.addFindings(diagnostic);
            });

            // Evaluate project up to Measure level
            project.evaluateMeasures();

            //System.out.println("Measures despues de evaluar");
            //System.out.println(project.getQualityModel().getMeasures().values());

            // Add new project (with tool findings information included) to the list
             projects.add(project); //Un arrayList de proyectos

            // Print information
            System.out.println("\n\tFinished analyzing project " + project.getName());
            System.out.println("\t" + counter + " of " + totalProjects + " analyzed.\n");
        }

        // Map all values audited for each measure
        Map<String, ArrayList<Double>> measureBenchmarkData = new HashMap<>();
        projects.forEach(p -> {
            p.getQualityModel().getMeasures().values().forEach(m -> {
                        if (!measureBenchmarkData.containsKey(m.getName())) {
                            measureBenchmarkData.put(m.getName(), new ArrayList<Double>() {{
                                add(m.getValue());
                            }});
                        } else {
                            measureBenchmarkData.get(m.getName()).add(m.getValue());
                        }
                System.out.println(m.getChildren());
                    }
            );
        });

        //System.out.println("measureBenchmarkData: ");   //Aqui nos devuelve un arreglo, que tiene 10 arreglos
        //y cada uno solo tiene un elemento, 0.0.
        //System.out.println(measureBenchmarkData.values());

        
        // Identify the 1st and 3rd quartiles of each measure value
        Double[] percentiles = new Double[2];
        percentiles[0]=0.25;
        percentiles[1]=0.75;
        Map<String, Double[]> measureThresholds = new HashMap<>();
        measureBenchmarkData.forEach((measureName, measureValues) -> {
            measureThresholds.putIfAbsent(measureName, new Double[2]);
            
            measureThresholds.get(measureName)[0] = mean(measureValues)-calculateSD(measureValues);
            measureThresholds.get(measureName)[1] = mean(measureValues)+calculateSD(measureValues);
            
            if (measureThresholds.get(measureName)[0] < 0.0) {
            	measureThresholds.get(measureName)[0] = 0.0;
            }

        });
        //System.out.println(measureThresholds.values());

        return measureThresholds;
    }
    
    private static Double mean(ArrayList<Double> measureValues) {
    	Double sum = 0.0;
        for (int i = 0; i < measureValues.size(); i++) {
            sum += measureValues.get(i);
        }
        return sum / measureValues.size();
    }
    
    private static Double[] getPercentiles(ArrayList<Double> values, Double[] percentiles) {
    	Double[] tempVals= new Double[values.size()];
    	tempVals = values.toArray(tempVals);
        Arrays.sort(tempVals, 0, tempVals.length);
        for (int i = 0; i < percentiles.length; i++) {
          int index = (int) (percentiles[i] * tempVals.length);
          percentiles[i] = tempVals[index];
        }
        
        return percentiles;
      }

    private static Double calculateSD(ArrayList<Double> measureValues)
    {
    	Double sum = 0.0, standardDeviation = 0.0;
        int length = measureValues.size();

        for(Double num : measureValues) {
            sum += num;
        }

        double mean = sum/length;

        for(Double num: measureValues) {
            standardDeviation += Math.pow(num - mean, 2);
        }

        return Math.sqrt(standardDeviation/length);
    }
    
    @Override
    public String getName() {
        return this.getClass().getCanonicalName();
    }
}
