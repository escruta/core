package com.escruta.core.services;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Deterministic recursive-descent mathematical expression evaluator.
 * Uses BigDecimal (DECIMAL128) for basic arithmetic and bounded double-precision
 * evaluation for transcendental functions.
 */
public final class MathExpressionEvaluator {
    private static final int MAX_EXPRESSION_LENGTH = 1000;
    private static final int MAX_RECURSION_DEPTH = 64;
    private static final int MAX_FACTORIAL = 100;
    private static final MathContext ARITHMETIC_CTX = MathContext.DECIMAL128;
    private static final MathContext TRANSCENDENTAL_CTX = new MathContext(15, RoundingMode.HALF_UP);

    private MathExpressionEvaluator() {
    }

    public record EvaluationResult(
            BigDecimal value,
            String formatted
    ) {
    }

    public static EvaluationResult evaluate(String expression) {
        if (expression == null || expression.isBlank()) {
            throw new IllegalArgumentException("Expression cannot be empty");
        }
        String trimmed = expression.trim();
        if (trimmed.length() > MAX_EXPRESSION_LENGTH) {
            throw new IllegalArgumentException("Expression exceeds maximum length of " + MAX_EXPRESSION_LENGTH + " characters");
        }

        Parser parser = new Parser(trimmed);
        BigDecimal value = parser.parse();
        return new EvaluationResult(value, format(value));
    }

    public static String format(BigDecimal value) {
        if (value.compareTo(BigDecimal.ZERO) == 0) {
            return "0";
        }
        BigDecimal normalized = value.stripTrailingZeros();
        double asDouble = normalized.doubleValue();
        if (normalized.scale() <= 0 && (normalized.precision() - normalized.scale()) <= 20) {
            return normalized.toPlainString();
        }
        double abs = Math.abs(asDouble);
        if (abs >= 1e-6 && abs < 1e15) {
            return normalized.toPlainString();
        }
        return normalized.toString();
    }

    private static final class Parser {
        private final String input;
        private int pos = 0;
        private int depth = 0;

        private Parser(String input) {
            this.input = input;
        }

        private BigDecimal parse() {
            BigDecimal result = parseAddSub();
            skipWhitespace();
            if (pos < input.length()) {
                throw new IllegalArgumentException("Unexpected character '" + input.charAt(pos) + "' at position " + pos);
            }
            return result;
        }

        private BigDecimal parseAddSub() {
            BigDecimal left = parseMulDivMod();
            while (true) {
                skipWhitespace();
                if (match('+')) {
                    left = left.add(parseMulDivMod(), ARITHMETIC_CTX);
                } else if (match('-')) {
                    left = left.subtract(parseMulDivMod(), ARITHMETIC_CTX);
                } else {
                    return left;
                }
            }
        }

        private BigDecimal parseMulDivMod() {
            BigDecimal left = parseUnary();
            while (true) {
                skipWhitespace();
                if (match('*')) {
                    left = left.multiply(parseUnary(), ARITHMETIC_CTX);
                } else if (match('/')) {
                    BigDecimal right = parseUnary();
                    if (right.compareTo(BigDecimal.ZERO) == 0) {
                        throw new IllegalArgumentException("Division by zero");
                    }
                    left = left.divide(right, ARITHMETIC_CTX);
                } else if (match('%')) {
                    BigDecimal right = parseUnary();
                    if (right.compareTo(BigDecimal.ZERO) == 0) {
                        throw new IllegalArgumentException("Modulo by zero");
                    }
                    left = left.remainder(right, ARITHMETIC_CTX);
                } else {
                    return left;
                }
            }
        }

        private BigDecimal parseUnary() {
            skipWhitespace();
            if (match('+')) {
                return parseUnary();
            }
            if (match('-')) {
                return parseUnary().negate();
            }
            return parsePower();
        }

        private BigDecimal parsePower() {
            BigDecimal base = parsePrimary();
            skipWhitespace();
            if (match('^')) {
                BigDecimal exponent = parseUnary();
                return pow(base, exponent);
            }
            return base;
        }

