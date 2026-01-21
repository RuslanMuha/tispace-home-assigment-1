package com.tispace.dataingestion.application.validation;

import com.tispace.common.exception.BusinessException;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * Parses sort query parameter ("field,direction") with whitelist validation.
 * Rejects invalid fields/directions to prevent unsafe SQL.
 */
@Component
public class SortStringParser {

    private static final int MAX_SORT_STRING_LENGTH = 50;

    private static final String DEFAULT_SORT_FIELD = "publishedAt";
    private static final Sort.Direction DEFAULT_SORT_DIRECTION = Sort.Direction.DESC;

    private static final Set<String> VALID_SORT_FIELDS = Set.of(
            "id", "title", "description", "author", "publishedAt", "category", "createdAt", "updatedAt"
    );

    // Stable order for error message (Set order is not guaranteed)
    private static final String VALID_FIELDS_MESSAGE = VALID_SORT_FIELDS.stream()
            .sorted()
            .collect(Collectors.joining(", "));

    public Sort parse(String sortString) {
        if (sortString == null || sortString.isBlank()) {
            return defaultSort();
        }


        if (sortString.length() > MAX_SORT_STRING_LENGTH) {
            throw new BusinessException("Sort parameter cannot exceed " + MAX_SORT_STRING_LENGTH + " characters");
        }

        ParsedSort parsed = parseParts(sortString);
        String field = validateField(parsed.field());
        Sort.Direction direction = parseDirection(parsed.direction());

        return Sort.by(direction, field);
    }

    private ParsedSort parseParts(String sortString) {
        int commaIndex = sortString.indexOf(',');
        if (commaIndex <= 0 || commaIndex == sortString.length() - 1) {
            throw new BusinessException("Sort parameter must be in format 'field,direction' (e.g., 'publishedAt,desc')");
        }

        if (sortString.indexOf(',', commaIndex + 1) != -1) {
            throw new BusinessException("Sort parameter must contain exactly one comma: 'field,direction'");
        }

        String field = sortString.substring(0, commaIndex).trim();
        String direction = sortString.substring(commaIndex + 1).trim();

        if (field.isEmpty()) {
            throw new BusinessException("Sort field cannot be empty");
        }
        if (direction.isEmpty()) {
            throw new BusinessException("Sort direction cannot be empty");
        }

        return new ParsedSort(field, direction);
    }

    private String validateField(String field) {
        if (!VALID_SORT_FIELDS.contains(field)) {
            throw new BusinessException("Invalid sort field: " + field + ". Valid fields are: " + VALID_FIELDS_MESSAGE);
        }
        return field;
    }

    private Sort.Direction parseDirection(String direction) {
        return switch (direction.toLowerCase()) {
            case "asc" -> Sort.Direction.ASC;
            case "desc" -> Sort.Direction.DESC;
            default -> throw new BusinessException("Invalid sort direction: " + direction + ". Must be 'asc' or 'desc'");
        };
    }

    private Sort defaultSort() {
        return Sort.by(DEFAULT_SORT_DIRECTION, DEFAULT_SORT_FIELD);
    }

    private record ParsedSort(String field, String direction) {}
}

