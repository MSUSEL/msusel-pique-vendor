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

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.List;
import java.lang.Integer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.fasterxml.jackson.dataformat.csv.CsvSchema;
import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.SerializationFeature;


import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import pique.analysis.ITool;
import pique.analysis.Tool;
import pique.model.Diagnostic;
import pique.model.Finding;
import pique.model.ModelNode;
import pique.model.QualityModel;
import pique.model.QualityModelImport;
import utilities.PiqueProperties;
import utilities.helperFunctions;

public class FlawfinderToolWrapper extends Tool implements ITool  {


	public FlawfinderToolWrapper(Path toolRoot) {
		super("flawfinder", toolRoot);

	}

	// Methods
	/**
	 * @param path The path to a binary file for the desired solution of project to
	 *             analyze
	 * @return The path to the analysis results file
	 */
	@Override
	public Path analyze(Path projectLocation) {
		File tempResults = new File(System.getProperty("user.dir") + "/out/flawfinderOutput.csv");
		tempResults.delete(); // clear out the last output. May want to change this to rename rather than delete.
		tempResults.getParentFile().mkdirs();

		String cmd = String.format("cmd /c flawfinder.exe --csv %s -> %s",
				projectLocation.toAbsolutePath().toString(), tempResults.toPath().toAbsolutePath().toString());

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

		//Adapted from: https://kalliphant.com/jackson-convert-csv-json-example/
		File input = new File(toolResults.toString());
		File output = new File(System.getProperty("user.dir") + "/out/flawfinderOutput.json");
		CsvSchema csvSchema = CsvSchema.builder().setUseHeader(true).build();
		CsvMapper csvMapper = new CsvMapper();
		List <Object> readAll = returnCsvReadList(csvSchema, csvMapper, input);
		ObjectMapper mapper = new ObjectMapper();
		returnJSONFile(mapper, output, readAll);

		String results = "";
		Path pathToResults = Paths.get(System.getProperty("user.dir") + "/out/flawfinderOutput.json");

		try {
			results = helperFunctions.readFileContent(pathToResults);

		} catch (IOException e) {
			System.err.println("No results to read.");
			return diagnosticsUniverseForTool;
		}

		ArrayList<String> ruleIDList = new ArrayList<String>();
		ArrayList<Integer> severityList = new ArrayList<Integer>();

		try {
			JSONArray jsonResults = new JSONArray(results);

			for (int k = 1; k < jsonResults.length(); k += 2) {
				JSONObject jsonFinding = (JSONObject) jsonResults.get(k);
				String findingName = jsonFinding.get("RuleId").toString();
				String findingSeverity = jsonFinding.get("DefaultLevel").toString();
				severityList.add(this.severityToInt(findingSeverity));
				ruleIDList.add(findingName);
			}

			for (int i = 0; i < ruleIDList.size(); i++) {


				Diagnostic diag = diagnosticsUniverseForTool.get((ruleIDList.get(i)));
				if (diag == null) {
					//this means that either it is unknown, mapped to a CWE outside of the expected results, or is not assigned a CWE
					//We may want to treat this in another way.
					diag = diagnosticsUniverseForTool.get("CVE-CWE-Unknown-Other");
				}
				Finding finding = new Finding("",0,0, severityList.get(i));
				finding.setName(ruleIDList.get(i));
				finding.setValue(1.0);
				diag.setChild(finding);
				diag.getValue();
				diagnosticsFound.put(diag.getName(), diag);
			}


		} catch (JSONException e) {
			e.printStackTrace();
		}

		return diagnosticsFound;
	}

	public List<Object> returnCsvReadList(CsvSchema csvSchema, CsvMapper csvMapper, File input) {
		List<Object> readAll = null;
		try {
			readAll = csvMapper.readerFor(Map.class).with(csvSchema).readValues(input).readAll();
			return readAll;
		} catch (IOException e) {
			e.printStackTrace();
		}
		return readAll;
	}

	public void returnJSONFile(ObjectMapper mapper, File output, List<Object> readAll) {
		try {
			mapper.writerWithDefaultPrettyPrinter().writeValue(output, readAll);
		} catch (IOException e) {
			e.printStackTrace();
		}
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
			if (diag.getToolName().equals("flawfinder")) {
				diagnostics.put(diag.getName(),diag);
			}
		}

		return diagnostics;
	}

	private Integer severityToInt(String severity) {
		Integer severityInt = 0;
		return severityInt.parseInt(severity);
	}


}
