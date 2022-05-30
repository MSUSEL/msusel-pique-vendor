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
package tool;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.io.FileWriter;
import java.io.BufferedWriter;
import java.lang.Integer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.io.FilenameUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import org.json.XML;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pique.analysis.ITool;
import pique.analysis.Tool;
import pique.model.Diagnostic;
import pique.model.Finding;
import pique.model.ModelNode;
import pique.model.QualityModel;
import pique.model.QualityModelImport;
import pique.utility.BigDecimalWithContext;
import utilities.PiqueProperties;
import utilities.HelperFunctions;

public class CPPCheckToolWrapper extends Tool implements ITool  {
    private static final Logger LOGGER = LoggerFactory.getLogger(CPPCheckToolWrapper.class);

    public CPPCheckToolWrapper(Path toolRoot) {
        super("cppcheck", toolRoot);

    }

    /**
     * @param  projectLocation path to a binary file for the desired solution of project to
     *             analyze
     * @return The path to the analysis results file
     */
    @Override
    public Path analyze(Path projectLocation) {
        String fileLocation = PiqueProperties.getPropertiesDefault().getProperty("results.directory");
        if (PiqueProperties.saveBenchmarkResults()){
            LOGGER.info("logging CPPCheck results to benchmark directory nested under the results directory");
            fileLocation += "benchmark/";
        }
        File toolResults = new File(fileLocation + FilenameUtils.removeExtension(projectLocation.getFileName().toString())+ "--cppcheckOutput.xml");
        toolResults.delete();
        toolResults.getParentFile().mkdirs();

        File toolSTDOUT = new File(fileLocation + FilenameUtils.removeExtension(projectLocation.getFileName().toString())+ "--cppcheckSTDOUT.out");
        toolSTDOUT.delete(); // clear out the last output. May want to change this to rename rather than delete.
        toolSTDOUT.getParentFile().mkdirs();

        String[] cmd = {"./"+PiqueProperties.getPropertiesDefault().getProperty("tool.cppcheck.filepath"),
            projectLocation.toString(),
            "--enable=all",
            "--xml",
            "--output-file="+toolResults.toString()};

        LOGGER.info("Built CPPCheck command: " + Arrays.toString(cmd)
            .replace(",", "")  //remove the commas
            .replace("[", "")  //remove the right bracket
            .replace("]", "")  //remove the left bracket
            .trim());           //remove trailing spaces from partially initialized arrays);

        String out = "";
        try (BufferedWriter writer = Files.newBufferedWriter(toolSTDOUT.toPath())) {
            out = getOutputFromProgram(cmd,LOGGER);
            writer.write(out);
        } catch (IOException e) {
            e.printStackTrace();
        }
        LOGGER.info("Finished analyzing: " + projectLocation);
        return toolResults.toPath();
    }

     /**
     * Taken directly from https://stackoverflow.com/questions/13008526/runtime-getruntime-execcmd-hanging
     *
     * @param program - A string as would be passed to Runtime.getRuntime().exec(program)
     * @return the text output of the command. Includes input and error.
     * @throws IOException
     */
    public String getOutputFromProgram(String[] program, Logger logger) throws IOException {
        if(logger!=null) logger.info("Executing: " + String.join(" ", program));
        Process proc = Runtime.getRuntime().exec(program);
        return Stream.of(proc.getErrorStream(), proc.getInputStream()).parallel().map((InputStream isForOutput) -> {
            StringBuilder output = new StringBuilder();
            try (BufferedReader br = new BufferedReader(new InputStreamReader(isForOutput))) {
                String line;
                while ((line = br.readLine()) != null) {
                    if(logger!=null) {
                        logger.debug(line);
                        System.out.println(line);
                    }
                    output.append(line);
                    output.append("\n");
                }
            } catch (IOException e) {
                logger.error("Failed to get output of execution.");
                throw new RuntimeException(e);
            } catch (SecurityException e2){
                logger.error("Unable to create subprocess from CPPCheck");
                e2.printStackTrace();
            }
            return output;
        }).collect(Collectors.joining());
    }



