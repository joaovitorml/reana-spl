package tool.analyzers.buildingblocks;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Responsible for variability encoding of expressions (gamma).
 *
 */
public class ExpressionsVariabilityEncoding {

    private static DerivationFunction<String, String, String> derive150Model =
            DerivationFunction.abstractDerivation(ExpressionsVariabilityEncoding::ifThenElse,   // How do we combine an expression,
                                                                                                // a presence variable, and the default value?
                                                  ExpressionsVariabilityEncoding::inline,       // How do we replace placeholders with the
                                                                                                // corresponding (already derived) variants?
                                                  "1");                                         // Default value whenever a behavior is absent.

    /**
     * Encode many expressions in a single 150% one, whose variables represent equivalence classes
     * for the presence conditions in the RDG.
     *
     * @param sortedExpressions Topologically sorted (according to the RDG dependencies) reliability expressions.
     *      The variables in these expressions correspond to RDG node IDs.
     * @return A variability-encoded expression whose variables represent equivalence classes
     *      for the presence conditions in the RDG.
     */
    public static String encodeVariability(List<Component<String>> sortedExpressions) {
        List<String> presenceConditions = sortedExpressions.stream()
                .map(Component::getPresenceCondition)
                .collect(Collectors.toList());

        Map<String, String> pcEquivalence = PresenceConditions.toEquivalenceClasses(presenceConditions);
        String derived150Model = Component.deriveFromMany(sortedExpressions,
                                                          derive150Model,
                                                          c -> pcEquivalence.get(c.getPresenceCondition()));
        return derived150Model;
    }

    /**
     * If-then-else operator for expressions.
     */
    private static String ifThenElse(String variable,
                                     String ifTrueExpr,
                                     String ifFalseExpr) {
        return variable + "*(" + ifTrueExpr + ")"
             + " + (1 - " + variable + ")*(" + ifFalseExpr + ")";
    }

    /**
     * Inlining of "derived" expressions (i.e., ones that were already subject to this
     * very process) in place of variables.
     * @param expression
     * @param toBeEncoded Expressions that act as replacements for variables in the input expression.
     * @return
     */
    private static String inline(String expression, Map<String, String> toBeEncoded) {
        System.out.println(">>>>>>>>>>>>>>>>>>>");
        System.out.println("Inlining: "+expression);
        String inlined = expression;
        for (Map.Entry<String, String> entry: toBeEncoded.entrySet()) {
            inlined = inlined.replaceAll("\\b"+entry.getKey()+"\\b",        // Matches a whole word
                                         "(" + entry.getValue() + ")");
            System.out.println("    " + inlined);
        }
        System.out.println("-------------------");
        return inlined;
    }
}
