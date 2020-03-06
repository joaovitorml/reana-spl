package tool.analyzers.strategies;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import expressionsolver.Expression;
import expressionsolver.ExpressionSolver;
import jadd.JADD;
import paramwrapper.ParametricModelChecker;
import tool.CyclicRdgException;
import tool.RDGNode;
import tool.UnknownFeatureException;
import tool.analyzers.IReliabilityAnalysisResults;
import tool.analyzers.MapBasedReliabilityResults;
import tool.analyzers.buildingblocks.Component;
import tool.analyzers.buildingblocks.ConcurrencyStrategy;
import tool.analyzers.buildingblocks.PresenceConditions;
import tool.analyzers.buildingblocks.ProductIterationHelper;
import tool.stats.CollectibleTimers;
import tool.stats.IFormulaCollector;
import tool.stats.ITimeCollector;

public class FeatureFamilyProductBasedAnalyzer {
	private static final Logger LOGGER = Logger.getLogger(FeatureFamilyProductBasedAnalyzer.class.getName());
	private ExpressionSolver expressionSolver;
    private FeatureBasedFirstPhase firstPhase;
    
    private ITimeCollector timeCollector;

    public FeatureFamilyProductBasedAnalyzer(JADD jadd,
                                       ParametricModelChecker modelChecker,
                                       ITimeCollector timeCollector,
                                       IFormulaCollector formulaCollector) {
        this.expressionSolver = new ExpressionSolver(jadd);

        this.timeCollector = timeCollector;

        this.firstPhase = new FeatureBasedFirstPhase(modelChecker,
                                                     formulaCollector);
    }
    
    /**
     * Evaluates the feature-product-based reliability value of an RDG node, based
     * on the reliabilities of the nodes on which it depends.
     *
     * @param node RDG node whose reliability is to be evaluated.
     * @return
     * @throws CyclicRdgException
     * @throws UnknownFeatureException
     */
    public IReliabilityAnalysisResults evaluateReliability(RDGNode node, Stream<Collection<String>> configurations, ConcurrencyStrategy concurrencyStrategy) throws CyclicRdgException {
        if (concurrencyStrategy == ConcurrencyStrategy.PARALLEL) {
            LOGGER.info("Solving the family-wide expression for each product in parallel.");
        }
        List<RDGNode> dependencies = node.getDependenciesTransitiveClosure();

        timeCollector.startTimer(CollectibleTimers.MODEL_CHECKING_TIME);
        // Lambda_v + alpha_v
        
        List<Component<String>> expressions = firstPhase.getReliabilityExpressions(dependencies, concurrencyStrategy);
        
        timeCollector.stopTimer(CollectibleTimers.MODEL_CHECKING_TIME);

        timeCollector.startTimer(CollectibleTimers.EXPRESSION_SOLVING_TIME);

        List<String> presenceConditions = dependencies.stream()
                .map(RDGNode::getPresenceCondition)
                .collect(Collectors.toList());
        Map<String, String> pcEquivalence = PresenceConditions.toEquivalenceClasses(presenceConditions);
        Map<String, String> eqClassToPC = pcEquivalence.entrySet().stream()
                .collect(Collectors.toMap(e -> e.getValue(),
                                          e -> e.getKey(),
                                          (a, b) -> a));
       
        String expression = getReliabilityFinalExpression(expressions);
        //String finalExpression = changeParameters(expression, pcEquivalence, expressions);

        Map<Collection<String>, Double> results;
        if (concurrencyStrategy == ConcurrencyStrategy.SEQUENTIAL) {
            Expression<Double> parsedExpression = expressionSolver.parseExpression(expression);
            results = ProductIterationHelper.evaluate(configuration -> evaluateSingle(parsedExpression,
                                                                                      configuration,
                                                                                      eqClassToPC),
                                                      configurations,
                                                      concurrencyStrategy);
        } else {
            results = ProductIterationHelper.evaluate(configuration -> evaluateSingle(expression,
                                                                                      configuration,
                                                                                      eqClassToPC),
                                                      configurations,
                                                      concurrencyStrategy);
        }

        timeCollector.stopTimer(CollectibleTimers.EXPRESSION_SOLVING_TIME);
        LOGGER.info("Formulae evaluation ok...");
        return new MapBasedReliabilityResults(results);
    }
    

	public String getReliabilityFinalExpression(List<Component<String>> expressionsRDG) {
		String temp;
		List<String> lista_Expr = null;
		
		for (Component<String> node : expressionsRDG) {
			temp = node.getAsset();
			//temp = putSpaces(temp);
			for (String key : lista_Expr) {
					temp = temp.replace(key, " ( ( "+key+" * "+lista_Expr.getString(key)+" ) + ( 1 - "+key+" ) ) ");
			}
			lista_Expr.put(node.getId()+" ",temp);
	}
		
		return lista_Expr.get(0);
	}
	
//	private String changeParameters(String expression, Map<String, String> eqClassToPC, List<Component<String>> expressionsRDG) {
//    	String newParam;
//    	String oldParam;
//    	for (Component<String> node : expressionsRDG) {
//    		if (!(node.getPresenceCondition().contentEquals("true"))) {
//    			newParam = eqClassToPC.get(node.getPresenceCondition());
//    			oldParam = node.getId();
//    			expression = expression.replaceAll(oldParam, newParam);
//    		}
//    		else {
//    			oldParam = node.getId();
//    			expression = expression.replaceAll(oldParam, "1");
//    		}
//    	}
//    	return expression;
//    }
	
    
    private Double evaluateSingle(Expression<Double> expression, Collection<String> configuration, Map<String, String> eqClassToPC) {
        Function<Map.Entry<String, String>, Boolean> isPresent = e -> PresenceConditions.isPresent(e.getValue(),
                                                                                                   configuration,
                                                                                                   expressionSolver);
        Map<String, Double> values = eqClassToPC.entrySet().stream()
            .collect(Collectors.toMap(e -> e.getKey(),
                                      isPresent.andThen(present -> present ? 1.0 : 0.0)));

        return expression.solve(values);

    }

    private Double evaluateSingle(String expression, Collection<String> configuration, Map<String, String> eqClassToPC) {
        Expression<Double> parsedExpression = expressionSolver.parseExpression(expression);
        return evaluateSingle(parsedExpression, configuration, eqClassToPC);
    }
}
