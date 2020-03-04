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

        // TO-DO: ITE
        String familyWideExpression = encodeVariability(expressions);
        formulaCollector.collectFormula(node, familyWideExpression);

        if (concurrencyStrategy == ConcurrencyStrategy.PARALLEL) {
            LOGGER.info("Solving the family-wide expression for each product in parallel.");
        }

        Map<Collection<String>, Double> results = productBasedEvaluator.evaluate(familyWideExpression,
                                                                                 dependencies,
                                                                                 configurations,
                                                                                 concurrencyStrategy);

        timeCollector.stopTimer(CollectibleTimers.EXPRESSION_SOLVING_TIME);
        LOGGER.info("Formulae evaluation ok...");
        return new MapBasedReliabilityResults(results);
    }

    private String encodeVariability(List<Component<String>> sortedExpressions) {
        return ExpressionsVariabilityEncoding.encodeVariability(sortedExpressions);
    }

}
