package com.codinglair.taf.core.reporting.impl.allure;

import com.codinglair.taf.core.environment.EnvironmentProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

public class ReportUtility {
    private static final Logger logger = LoggerFactory.getLogger(ReportUtility.class);

    public static void generateAllureReport(EnvironmentProperties props) {
        // 1. Get Absolute Paths
        File resultsDir = new File("target/allure-results").getAbsoluteFile();
        DateFormat df = new SimpleDateFormat("yyyyMMddHHmm");
        String reportTimeStamp = df.format(new Date());
        String reportDirPath=String.format("%s/%s",
                props.getEnvProperty("report_path"), reportTimeStamp);
        File reportDir = new File(reportDirPath).getAbsoluteFile();

        String customName = String.format("%s_%s.html",
                props.getEnvProperty("report_name"),
                reportTimeStamp);


        // 2. Build the command
        // On Windows, use "allure.bat" or "cmd /c allure" if "allure" fails
        String allureCmd = System.getProperty("os.name").toLowerCase().contains("win") ? "allure.bat" : "allure";

        ProcessBuilder pb = new ProcessBuilder(
                allureCmd, "generate",
                resultsDir.getAbsolutePath(),
                "--clean",
                "--single-file", // THE MAGIC FLAG
                "-o", reportDir.getAbsolutePath()
        );

        try {
            if (pb.inheritIO().start().waitFor() == 0) {
                // THE RENAME LOGIC
                File generatedFile = new File(reportDir, "index.html");
                File renamedFile = new File(reportDir, customName);

                if (generatedFile.exists()) {
                    if (renamedFile.exists()) renamedFile.delete(); // Clean up old version
                    boolean success = generatedFile.renameTo(renamedFile);

                    if (success) {
                        logger.info(String.format("✅ Report renamed to: %s", renamedFile.getAbsolutePath()));
                    }
                }
            }
        } catch (Exception e) {
            logger.error(e.getMessage());
        }
    }
}
