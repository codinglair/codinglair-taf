package com.codinglair.taf.sauce.data;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.codinglair.taf.core.annotation.secret.Decrypt;
import com.codinglair.taf.core.data.abstraction.IdentifiableTestData;

public class WebUser implements IdentifiableTestData {
    @JsonProperty("TestCaseId")
    private String testCaseId;
    @JsonProperty("UserName")
    private String userName;
    @JsonProperty("Password")
    private String pwd;
    @JsonProperty("FirstName")
    private String firstName;
    @JsonProperty("LastName")
    private String lastName;
    @JsonProperty("ZipCode")
    private String zipCode;
    /*public WebUser(){
        String credsPath = AppConfigLoader.loadSubmoduleConfig("config/app.config").getProperty("AUTH_CREDS_PATH");
        Properties props = new Properties();
        String absolutePath=String.format(credsPath,
                System.getenv("USERPROFILE"));
        try (InputStream input = new FileInputStream(absolutePath)) {
            props.load(input);
            userName=props.getProperty("saucedemo_user");
            pwd=props.getProperty("saucedemo_pwd");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }*/

    public String getUserName() {
        return userName;
    }

    @Decrypt
    public String getPwd() {
        return pwd;
    }

    @Override
    public String getTestCaseId() {
        return testCaseId;
    }

    public void setTestCaseId(String testCaseId) {
        this.testCaseId = testCaseId;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public void setPwd(String pwd) {
        this.pwd = pwd;
    }

    public String getFirstName() {
        return firstName;
    }

 /*   public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getZipCode() {
        return zipCode;
    }

    public void setZipCode(String zipCode) {
        this.zipCode = zipCode;
    }*/
}
