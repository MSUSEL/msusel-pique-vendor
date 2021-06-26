package calibration;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import com.sun.scenario.effect.impl.sw.sse.SSEBlend_SRC_OUTPeer;
import org.apache.commons.lang3.ArrayUtils;

import pique.calibration.IWeighter;
import pique.calibration.WeightResult;
import pique.model.ModelNode;
import pique.model.QualityModel;

/** 
 * @author Andrew Johnson
 *
 * This class should weight based off of pairwise comparisons for the quality aspect level but utilize manual weights for 
 * product factors to the quality aspect level. This allows stakeholder
 * interest to be represented but reduces the extreme amount of comparisons.
 */
public class BinaryCWEWeighter implements IWeighter{
	private String[] qaNames;
	private String[] pfNames;
	private double[][] manWeights;
	private double[][] comparisonMat;
	private int numQA;
	private int numPF;
	

	@Override
	public Set<WeightResult> elicitateWeights(QualityModel qualityModel, Path... externalInput) { //Java varargs
		numQA = qualityModel.getQualityAspects().size();
		//System.out.println(numQA);
		numPF = qualityModel.getProductFactors().size();
		//System.out.println(numPF);
		qaNames = new String[numQA];
		pfNames = new String[numPF];
		manWeights = new double[numPF][numQA];
		comparisonMat = new double[numQA][numQA];
		
		String pathToCsv = "src/main/resources/comparisons.csv";
		String pfPrefix = "Category CWE-";
		BufferedReader csvReader;
		int lineCount = 0;
		int pfNamesIndex = 0;
		try {
			csvReader = new BufferedReader(new FileReader(pathToCsv));
		
			String row;
			while (( row = csvReader.readLine()) != null) {
				String[] data = row.split(",");
				/*for (int j = 0; j < data.length; j++){
					System.out.println(data.length);
				}*/
				if (lineCount == 0) {
					for (int i = 1; i < data.length; i++) {
						qaNames[i-1] = data[i];
						/*System.out.println("qaNames");
						System.out.println(data[i]);*/
					}
				}
				else if (lineCount < numQA+1) { //tqi weights, fill values for ahpMat
					for (int i = 1; i < data.length; i++) {
						comparisonMat[lineCount-1][i-1] = Double.parseDouble(data[i].trim());
						/*System.out.println("comparisonMat");
						System.out.println(data[i]);*/
					}
				}
				else { //QA weights, fill values for manWeights
					pfNames[pfNamesIndex] = data[0];
					/*System.out.println("pfNames");
					System.out.println(pfNames[pfNamesIndex]);*/
					pfNamesIndex++;
					for (int i = 1; i < data.length; i++) {
						//parse out the integer for the CWE number and add appropriate prefix
						//pfNames[lineCount-numQA-1] = pfPrefix + Integer.toString(Integer.parseInt(data[i].replaceAll("[\\D]", "")));
						manWeights[lineCount-numQA-1][i-1] = Double.parseDouble(data[i].trim());
						/*System.out.println("manWeight");
						System.out.println(data[i]);*/
					}
				}
			    lineCount++;
			}
			csvReader.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		
		Set<WeightResult> weights = new HashSet<>();

		//there is certainly a better way to implement this next part
		
		//set the weights as a simple 1/number of children for each edge
		averageWeights(qualityModel.getMeasures().values(),weights);
		averageWeights(qualityModel.getDiagnostics().values(),weights);
		averageWeights(qualityModel.getProductFactors().values(),weights);


		//set the weights for edges going into quality aspects based on manual weighting
		//System.out.println("Antes de llamar manualWeights: ");
		//System.out.println(qualityModel.getQualityAspects().values().getClass());
		manualWeights(qualityModel.getQualityAspects().values(),weights);
		
		//set the weights for edges going into tqi based on ahp
		ahpWeights(qualityModel.getTqi(), weights);
		
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
	private void ahpWeights(ModelNode tqi, Set<WeightResult> weights) {
		double[] ahpVec = new double[numQA];
		//normalize by column sums
		double[][] norm = normalizeByColSum(comparisonMat);
		//get the row means
		for (int i= 0; i < numQA; i++) {
			ahpVec[i] = rowMean(norm,i);
		}
		WeightResult weightResult = new WeightResult(tqi.getName());	
		tqi.getChildren().values().forEach(child -> 
			weightResult.setWeight(child.getName(), ahpVec[ArrayUtils.indexOf(qaNames, child.getName())]));
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
	
	//weights based on manual entry
	private void manualWeights(Collection<ModelNode> nodes, Set<WeightResult> weights) {
		double[][] normMat = normalizeByColSum(manWeights);
		/*for (int i = 0; i < 12; i++){
			for (int j = 0; j < 6; j++){
				System.out.print(normMat[i][j]);
				System.out.print(" ");
			}
			System.out.println();
		}*/
		

		for (ModelNode node : nodes) {
			WeightResult weightResult = new WeightResult(node.getName());
			System.out.print("Quality aspect node: ");
			System.out.println(node.getName());
			System.out.print("QA node's children: ");
			System.out.println(node.getChildren());
			for (ModelNode child : node.getChildren().values()) {
				System.out.println(child);
				System.out.println(node.getChildren().values());
				System.out.println(child.getName());
				for(String pfname: pfNames){
					System.out.print(pfname);
					System.out.print(" ");
				}
				System.out.println();
				for(String qaname: qaNames){
					System.out.print(qaname);
					System.out.print(" ");
				}
				System.out.println();
				System.out.println(ArrayUtils.indexOf(pfNames,child.getName()));
				System.out.println(ArrayUtils.indexOf(qaNames, node.getName()));
				System.out.println(normMat[ArrayUtils.indexOf(pfNames, child.getName())][ArrayUtils.indexOf(qaNames, node.getName())]);
				weightResult.setWeight(child.getName(), normMat[ArrayUtils.indexOf(pfNames, child.getName())][ArrayUtils.indexOf(qaNames, node.getName())]);
			}
			//System.out.println("Hello");
			//System.out.println(weightResult.getWeights());
			weights.add(weightResult);
		}
	}
	
}
