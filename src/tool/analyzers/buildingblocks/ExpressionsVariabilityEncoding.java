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
            DerivationFunction.abstractDerivation(ExpressionsVariabilityEncoding::ifThenElse,
                                                  ExpressionsVariabilityEncoding::inline,
                                                  "1");

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

    private static String ifThenElse(String variable,
                                     String ifTrueExpr,
                                     String ifFalseExpr) {
        return variable + "*(" + ifTrueExpr + ")"
             + " + (1 - " + variable + ")*(" + ifFalseExpr + ")";
    }

    private static String inline(String expression, Map<String, String> toBeEncoded) {
        System.out.println(">>>>>>>>>>>>>>>>>>>");
        System.out.println("Inlining: "+expression);
        String inlined = expression;
        for (Map.Entry<String, String> entry: toBeEncoded.entrySet()) {
            inlined = inlined.replaceAll("\\b"+entry.getKey()+"\\b",
                                         "(" + entry.getValue() + ")");
            System.out.println("    " + inlined);
        }
        System.out.println("-------------------");
        return inlined;
    }
}
