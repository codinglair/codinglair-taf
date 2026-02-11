package com.codinglair.taf.core.validation.abstraction;

/**
 * Interface for validators
 * @param <T> - Type of objects to compare
 */
public interface Validator <T> {
    void validate(T expected, T actual);
}