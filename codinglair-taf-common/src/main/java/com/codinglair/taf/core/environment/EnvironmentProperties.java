package com.codinglair.taf.core.environment;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;

public class EnvironmentProperties {
    private Map<String, String> envProps;

    public EnvironmentProperties(String pathToEnvProps) {
        loadEnvProperties(pathToEnvProps);
    }

    private void loadEnvProperties(String pathToEnvProps){
        Properties props = new Properties();
        try (InputStream input = new FileInputStream(pathToEnvProps)) {
            props.load(input);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        envProps = props.entrySet().stream()
                .collect(Collectors.toMap(
                        e -> e.getKey().toString(),
                        e -> e.getValue().toString()
                ));
    }

    public Map<String, String> getEnvPropsMap() {
        return envProps;
    }

    public String getEnvProperty(String propertyKey) {
        return envProps.get(propertyKey);
    }

    public void putEnvProperty(String key, String value) {
        envProps.put(key, value);
    }
}
