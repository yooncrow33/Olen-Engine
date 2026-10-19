package com.fw.main.utils.platform.system.console.autoComplete;

import java.util.List;
import java.util.function.Supplier;

public class Condition {
    private final int targetIndex;
    private final String expectedValue;
    private final Supplier<List<String>> dynamicValuesSupplier;

    public int getTargetIndex() {
        return targetIndex;
    }

    public Condition(int targetIndex, String expectedValue) {
        this.targetIndex = targetIndex;
        this.expectedValue = expectedValue;
        this.dynamicValuesSupplier = null;
    }

    public Condition(int targetIndex, Supplier<List<String>> dynamicValuesSupplier) {
        this.targetIndex = targetIndex;
        this.expectedValue = null;
        this.dynamicValuesSupplier = dynamicValuesSupplier;
    }

    public boolean matches(List<String> currentTokens) {
        if (targetIndex < 0 || targetIndex >= currentTokens.size()) {
            return false;
        }

        String actualToken = currentTokens.get(targetIndex);

        if (dynamicValuesSupplier != null) {
            List<String> validValues = dynamicValuesSupplier.get();
            if (validValues == null || validValues.isEmpty()) return false;
            for (String val : validValues) {
                if (actualToken.equalsIgnoreCase(val)) return true;
            }
            return false;
        }

        return actualToken.equalsIgnoreCase(expectedValue);
    }
}