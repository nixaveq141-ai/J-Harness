package example;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

class CalculatorTest {
    @Test void addsIntegers() { assertEquals(5, new Calculator().add(2, 3)); }
}
