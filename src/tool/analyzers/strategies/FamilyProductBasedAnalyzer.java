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
import tool.analyzers.buildingblocks.ConcurrencyStrategy;
import tool.stats.CollectibleTimers;
import tool.stats.IFormulaCollector;
import tool.stats.ITimeCollector;

/**
 * Orchestrator of family-product-based analyses.
 */
public class FamilyProductBasedAnalyzer {
    private static final Logger LOGGER = Logger.getLogger(FamilyProductBasedAnalyzer.class.getName());

    private FamilyBasedFirstPhase firstPhase;
    private ProductBasedAnnotativeExpressionEvaluator productBasedEvaluator;

    private ITimeCollector timeCollector;
    private IFormulaCollector formulaCollector;

    public FamilyProductBasedAnalyzer(JADD jadd,
                               ParametricModelChecker modelChecker,
                               ITimeCollector timeCollector,
                               IFormulaCollector formulaCollector) {
        this.firstPhase = new FamilyBasedFirstPhase(modelChecker);
        this.productBasedEvaluator = new ProductBasedAnnotativeExpressionEvaluator(jadd);

        this.timeCollector = timeCollector;
        this.formulaCollector = formulaCollector;
    }

    /**
     * Evaluates the family-product-based reliability function of an RDG node.
     *
     * @param node RDG node whose reliability is to be evaluated.
     * @return
     * @throws CyclicRdgException
     */
    public IReliabilityAnalysisResults evaluateReliability(RDGNode node, Stream<Collection<String>> configurations, ConcurrencyStrategy concurrencyStrategy) throws CyclicRdgException {
        List<RDGNode> dependencies = node.getDependenciesTransitiveClosure();

        timeCollector.startTimer(CollectibleTimers.MODEL_CHECKING_TIME);
        // Lambda_v + alpha_v
        String expression = firstPhase.getReliabilityExpression(dependencies);
        formulaCollector.collectFormula(node, expression);
        timeCollector.stopTimer(CollectibleTimers.MODEL_CHECKING_TIME);

        if (concurrencyStrategy == ConcurrencyStrategy.PARALLEL) {
            LOGGER.info("Solving the family-wide expression for each product in parallel.");
        }
        timeCollector.startTimer(CollectibleTimers.EXPRESSION_SOLVING_TIME);

        Map<Collection<String>, Double> results = productBasedEvaluator.evaluate(expression,
                                                                                 dependencies,
                                                                                 configurations,
                                                                                 concurrencyStrategy);

        timeCollector.stopTimer(CollectibleTimers.EXPRESSION_SOLVING_TIME);
        LOGGER.info("Formulae evaluation ok...");
        return new MapBasedReliabilityResults(results);
    }

}
