package com.guardbench.testrun.presentation.controller;

import java.util.ArrayList;
import java.util.List;

import com.guardbench.common.presentation.dto.FieldErrorDetail;
import com.guardbench.testrun.application.port.out.SortDirection;
import com.guardbench.testrun.application.port.out.SortOrder;

/**
 * {@code {field},{asc|desc}} 형식의 반복 가능한 {@code sort} Query Parameter를 Application 조회
 * 조건의 {@link SortOrder}로 변환한다.
 *
 * <p>Spring의 {@code String}→{@code Collection} 변환은 단일 값을 쉼표로 분리하므로, 값 자체에 쉼표를
 * 포함하는 {@code sort}는 {@code String[]}로 받아 이 Parser가 직접 형식을 검증한다.
 *
 * @see <a href="../../../../../../../../docs/api/README.md">GuardBench API V1</a>
 */
final class SortParamParser {

    private SortParamParser() {
    }

    static <F extends Enum<F>> List<SortOrder<F>> parse(String[] rawSort, Class<F> fieldType) {
        if (rawSort == null || rawSort.length == 0 || (rawSort.length == 1 && rawSort[0].isEmpty())) {
            return List.of();
        }
        List<SortOrder<F>> orders = new ArrayList<>();
        List<FieldErrorDetail> errors = new ArrayList<>();
        for (int i = 0; i < rawSort.length; i++) {
            String raw = rawSort[i];
            String[] parts = raw.split(",", 2);
            if (parts.length != 2 || !isDirection(parts[1]) || !isKnownField(parts[0], fieldType)) {
                errors.add(new FieldErrorDetail("sort[" + i + "]", "허용되지 않은 정렬 조건입니다."));
                continue;
            }
            SortDirection direction = "desc".equalsIgnoreCase(parts[1]) ? SortDirection.DESC : SortDirection.ASC;
            orders.add(new SortOrder<>(Enum.valueOf(fieldType, toEnumName(parts[0])), direction));
        }
        if (!errors.isEmpty()) {
            throw new QueryParamValidationException(errors);
        }
        return orders;
    }

    private static boolean isDirection(String value) {
        return "asc".equalsIgnoreCase(value) || "desc".equalsIgnoreCase(value);
    }

    private static <F extends Enum<F>> boolean isKnownField(String camelCaseField, Class<F> fieldType) {
        if (camelCaseField.isEmpty()) {
            return false;
        }
        try {
            Enum.valueOf(fieldType, toEnumName(camelCaseField));
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static String toEnumName(String camelCaseField) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < camelCaseField.length(); i++) {
            char current = camelCaseField.charAt(i);
            if (Character.isUpperCase(current) && i > 0) {
                result.append('_');
            }
            result.append(Character.toUpperCase(current));
        }
        return result.toString();
    }
}

