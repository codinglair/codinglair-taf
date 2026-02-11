package com.codinglair.taf.core.reporting.impl.allure;

import com.codinglair.taf.core.environment.EnvironmentProperties;

import java.io.File;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

public class ReportUtility {

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
                        System.out.println("✅ Report renamed to: " + renamedFile.getAbsolutePath());
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

/*        try {
            System.out.println("Generating report from: " + resultsDir.getAbsolutePath());
            Process process = pb.inheritIO().start();
            int exitCode = process.waitFor();

            if (exitCode == 0) {
                System.out.println("✅ Report ready: " + reportDir.getAbsolutePath() + "/index.html");
            } else {
                System.err.println("❌ Allure CLI exited with code: " + exitCode);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }*/
    }