        private BigDecimal parsePrimary() {
            skipWhitespace();
            if (pos >= input.length()) {
                throw new IllegalArgumentException("Unexpected end of expression");
            }

            if (match('(')) {
                enterDepth();
                try {
                    BigDecimal value = parseAddSub();
                    skipWhitespace();
                    if (!match(')')) {
                        throw new IllegalArgumentException("Missing closing parenthesis ')' at position " + pos);
                    }
                    return value;
                } finally {
                    depth--;
                }
            }

            char ch = input.charAt(pos);
            if ((ch >= '0' && ch <= '9') || ch == '.') {
                return parseNumber();
            }

            if (Character.isLetter(ch) || ch == '_') {
                String ident = parseIdentifier();
                skipWhitespace();
                if (match('(')) {
                    enterDepth();
                    try {
                        List<BigDecimal> args = parseArguments();
                        return evaluateFunction(ident, args);
                    } finally {
                        depth--;
                    }
                }
                return evaluateConstant(ident);
            }

            throw new IllegalArgumentException("Unexpected character '" + ch + "' at position " + pos);
        }

        private List<BigDecimal> parseArguments() {
            List<BigDecimal> args = new ArrayList<>();
            skipWhitespace();
            if (match(')')) {
                return args;
            }
            while (true) {
                args.add(parseAddSub());
                skipWhitespace();
                if (match(',')) {
                    continue;
                }
                if (match(')')) {
                    return args;
                }
                throw new IllegalArgumentException("Expected ',' or ')' at position " + pos);
            }
        }

        private BigDecimal parseNumber() {
            int start = pos;
            boolean hasDigits = false;
            boolean hasDot = false;

            while (pos < input.length()) {
                char c = input.charAt(pos);
                if (c >= '0' && c <= '9') {
                    hasDigits = true;
                    pos++;
                } else if (c == '.' && !hasDot) {
                    hasDot = true;
                    pos++;
                } else {
                    break;
                }
            }

            if (!hasDigits) {
                throw new IllegalArgumentException("Invalid number at position " + start);
            }

            if (pos < input.length() && (input.charAt(pos) == 'e' || input.charAt(pos) == 'E')) {
                int expStart = pos;
                pos++;
                if (pos < input.length() && (input.charAt(pos) == '+' || input.charAt(pos) == '-')) {
                    pos++;
                }
                int expDigits = 0;
                while (pos < input.length() && input.charAt(pos) >= '0' && input.charAt(pos) <= '9') {
                    expDigits++;
                    pos++;
                }
                if (expDigits == 0) {
                    pos = expStart;
                }
            }

            String token = input.substring(start, pos);
            try {
                return new BigDecimal(token);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid number: " + token);
            }
        }

        private String parseIdentifier() {
            int start = pos;
            while (pos < input.length()) {
                char c = input.charAt(pos);
                if (Character.isLetterOrDigit(c) || c == '_') {
                    pos++;
                } else {
                    break;
                }
            }
            return input.substring(start, pos).toLowerCase(Locale.ROOT);
        }

        private BigDecimal evaluateConstant(String name) {
            return switch (name) {
                case "pi" -> BigDecimal.valueOf(Math.PI);
                case "e" -> BigDecimal.valueOf(Math.E);
                case "tau" -> BigDecimal.valueOf(2.0 * Math.PI);
                default -> throw new IllegalArgumentException("Unknown identifier: " + name);
            };
        }

