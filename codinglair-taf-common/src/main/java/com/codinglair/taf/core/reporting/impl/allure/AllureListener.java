package com.codinglair.taf.core.reporting.impl.allure;

import com.codinglair.taf.core.controller.abstraction.TestController;
import com.codinglair.taf.core.test.abstraction.BaseTest;
import org.testng.ITestListener;
import org.testng.ITestResult;

import java.io.ByteArrayInputStream;

public class AllureListener implements ITestListener {

    @Override
    public void onTestFailure(ITestResult result) {
        Object testInstance = result.getInstance();

        if (testInstance instanceof BaseTest<?, ?, ?> base) {
            TestController controller = base.getController();
            byte[] data = controller.getFailureAttachment();

            if (data != null) {
                // Allure's manual attachment API is better for dynamic types
                // than the @Attachment annotation
                io.qameta.allure.Allure.addAttachment(
                        "Failure Evidence",
                        controller.getAttachmentType(),
                        new ByteArrayInputStream(data),
                        controller.getAttachmentExtension()
                );
            }
        }
    }
}