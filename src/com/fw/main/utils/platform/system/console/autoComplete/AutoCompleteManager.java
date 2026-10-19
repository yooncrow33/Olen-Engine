package com.fw.main.utils.platform.system.console.autoComplete;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class AutoCompleteManager {
    private final Map<Integer, List<CandidateRule>> ruleMap = new ConcurrentHashMap<>();

    public CandidateBuilder suggestAt(int tokenIndex, String candidateValue) {
        CandidateRule rule = new CandidateRule(candidateValue);
        ruleMap.computeIfAbsent(tokenIndex, k -> new ArrayList<>()).add(rule);
        return new CandidateBuilder(tokenIndex, rule);
    }

    public CandidateBuilder suggestAt(int tokenIndex, List<? extends ConsoleLabel> labelList) {
        CandidateRule rule = new CandidateRule(() -> extractLabels(labelList));
        ruleMap.computeIfAbsent(tokenIndex, k -> new ArrayList<>()).add(rule);
        return new CandidateBuilder(tokenIndex, rule);
    }

    private static List<String> extractLabels(List<? extends ConsoleLabel> labelList) {
        if (labelList == null || labelList.isEmpty()) return Collections.emptyList();
        List<String> result = new ArrayList<>(labelList.size());
        for (ConsoleLabel item : labelList) {
            if (item != null && item.getConsoleLabel() != null) {
                result.add(item.getConsoleLabel());
            }
        }
        return result;
    }

    public TreeBuilder tree() {
        return new TreeBuilder(this);
    }

    public AutoCompleteManager tree(java.util.function.Consumer<TreeBuilder> rootConsumer) {
        rootConsumer.accept(new TreeBuilder(this));
        return this;
    }

    public static class CandidateBuilder {
        private final int tokenIndex;
        private final CandidateRule rule;

        public CandidateBuilder(int tokenIndex, CandidateRule rule) {
            this.tokenIndex = tokenIndex;
            this.rule = rule;
        }

        public WhenClause whenToken(int conditionIndex) {
            return new WhenClause(this, conditionIndex);
        }

        public class WhenClause {
            private final CandidateBuilder parent;
            private final int conditionIndex;

            public WhenClause(CandidateBuilder parent, int conditionIndex) {
                this.parent = parent;
                this.conditionIndex = conditionIndex;
            }

            public CandidateBuilder is(String expectedValue) {
                validateIndex();
                rule.addCondition(new Condition(conditionIndex, expectedValue));
                return parent;
            }

            public CandidateBuilder is(List<? extends ConsoleLabel> labelList) {
                validateIndex();
                rule.addCondition(new Condition(conditionIndex, () -> extractLabels(labelList)));
                return parent;
            }

            private void validateIndex() {
                if (conditionIndex >= tokenIndex) {
                    System.err.println("Auto complete condition error: conditionIndex must be less than tokenIndex!");
                }
                for (Condition condition : rule.getConditions()) {
                    if (condition.getTargetIndex() == conditionIndex) {
                        System.err.println("overlapped condition!");
                    }
                }
            }
        }
    }

    private void sortCandidates(String input, List<String> candidates) {
        if (input == null || input.isEmpty()) return;
        candidates.sort((a, b) -> {
            int scoreA = calculateScore(input, a);
            int scoreB = calculateScore(input, b);
            if (scoreA != scoreB) return Integer.compare(scoreB, scoreA);
            return a.compareToIgnoreCase(b);
        });
    }

    private int calculateScore(String input, String candidate) {
        if (candidate.startsWith(input)) return 1000 + (100 - candidate.length());
        if (candidate.toLowerCase().startsWith(input.toLowerCase())) return 500;
        if (candidate.contains(input)) return 100;
        return 0;
    }

    public List<String> getCandidates(List<String> currentTokens, String currentInputToken) {
        int targetIndex = currentTokens.isEmpty() ? 0 : currentTokens.size() - 1;
        List<CandidateRule> rules = ruleMap.get(targetIndex);

        if (rules == null || rules.isEmpty() || currentInputToken == null || currentInputToken.isEmpty()) {
            return Collections.emptyList();
        }

        List<String> activeCandidates = new ArrayList<>();
        for (CandidateRule rule : rules) {
            if (rule.isSatisfied(currentTokens)) {
                for (String val : rule.getCandidateValues()) {
                    if (val.toLowerCase().contains(currentInputToken.toLowerCase())) {
                        activeCandidates.add(val);
                    }
                }
            }
        }

        sortCandidates(currentInputToken, activeCandidates);
        return activeCandidates;
    }

    public List<String> getAllCandidates(List<String> currentTokens) {
        int targetIndex = currentTokens.isEmpty() ? 0 : currentTokens.size() - 1;
        List<CandidateRule> rules = ruleMap.get(targetIndex);

        if (rules == null || rules.isEmpty()) {
            return Collections.emptyList();
        }

        List<String> allCandidates = new ArrayList<>();
        for (CandidateRule rule : rules) {
            if (rule.isSatisfied(currentTokens)) {
                allCandidates.addAll(rule.getCandidateValues());
            }
        }
        return allCandidates;
    }
}