        private BigDecimal evaluateFunction(String name, List<BigDecimal> args) {
            return switch (name) {
                case "abs" -> {
                    requireArity(name, args, 1);
                    yield args.getFirst().abs();
                }
                case "neg" -> {
                    requireArity(name, args, 1);
                    yield args.getFirst().negate();
                }
                case "sign" -> {
                    requireArity(name, args, 1);
                    yield BigDecimal.valueOf(args.getFirst().signum());
                }
                case "floor" -> {
                    requireArity(name, args, 1);
                    yield args.getFirst().setScale(0, RoundingMode.FLOOR);
                }
                case "ceil" -> {
                    requireArity(name, args, 1);
                    yield args.getFirst().setScale(0, RoundingMode.CEILING);
                }
                case "round" -> {
                    if (args.size() == 1) {
                        yield args.getFirst().setScale(0, RoundingMode.HALF_UP);
                    }
                    if (args.size() == 2) {
                        int scale = toIntExact(args.get(1), "round scale");
                        if (scale < -20 || scale > 20) {
                            throw new IllegalArgumentException("round scale must be between -20 and 20");
                        }
                        yield args.getFirst().setScale(scale, RoundingMode.HALF_UP);
                    }
                    throw new IllegalArgumentException("Function 'round' expects 1 or 2 arguments");
                }
                case "sqrt" -> {
                    requireArity(name, args, 1);
                    double v = args.getFirst().doubleValue();
                    if (v < 0) {
                        throw new IllegalArgumentException("Square root of negative number");
                    }
                    yield fromDouble(Math.sqrt(v), "sqrt");
                }
                case "cbrt" -> {
                    requireArity(name, args, 1);
                    yield fromDouble(Math.cbrt(args.getFirst().doubleValue()), "cbrt");
                }
                case "sin" -> {
                    requireArity(name, args, 1);
                    yield fromDouble(Math.sin(args.getFirst().doubleValue()), "sin");
                }
                case "cos" -> {
                    requireArity(name, args, 1);
                    yield fromDouble(Math.cos(args.getFirst().doubleValue()), "cos");
                }
                case "tan" -> {
                    requireArity(name, args, 1);
                    yield fromDouble(Math.tan(args.getFirst().doubleValue()), "tan");
                }
                case "asin" -> {
                    requireArity(name, args, 1);
                    double v = args.getFirst().doubleValue();
                    if (v < -1.0 || v > 1.0) {
                        throw new IllegalArgumentException("asin argument must be in [-1, 1]");
                    }
                    yield fromDouble(Math.asin(v), "asin");
                }
                case "acos" -> {
                    requireArity(name, args, 1);
                    double v = args.getFirst().doubleValue();
                    if (v < -1.0 || v > 1.0) {
                        throw new IllegalArgumentException("acos argument must be in [-1, 1]");
                    }
                    yield fromDouble(Math.acos(v), "acos");
                }
                case "atan" -> {
                    requireArity(name, args, 1);
                    yield fromDouble(Math.atan(args.getFirst().doubleValue()), "atan");
                }
                case "atan2" -> {
                    requireArity(name, args, 2);
                    yield fromDouble(Math.atan2(args.get(0).doubleValue(), args.get(1).doubleValue()), "atan2");
                }
                case "sinh" -> {
                    requireArity(name, args, 1);
                    yield fromDouble(Math.sinh(args.getFirst().doubleValue()), "sinh");
                }
                case "cosh" -> {
                    requireArity(name, args, 1);
                    yield fromDouble(Math.cosh(args.getFirst().doubleValue()), "cosh");
                }
                case "tanh" -> {
                    requireArity(name, args, 1);
                    yield fromDouble(Math.tanh(args.getFirst().doubleValue()), "tanh");
                }
                case "ln" -> {
                    requireArity(name, args, 1);
                    double v = args.getFirst().doubleValue();
                    if (v <= 0) {
                        throw new IllegalArgumentException("ln argument must be positive");
                    }
                    yield fromDouble(Math.log(v), "ln");
                }
                case "log", "log10" -> {
                    requireArity(name, args, 1);
                    double v = args.getFirst().doubleValue();
                    if (v <= 0) {
                        throw new IllegalArgumentException(name + " argument must be positive");
                    }
                    yield fromDouble(Math.log10(v), name);
                }
                case "log2" -> {
                    requireArity(name, args, 1);
                    double v = args.getFirst().doubleValue();
                    if (v <= 0) {
                        throw new IllegalArgumentException("log2 argument must be positive");
                    }
                    yield fromDouble(Math.log(v) / Math.log(2.0), "log2");
                }
                case "exp" -> {
                    requireArity(name, args, 1);
                    yield fromDouble(Math.exp(args.getFirst().doubleValue()), "exp");
                }
                case "deg" -> {
                    requireArity(name, args, 1);
                    yield fromDouble(Math.toDegrees(args.getFirst().doubleValue()), "deg");
                }
                case "rad" -> {
                    requireArity(name, args, 1);
                    yield fromDouble(Math.toRadians(args.getFirst().doubleValue()), "rad");
                }
                case "pow" -> {
                    requireArity(name, args, 2);
                    yield pow(args.get(0), args.get(1));
                }
                case "mod" -> {
                    requireArity(name, args, 2);
                    if (args.get(1).compareTo(BigDecimal.ZERO) == 0) {
                        throw new IllegalArgumentException("Modulo by zero");
                    }
                    yield args.get(0).remainder(args.get(1), ARITHMETIC_CTX);
                }
                case "min" -> {
                    requireMinArity(name, args);
                    BigDecimal min = args.getFirst();
                    for (int i = 1; i < args.size(); i++) {
                        if (args.get(i).compareTo(min) < 0) {
                            min = args.get(i);
                        }
                    }
                    yield min;
                }
                case "max" -> {
                    requireMinArity(name, args);
                    BigDecimal max = args.getFirst();
                    for (int i = 1; i < args.size(); i++) {
                        if (args.get(i).compareTo(max) > 0) {
                            max = args.get(i);
                        }
                    }
                    yield max;
                }
                case "sum" -> {
                    requireMinArity(name, args);
                    BigDecimal sum = BigDecimal.ZERO;
                    for (BigDecimal arg : args) {
                        sum = sum.add(arg, ARITHMETIC_CTX);
                    }
                    yield sum;
                }
                case "avg", "mean" -> {
                    requireMinArity(name, args);
                    BigDecimal sum = BigDecimal.ZERO;
                    for (BigDecimal arg : args) {
                        sum = sum.add(arg, ARITHMETIC_CTX);
                    }
                    yield sum.divide(BigDecimal.valueOf(args.size()), ARITHMETIC_CTX);
                }
                case "factorial", "fact" -> {
                    requireArity(name, args, 1);
                    int n = toIntExact(args.getFirst(), "factorial");
                    if (n < 0 || n > MAX_FACTORIAL) {
                        throw new IllegalArgumentException("factorial argument must be between 0 and " + MAX_FACTORIAL);
                    }
                    BigDecimal fact = BigDecimal.ONE;
                    for (int i = 2; i <= n; i++) {
                        fact = fact.multiply(BigDecimal.valueOf(i));
                    }
                    yield fact;
                }
                default -> throw new IllegalArgumentException("Unknown function: " + name);
            };
        }

