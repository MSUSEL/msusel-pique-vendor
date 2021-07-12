package evaluator;

import pique.evaluation.Evaluator;
import pique.model.Measure;
import pique.model.ModelNode;

public class MeasureEvaluator extends Evaluator {
    @Override
    public double evaluate(ModelNode modelNode) {
        Measure node = (Measure)modelNode;

        double value = 0.0;
        value = node.getChildren().values().stream()
                .mapToDouble(ModelNode::getValue)
                .sum();
        /*for (ModelNode child : modelNode.getChildren().values()) {
            value += child.getValue() * modelNode.getWeight(child.getName());
        }*/

        // Normalize
        value = node.getNormalizerObject().normalize(value);

        // Apply utility function
        return node.getUtilityFunctionObject().utilityFunction(value, node.getThresholds(), node.isPositive());
    }
}
