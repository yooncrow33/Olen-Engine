package com.fw.main.utils.platform.system.console.autoComplete;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

public class CandidateRule {
    private final Supplier<List<String>> candidateSupplier;
    private final List<Condition> conditions = new ArrayList<>();

    public CandidateRule(String staticValue) {
        this.candidateSupplier = () -> Collections.singletonList(staticValue);
    }

    public CandidateRule(Supplier<List<String>> candidateSupplier) {
        this.candidateSupplier = candidateSupplier;
    }

    public void addCondition(Condition condition) {
        this.conditions.add(condition);
    }

    public List<Condition> getConditions() {
        return conditions;
    }

    public List<String> getCandidateValues() {
        List<String> values = candidateSupplier.get();
        return values != null ? values : Collections.emptyList();
    }

    public boolean isSatisfied(List<String> currentTokens) {
        for (Condition condition : conditions) {
            if (!condition.matches(currentTokens)) {
                return false;
            }
        }
        return true;
    }
}