    @Override
    public Map<String, Diagnostic> parseAnalysis(Path toolResults) {
        Map<String, Diagnostic> diagnosticsUniverseForTool = initializeDiagnostics();
        Map<String, Diagnostic> diagnosticsFound = new HashMap<>();

        File input = new File(toolResults.toString()); //in xml file
        File tempFile = new File("out/cppcheckOutput.json");

        String results = "";

        try {
            results = HelperFunctions.readFileContent(toolResults);
        } catch (IOException e) {
            System.err.println("No results to read.");
            return diagnosticsUniverseForTool;
        }

        try {
            //rely on JSON object manipulation instead of xml. So this step is very redundant but it is easier to deal with json than xml.
            JSONObject jsonResultsObject = XML.toJSONObject(results);
            try {
                BufferedWriter writer = new BufferedWriter(new FileWriter(tempFile));
                String jsonResultsString = jsonResultsObject.toString();
                writer.write(jsonResultsString.substring(jsonResultsString.indexOf('['), jsonResultsString.indexOf("}}}")));
                writer.close();
            } catch (IOException e) {
                System.out.println("Can't make new file");
                return diagnosticsUniverseForTool;
            }
        } catch (JSONException e) {
            return diagnosticsUniverseForTool;
        }

        try {
            String tempPathString = tempFile.getAbsolutePath();     //TODO: make from properties file
            Path tempPath = Paths.get(tempPathString);
            results = HelperFunctions.readFileContent(tempPath);
        } catch (IOException e) {
            System.err.println("No results to read.");
            return diagnosticsUniverseForTool;
        }

        ArrayList<String> IDList = new ArrayList<>();
        ArrayList<Integer> severityList = new ArrayList<>();

        try {

            JSONArray jsonResults = new JSONArray(results);

            for (int k = 0; k < jsonResults.length(); k++) {
                JSONObject jsonFinding = (JSONObject) jsonResults.get(k);

                String findingName;
                String findingSeverity;
                findingName = jsonFinding.get("id").toString();
                findingSeverity = jsonFinding.get("severity").toString();
                severityList.add(severityToInt(findingSeverity));
                IDList.add(findingName);
            }

            for (int i = 0; i < IDList.size(); i++) {

                Diagnostic diag = diagnosticsUniverseForTool.get((IDList.get(i)));
                if (diag == null) {
                    //this means that either it is unknown, mapped to a CWE outside of the expected results, or is not assigned a CWE
                    //We may want to treat this in another way.
                    diag = diagnosticsUniverseForTool.get("unknown");
                    IDList.set(i, "unknown");
                    severityList.set(i, 0);
                }
                Finding finding = new Finding("",0,0,severityList.get(i));
                finding.setName(IDList.get(i));
                finding.setValue(new BigDecimalWithContext(1.0));
                diag.setChild(finding); //Null pointer
                diag.getValue();
                diagnosticsFound.put(diag.getName(), diag);
            }


        } catch (JSONException e) {
            e.printStackTrace();
        }

        return diagnosticsFound;
    }


    @Override
    public Path initialize(Path toolRoot) {
        return toolRoot;
    }

    // Creates and returns a set of CWE diagnostics without findings
    private Map<String, Diagnostic> initializeDiagnostics() {
        // load the qm structure
        Properties prop = PiqueProperties.getPropertiesDefault();
        Path blankqmFilePath = Paths.get(prop.getProperty("blankqm.filepath"));
        QualityModelImport qmImport = new QualityModelImport(blankqmFilePath);
        QualityModel qmDescription = qmImport.importQualityModel();

        Map<String, Diagnostic> diagnostics = new HashMap<>();

        // for each diagnostic in the model, if it is associated with this tool,
        // add it to the list of diagnostics
        for (ModelNode x : qmDescription.getDiagnostics().values()) {
            Diagnostic diag = (Diagnostic) x;
            if (diag.getToolName().equals("cppcheck") || diag.getToolName().equals("flawfinder")) {
                diagnostics.put(diag.getName(),diag);
            }
        }

        return diagnostics;
    }

    private Integer severityToInt(String severity) {
        int severityInt = 0;

        switch (severity) {
            case "error": severityInt = 4;
                break;
            case "debug": severityInt = 3;
                break;
            case "warning": severityInt = 2;
                break;
            case "performance": severityInt = 2;
                break;
            case "portability": severityInt = 2;
                break;
            case "information": severityInt = 2;
                break;
            case "style": severityInt = 1;
                break;
            default: severityInt= 0;
        }

        return severityInt;
    }


}
