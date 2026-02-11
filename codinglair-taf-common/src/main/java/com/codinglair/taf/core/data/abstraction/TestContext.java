package com.codinglair.taf.core.data.abstraction;

import com.codinglair.taf.core.data.dao.abstraction.DataProvider;
import com.codinglair.taf.core.environment.EnvironmentProperties;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public abstract class TestContext<I, O> {
    protected final DataProvider<String, I> inputsProvider;
    protected final DataProvider<String, O> expectedOutputsProvider;
    protected final Map<String, List<O>> actualTestOutputs;
    protected final EnvironmentProperties environmentProperties;
    public TestContext(EnvironmentProperties environmentProperties,
                       DataProvider<String, I> inputsProvider,
                       DataProvider<String, O> expectedOutputsProvider){
        this.inputsProvider = inputsProvider;
        this.expectedOutputsProvider = expectedOutputsProvider;
        this.environmentProperties=environmentProperties;
        actualTestOutputs = new ConcurrentHashMap<>();
    }

    /**
     * It returns the list of inputs in case if a test scenario requires more
     * than one input.
     *
     * @param testCaseId
     * @return
     */
    public List<I> getTestInputs(String testCaseId){
        return inputsProvider.readAll(testCaseId);
    }

    /**
     * If a testcase requires more than one input and every input needs to be
     * additionally identified, use this method and a unique identifier in the
     * payload in order to retrieve a correct input.
     * @param testCaseId
     * @param correlationId
     * @return
     */
    public I getTestInput(String testCaseId, String correlationId) {
        return inputsProvider.read(testCaseId, correlationId);
    }

    /**
     * Use this method if there's only one test input per test case.
     * @param testCaseId
     * @return
     */
    public I getTestInput(String testCaseId) {
        return inputsProvider.read(testCaseId);
    }

    /**
     * It returns the list of expected outputs in case if a test scenario requires more
     * than one input.
     *
     * @param testCaseId
     * @return
     */
    public List<O>  getExpectedTestOutputs(String testCaseId){
        return expectedOutputsProvider.readAll(testCaseId);
    }

    public O getExpectedTestOutput(String testCaseId, String correlationId){
        return expectedOutputsProvider.read(testCaseId, correlationId);
    }

    public O getExpectedTestOutput(String testCaseId) {
        return expectedOutputsProvider.read(testCaseId);
    }

    public void recordActual(String testCaseId, O result) {
        actualTestOutputs.computeIfAbsent(testCaseId, k -> new CopyOnWriteArrayList<>())
                .add(result);
    }

    public List<O>  getActualTestOutputs(String testCaseId){
        return actualTestOutputs.get(testCaseId);
    }

    public O getActualTestOutput(String testCaseId, String correlationId){
        return getActualTestOutputs(testCaseId).stream()
                .filter(Objects::nonNull)
                .filter(obj -> obj.toString().contains(correlationId))
                .findFirst()
                .orElseThrow(() -> new NoSuchElementException(
                        "Check the data. No expected output found matching: " + correlationId));
    }

    public O getLastActualTestOutput(String testCaseId) {
        return getActualTestOutputs(testCaseId).getLast();
    }

    public EnvironmentProperties getEnvironmentProperties() {
        return environmentProperties;
    }
}
