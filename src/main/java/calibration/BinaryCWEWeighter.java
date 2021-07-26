package calibration;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import org.apache.commons.lang3.ArrayUtils;

import pique.calibration.IWeighter;
import pique.calibration.WeightResult;
import pique.model.ModelNode;
import pique.model.QualityModel;

/**
 * @author Andrew Johnson. Modified by Ernesto Ortiz.
 *
 * This class should weight based off of pairwise comparisons for the quality aspect level but utilize manual weights for
 * product factors to the quality aspect level. This allows stakeholder
 * interest to be represented but reduces the extreme amount of comparisons.
 */
public class BinaryCWEWeighter implements IWeighter{
	private String[] childrenNames;
	private double[][] comparisonMat;
	private int numChildren;


	@Override
	public Set<WeightResult> elicitateWeights(QualityModel qualityModel, Path... externalInput) { //Java varargs
		Set<Path> comparisonMatrices = new HashSet<>();
		File[] csvFiles = externalInput[0].toFile().listFiles();
		for (File file : csvFiles) {
			if (file.isFile()) {
				comparisonMatrices.add(file.toPath());
			}
		}

		Set<WeightResult> weights = new HashSet<>();

		//set the weights as a simple 1/number of children for each edge
		averageWeights(qualityModel.getMeasures().values(),weights);
		averageWeights(qualityModel.getDiagnostics().values(),weights);
		averageWeights(qualityModel.getProductFactors().values(),weights);


		BufferedReader csvReader;
		int lineCount = 0;
		for (Path matrix : comparisonMatrices) {
			String pathToCsv = matrix.toString();
			try {
				csvReader = new BufferedReader(new FileReader(pathToCsv));

				String row;
				while ((row = csvReader.readLine()) != null) {
					String[] data = row.split(",");
					if (lineCount == 0) {
						numChildren = data.length - 1;
						comparisonMat = new double[numChildren][numChildren];
						childrenNames = new String[data.length];
						char [] temp = new char[data[0].length()];
						for (int k = 1; k < data[0].length(); k++){
							temp[k - 1] = data[0].charAt(k);
						}
						StringBuilder sb = new StringBuilder();
						for (int i = 0; i < temp.length - 1; i++) {
							sb.append(temp[i]);
						}
						String tempString = sb.toString();
						childrenNames[0] = tempString;
						for (int i = 1; i < data.length; i++) {
							childrenNames[i] = data[i];
						}
					} else if (lineCount < numChildren + 1) {
						for (int i = 1; i < data.length; i++) {
							comparisonMat[lineCount - 1][i - 1] = Double.parseDouble(data[i].trim());
						}
					}
					lineCount++;
				}
				csvReader.close();
				lineCount = 0;

				if (qualityModel.getTqi().getName() == childrenNames[0]) {
					ahpWeights(qualityModel.getTqi(), weights);
				} else {
					ahpWeights(qualityModel.getAllQualityModelNodes().get(childrenNames[0]), weights);
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

		return weights;
	}


	@Override
	public String getName() {
		return this.getClass().getCanonicalName();
	}

	private void averageWeights(Collection<ModelNode> values, Set<WeightResult> weights) {
		values.forEach(node -> {
			WeightResult weightResult = new WeightResult(node.getName());
			node.getChildren().values().forEach(child -> weightResult.setWeight(child.getName(), averageWeight(node)));
			weights.add(weightResult);
		});
	}

	private double averageWeight(ModelNode currentNode) {
		return 1.0 / (double)currentNode.getChildren().size();
	}

	//weight based on AHP
	private void ahpWeights(ModelNode node, Set<WeightResult> weights) {
		double[] ahpVec = new double[numChildren];
		//normalize by column sums
		double[][] norm = normalizeByColSum(comparisonMat);
		//get the row means
		for (int i = 0; i < numChildren; i++) {
			ahpVec[i] = rowMean(norm,i);
		}
		WeightResult weightResult = new WeightResult(node.getName());
		node.getChildren().values().forEach(child ->
				weightResult.setWeight(child.getName(), ahpVec[(ArrayUtils.indexOf(childrenNames, child.getName())) - 1]));
		weights.add(weightResult);
	}

	private double[][] normalizeByColSum(double[][] mat) {
		double[][] norm = new double[mat.length][mat[0].length];
		for (int i= 0; i < mat.length; i++) {
			for (int j= 0; j < mat[0].length; j++) {
				norm[i][j] = (mat[i][j]/colSum(mat,j));
			}
		}
		return norm;
	}

	private double[][] normalizeByRowSum(double[][] mat) {
		double[][] norm = new double[mat.length][mat[0].length];
		for (int i= 0; i < mat.length; i++) {
			for (int j= 0; j < mat[0].length; j++) {
				norm[i][j] = (mat[i][j]/rowSum(mat,i));
			}
		}
		return norm;
	}

	private double rowMean(double[][] mat, int row) {
		int cols = mat[0].length;
		return (rowSum(mat,row))/((double)cols);
	}

	private double rowSum(double[][] mat, int row) {
		int cols = mat[0].length;
		double sumRow = 0;
		for(int j = 0; j < cols; j++){
			sumRow = sumRow + mat[row][j];
		}
		return sumRow;
	}

	private double colSum(double[][] mat, int col) {
		int rows = mat.length;
		double sumCol = 0;
		for(int j = 0; j < rows; j++){
			sumCol = sumCol + mat[j][col];
		}
		return (sumCol);
	}

}