        private static BigDecimal pow(BigDecimal base, BigDecimal exponent) {
            if (isIntegral(exponent)) {
                try {
                    int intExp = exponent.intValueExact();
                    if (intExp >= -1000 && intExp <= 1000) {
                        if (base.compareTo(BigDecimal.ZERO) == 0 && intExp < 0) {
                            throw new IllegalArgumentException("Zero cannot be raised to a negative power");
                        }
                        return base.pow(intExp, ARITHMETIC_CTX);
                    }
                } catch (ArithmeticException ignored) {
                    // Fall through to double evaluation for out-of-int-range exponents
                }
            }
            double b = base.doubleValue();
            double e = exponent.doubleValue();
            if (b < 0 && !isIntegral(exponent)) {
                throw new IllegalArgumentException("Negative base with non-integer exponent");
            }
            if (b == 0.0 && e < 0.0) {
                throw new IllegalArgumentException("Zero cannot be raised to a negative power");
            }
            return fromDouble(Math.pow(b, e), "power");
        }

        private static boolean isIntegral(BigDecimal value) {
            BigDecimal stripped = value.stripTrailingZeros();
            return stripped.scale() <= 0;
        }

        private static int toIntExact(BigDecimal value, String context) {
            try {
                return value.stripTrailingZeros().intValueExact();
            } catch (ArithmeticException e) {
                throw new IllegalArgumentException(context + " must be an integer");
            }
        }

        private static BigDecimal fromDouble(double value, String context) {
            if (Double.isNaN(value) || Double.isInfinite(value)) {
                throw new IllegalArgumentException("Result of " + context + " is undefined or overflows");
            }
            if (value == 0.0) {
                return BigDecimal.ZERO;
            }
            return BigDecimal.valueOf(value).round(TRANSCENDENTAL_CTX);
        }

        private static void requireArity(String name, List<BigDecimal> args, int expected) {
            if (args.size() != expected) {
                throw new IllegalArgumentException("Function '" + name + "' expects " + expected + " argument(s), got " + args.size());
            }
        }

        private static void requireMinArity(String name, List<BigDecimal> args) {
            if (args.isEmpty()) {
                throw new IllegalArgumentException("Function '" + name + "' expects at least " + 1 + " argument(s)");
            }
        }

        private void enterDepth() {
            depth++;
            if (depth > MAX_RECURSION_DEPTH) {
                throw new IllegalArgumentException("Expression nesting exceeds maximum depth of " + MAX_RECURSION_DEPTH);
            }
        }

        private void skipWhitespace() {
            while (pos < input.length() && Character.isWhitespace(input.charAt(pos))) {
                pos++;
            }
        }

        private boolean match(char expected) {
            if (pos < input.length() && input.charAt(pos) == expected) {
                pos++;
                return true;
            }
            return false;
        }
    }
}
