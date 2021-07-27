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
package tool;

import java.io.*;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.List;
import java.io.FileWriter;
import java.io.BufferedWriter;
import java.lang.Integer;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import org.json.XML;
import pique.analysis.ITool;
import pique.analysis.Tool;
import pique.model.Diagnostic;
import pique.model.Finding;
import pique.model.ModelNode;
import pique.model.QualityModel;
import pique.model.QualityModelImport;
import utilities.PiqueProperties;
import utilities.helperFunctions;

public class CPPCheckToolWrapper extends Tool implements ITool  {


    public CPPCheckToolWrapper(Path toolRoot) {
        super("cppcheck", toolRoot);

    }

    /**
     * @param path The path to a binary file for the desired solution of project to
     *             analyze
     * @return The path to the analysis results file
     */
    @Override
    public Path analyze(Path projectLocation) {
        File tempResults = new File(System.getProperty("user.dir") + "/out/cppcheckOutput.xml");
        tempResults.delete(); // clear out the last output. May want to change this to rename rather than delete.
        tempResults.getParentFile().mkdirs();
        Path project = Paths.get("project.root");
        System.out.println(project.toString());

        String cmd = String.format("cmd /c cppcheck.exe --enable=all --xml %s --output-file=%s",
                projectLocation.toAbsolutePath().toString(), tempResults.toPath().toAbsolutePath().toString());
        //TODO: replace all absolute paths with path from properties file
        try {
            System.out.println(helperFunctions.getOutputFromProgram(cmd));

        } catch (IOException  e) {
            e.printStackTrace();
        }

        return tempResults.toPath();
    }



    @Override
    public Map<String, Diagnostic> parseAnalysis(Path toolResults) {
        Map<String, Diagnostic> diagnosticsUniverseForTool = initializeDiagnostics();
        Map<String, Diagnostic> diagnosticsFound = new HashMap<>();

        File input = new File(toolResults.toString()); //in xml file
        File tempFile = new File(System.getProperty("user.dir") + "/out/cppcheckOutput.json");

        String results = "";

        try {
            results = helperFunctions.readFileContent(toolResults);
        } catch (IOException e) {
            System.err.println("No results to read.");
            return diagnosticsUniverseForTool;
        }

        try {
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
            results = helperFunctions.readFileContent(tempPath);
        } catch (IOException e) {
            System.err.println("No results to read.");
            return diagnosticsUniverseForTool;
        }

        ArrayList<String> IDList = new ArrayList<String>();
        ArrayList<Integer> severityList = new ArrayList<Integer>();

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
                finding.setValue(1.0);
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
        Properties prop = PiqueProperties.getProperties();
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
