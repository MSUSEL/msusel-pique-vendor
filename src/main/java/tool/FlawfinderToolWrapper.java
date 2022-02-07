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
import java.lang.Integer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.fasterxml.jackson.dataformat.csv.CsvSchema;


import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

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


public class FlawfinderToolWrapper extends Tool implements ITool  {
	private static final Logger LOGGER = LoggerFactory.getLogger(FlawfinderToolWrapper.class);

	public FlawfinderToolWrapper(Path toolRoot) {
		super("flawfinder", toolRoot);
	}

	// Methods
	/**
	 * @param projectLocation The path to a binary file for the desired solution of project to
	 *             analyze
	 * @return The path to the analysis results file
	 */
	@Override
	public Path analyze(Path projectLocation) {
		String fileLocation = PiqueProperties.getProperties().getProperty("results.directory");

		if (PiqueProperties.saveBenchmarkResults()){
			LOGGER.info("logging flawfinder results to benchmark directory nested under the results directory");
			fileLocation += "benchmark/";
		}
		File toolResults = new File(fileLocation + FilenameUtils.removeExtension(projectLocation.getFileName().toString())+ "--flawfinderOutput.csv");
		toolResults.delete();
		toolResults.getParentFile().mkdirs();

		String[] cmd = {"python",
			PiqueProperties.getProperties().getProperty("tool.flawfinder.filepath"),
			"--csv",
			projectLocation.toString()};

		LOGGER.info("Built flawfinder command: " + Arrays.toString(cmd)
			.replace(",", "")  //remove the commas
			.replace("[", "")  //remove the right bracket
			.replace("]", "")  //remove the left bracket
			.trim());           //remove trailing spaces from partially initialized arrays);

		String out = "";
		try (BufferedWriter writer = Files.newBufferedWriter(toolResults.toPath())) {
			/*
			flawfinder is weird... When you specify flawfinder an output format it spits that output to STDOUT, no to a file.
			It is intended to be used with redirect operators (">"), but those don't work in java, so I am relying on calling
			the redirectoutput method of Process.
			So, instead of catching errors in a separate output stream, all output is sent to one file. That is why there is only one output file.
			 */
			out = getOutputFromProgram(cmd, LOGGER);
			writer.write(out);
		} catch (IOException e) {
			e.printStackTrace();
		}
		LOGGER.info("Finished analyzing: " + projectLocation);
		return toolResults.toPath();
	}

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

		//need to clean csv output (remove extra lines)
		File input = cleanCSV(toolResults.toFile());

		//Adapted from: https://kalliphant.com/jackson-convert-csv-json-example/
		File output = new File(System.getProperty("user.dir") + "/out/flawfinderOutput.json");
		CsvSchema csvSchema = CsvSchema.builder().setUseHeader(true).build();
		CsvMapper csvMapper = new CsvMapper();
		List <Object> readAll = returnCsvReadList(csvSchema, csvMapper, input);
		ObjectMapper mapper = new ObjectMapper();
		returnJSONFile(mapper, output, readAll);

		String results = "";
		Path pathToResults = Paths.get(System.getProperty("user.dir") + "/out/flawfinderOutput.json");

		try {
			results = HelperFunctions.readFileContent(pathToResults);

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
				finding.setValue(new BigDecimalWithContext(1.0));
				diag.setChild(finding);
				diag.getValue();
				diagnosticsFound.put(diag.getName(), diag);
			}


		} catch (JSONException e) {
			e.printStackTrace();
		}

		return diagnosticsFound;
	}

	private File cleanCSV(File toolResults){
		//https://stackoverflow.com/questions/34999999/java-how-to-remove-blank-lines-from-a-text-file
		try {
			List<String> lines = FileUtils.readLines(toolResults);
			Iterator<String> i = lines.iterator();
			while (i.hasNext()) {
				String line = i.next();
				if (line.isEmpty()) {
					i.remove();
				}
			}
			FileUtils.writeLines(toolResults, lines);
		}
		catch (IOException e) {
			e.printStackTrace();
		}
		return toolResults;
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
