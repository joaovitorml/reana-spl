package tool.analyzers.strategies;

import jadd.JADD;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.stream.Stream;

import paramwrapper.ParametricModelChecker;
import tool.CyclicRdgException;
import tool.RDGNode;
import tool.analyzers.IReliabilityAnalysisResults;
import tool.analyzers.MapBasedReliabilityResults;
import tool.analyzers.buildingblocks.Component;
import tool.analyzers.buildingblocks.ConcurrencyStrategy;
import tool.analyzers.buildingblocks.ExpressionsVariabilityEncoding;
import tool.stats.CollectibleTimers;
import tool.stats.IFormulaCollector;
import tool.stats.ITimeCollector;

/**
 * Orchestrator of feature-family-product-based analyses.
 */
public class FeatureFamilyProductBasedAnalyzer {
    private static final Logger LOGGER = Logger.getLogger(FamilyProductBasedAnalyzer.class.getName());

    private FeatureBasedFirstPhase firstPhase;
    private ProductBasedAnnotativeExpressionEvaluator productBasedEvaluator;

    private ITimeCollector timeCollector;
    private IFormulaCollector formulaCollector;

    public FeatureFamilyProductBasedAnalyzer(
            JADD jadd,
            ParametricModelChecker modelChecker,
            ITimeCollector timeCollector,
            IFormulaCollector formulaCollector) {
        this.firstPhase = new FeatureBasedFirstPhase(modelChecker,
                                                     formulaCollector);
        this.productBasedEvaluator = new ProductBasedAnnotativeExpressionEvaluator(jadd);

        this.timeCollector = timeCollector;
        this.formulaCollector = formulaCollector;
    }

    /**
     * Evaluates the feature-family-product-based reliability function of an RDG node.
     *
     * @param node RDG node whose reliability is to be evaluated.
     * @return
     * @throws CyclicRdgException
     */
    public IReliabilityAnalysisResults evaluateReliability(RDGNode node, Stream<Collection<String>> configurations, ConcurrencyStrategy concurrencyStrategy) throws CyclicRdgException {
        List<RDGNode> dependencies = node.getDependenciesTransitiveClosure();

        timeCollector.startTimer(CollectibleTimers.MODEL_CHECKING_TIME);
        // Alpha_v
        List<Component<String>> expressions = firstPhase.getReliabilityExpressions(dependencies, concurrencyStrategy);
        timeCollector.stopTimer(CollectibleTimers.MODEL_CHECKING_TIME);

        timeCollector.startTimer(CollectibleTimers.EXPRESSION_SOLVING_TIME);

        // The expressions so far have variables that correspond to RDG node IDs.
        String familyWideExpression = encodeVariability(expressions);
        // After encoding, the variables in the expression are different; now they
        // correspond to equivalence classes for the presence conditions of the RDG nodes
        // identified by the previous variables.
        formulaCollector.collectFormula(node, familyWideExpression);

        if (concurrencyStrategy == ConcurrencyStrategy.PARALLEL) {
            LOGGER.info("Solving the family-wide expression for each product in parallel.");
        }

        // This step is shared with the family-product-based strategy.
        Map<Collection<String>, Double> results = productBasedEvaluator.evaluate(familyWideExpression,
                                                                                 dependencies,
                                                                                 configurations,
                                                                                 concurrencyStrategy);

        timeCollector.stopTimer(CollectibleTimers.EXPRESSION_SOLVING_TIME);
        LOGGER.info("Formulae evaluation ok...");
        return new MapBasedReliabilityResults(results);
    }

    /**
     * Encode many expressions in a single 150% one, whose variables represent equivalence classes
     * for the presence conditions in the RDG.
     *
     * @param sortedExpressions Topologically sorted (according to the RDG dependencies) reliability expressions.
     *      The variables in these expressions correspond to RDG node IDs.
     * @return A variability-encoded expression whose variables represent equivalence classes
     *      for the presence conditions in the RDG.
     */
    private String encodeVariability(List<Component<String>> sortedExpressions) {
        return ExpressionsVariabilityEncoding.encodeVariability(sortedExpressions);
    }

}
