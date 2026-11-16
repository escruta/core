package com.escruta.core.services;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("MathExpressionEvaluator Tests")
class MathExpressionEvaluatorTest {
    @Test
    @DisplayName("should evaluate exact decimal arithmetic without binary floating point noise")
    void evaluatesExactDecimalArithmetic() {
        assertThat(MathExpressionEvaluator.evaluate("0.1 + 0.2").formatted()).isEqualTo("0.3");
        assertThat(MathExpressionEvaluator.evaluate("(1250 * 0.18) / 3").formatted()).isEqualTo("75");
        assertThat(MathExpressionEvaluator.evaluate("10 % 3").formatted()).isEqualTo("1");
        assertThat(MathExpressionEvaluator.evaluate("1.5e2 + 50").formatted()).isEqualTo("200");
    }

    @Test
    @DisplayName("should respect standard operator precedence and associativity")
    void respectsPrecedenceAndAssociativity() {
        assertThat(MathExpressionEvaluator.evaluate("2 + 3 * 4").formatted()).isEqualTo("14");
        assertThat(MathExpressionEvaluator.evaluate("(2 + 3) * 4").formatted()).isEqualTo("20");
        assertThat(MathExpressionEvaluator.evaluate("2 ^ 3 ^ 2").formatted()).isEqualTo("512");
        assertThat(MathExpressionEvaluator.evaluate("-2 ^ 2").formatted()).isEqualTo("-4");
        assertThat(MathExpressionEvaluator.evaluate("(-2) ^ 2").formatted()).isEqualTo("4");
        assertThat(MathExpressionEvaluator.evaluate("2 ^ -2").formatted()).isEqualTo("0.25");
    }

    @Test
    @DisplayName("should evaluate constants and scientific or statistical functions")
    void evaluatesConstantsAndFunctions() {
        assertThat(MathExpressionEvaluator.evaluate("sqrt(16) + cbrt(27)").formatted()).isEqualTo("7");
        assertThat(MathExpressionEvaluator.evaluate("round(sqrt(2) ^ 2, 10)").formatted()).isEqualTo("2");
        assertThat(MathExpressionEvaluator.evaluate("avg(10, 20, 30, 40)").formatted()).isEqualTo("25");
        assertThat(MathExpressionEvaluator.evaluate("sum(1.1, 2.2, 3.3)").formatted()).isEqualTo("6.6");
        assertThat(MathExpressionEvaluator.evaluate("min(9, -3, 4) + max(9, -3, 4)").formatted()).isEqualTo("6");
        assertThat(MathExpressionEvaluator.evaluate("factorial(5)").formatted()).isEqualTo("120");
        assertThat(MathExpressionEvaluator.evaluate("log10(1000) + log2(8)").formatted()).isEqualTo("6");
        assertThat(MathExpressionEvaluator.evaluate("round(ln(e), 4)").formatted()).isEqualTo("1");
        assertThat(MathExpressionEvaluator.evaluate("round(cos(pi), 6)").formatted()).isEqualTo("-1");
        assertThat(MathExpressionEvaluator.evaluate("round(deg(pi / 2), 4)").formatted()).isEqualTo("90");
    }

    @Test
    @DisplayName("should reject invalid expressions and domain errors")
    void rejectsInvalidAndDomainErrors() {
        assertThatThrownBy(() -> MathExpressionEvaluator.evaluate("   ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> MathExpressionEvaluator.evaluate("10 / 0"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Division by zero");
        assertThatThrownBy(() -> MathExpressionEvaluator.evaluate("sqrt(-1)")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> MathExpressionEvaluator.evaluate("ln(0)")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> MathExpressionEvaluator.evaluate("unknownFunc(5)")).isInstanceOf(
                IllegalArgumentException.class);
        assertThatThrownBy(() -> MathExpressionEvaluator.evaluate("(1 + 2")).isInstanceOf(IllegalArgumentException.class);
    }
}
