package com.codinglair.taf.core;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Properties;

public class AppConfigLoader {
    public static Properties loadSubmoduleConfig(String fileName) {
        Properties props = new Properties();
        // Uses the ClassLoader of the current thread (the submodule's context)
        try (InputStream is = Thread.currentThread().getContextClassLoader().getResourceAsStream(fileName)) {
            if (is == null) throw new FileNotFoundException("Could not find " + fileName + " in submodule resources");
            props.load(is);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return props;
    }
}
