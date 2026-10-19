package com.fw.main.utils.platform.system.console.autoComplete;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

public class TreeBuilder {
    private final AutoCompleteManager manager;
    private final int depth;
    private final List<Object> conditions; // String 또는 List<? extends ConsoleLabel>

    // 루트 생성용
    public TreeBuilder(AutoCompleteManager manager) {
        this.manager = manager;
        this.depth = 0;
        this.conditions = Collections.emptyList();
    }

    // 자식 노드 생성용
    private TreeBuilder(AutoCompleteManager manager, int depth, List<Object> conditions) {
        this.manager = manager;
        this.depth = depth;
        this.conditions = conditions;
    }

    // 1. 단일 리터럴 등록
    public TreeBuilder then(String value) {
        registerRule(value);
        return createChildBuilder(value);
    }

    public TreeBuilder then(String value, Consumer<TreeBuilder> branch) {
        TreeBuilder child = then(value);
        branch.accept(child);
        return this;
    }

    // 2. 동적 리스트 등록
    public TreeBuilder then(List<? extends ConsoleLabel> dynamicList) {
        registerRule(dynamicList);
        return createChildBuilder(dynamicList);
    }

    public TreeBuilder then(List<? extends ConsoleLabel> dynamicList, Consumer<TreeBuilder> branch) {
        TreeBuilder child = then(dynamicList);
        branch.accept(child);
        return this;
    }

    // 기존 API(suggestAt + whenToken.is) 호출 브리지
    private void registerRule(Object value) {
        AutoCompleteManager.CandidateBuilder builder;
        if (value instanceof String str) {
            builder = manager.suggestAt(depth, str);
        } else if (value instanceof List<?> list) {
            @SuppressWarnings("unchecked")
            List<? extends ConsoleLabel> labelList = (List<? extends ConsoleLabel>) list;
            builder = manager.suggestAt(depth, labelList);
        } else {
            return;
        }

        // 부모 경로의 조건들을 차례대로 whenToken(i).is(...)로 바인딩
        for (int i = 0; i < conditions.size(); i++) {
            Object cond = conditions.get(i);
            if (cond instanceof String strCond) {
                builder = builder.whenToken(i).is(strCond);
            } else if (cond instanceof List<?> listCond) {
                @SuppressWarnings("unchecked")
                List<? extends ConsoleLabel> labelCond = (List<? extends ConsoleLabel>) listCond;
                builder = builder.whenToken(i).is(labelCond);
            }
        }
    }

    public TreeBuilder then(String... values) {
        for (String val : values) {
            then(val);
        }
        return this;
    }

    public TreeBuilder then(Consumer<TreeBuilder> branch, String... values) {
        for (String val : values) {
            then(val, branch);
        }
        return this;
    }

    private TreeBuilder createChildBuilder(Object currentToken) {
        List<Object> nextConditions = new ArrayList<>(this.conditions);
        nextConditions.add(currentToken);
        return new TreeBuilder(manager, depth + 1, nextConditions);
    }
}