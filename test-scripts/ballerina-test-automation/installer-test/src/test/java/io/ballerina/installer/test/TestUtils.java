/*
 * Copyright (c) 2020, WSO2 Inc. (http://wso2.com) All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.ballerina.installer.test;

import io.ballerina.test.*;
import org.testng.Assert;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;

public class TestUtils {
    private static final String OS = System.getProperty("os.name").toLowerCase(Locale.getDefault());
    private static final String SWAN_LAKE_KEYWORD = "swan-lake";
    private static final String VERSION_DISPLAY_TEXT = System.getProperty("VERSION_DISPLAY_TEXT");

    /**
     * Get version output for version command.
     *  @param jBallerinaVersion Installed jBallerina version
     *  @param specVersion Installed language specification
     *  @param toolVersion Installed tool version
     *  @param versionDisplayText display text for installed jBallerina version
     *
     * @return version output
     */
    public static String getVersionOutput(String jBallerinaVersion, String specVersion, String toolVersion,
                                          String versionDisplayText) {
        String toolText = TestUtils.isOldToolVersion(toolVersion) ? "Ballerina tool" : "Update Tool";
        if (jBallerinaVersion.contains(TestUtils.SWAN_LAKE_KEYWORD)) {
            return "Ballerina Swan Lake " + versionDisplayText + "\n" + "Language specification " + specVersion +
                    "\n" + toolText + " " + toolVersion + "\n";
        }

        String ballerinaReference = isSupportedRelease(jBallerinaVersion) ? "jBallerina" : "Ballerina";
        return ballerinaReference + " " + jBallerinaVersion + "\n" + "Language specification " + specVersion + "\n" +
                toolText + " " + toolVersion + "\n";
    }

    public static Executor getExecutor(String version) {
        Executor executor;
        if (OS.contains("win")) {
            executor = new Windows(version);
        } else if (OS.contains("mac")) {
            executor = new MacOS(version);
        } else {
            String provider = System.getenv("OS_TYPE");
            if (provider != null && provider.equalsIgnoreCase("centos")) {
                executor = new CentOS(version);
            } else {
                executor = new Ubuntu(version);
            }
        }
        return executor;
    }

    public static void testDistCommands(Executor executor, String version, String specVersion, String toolVersion,
                                        String previousVersion, String previousSpecVersion,
                                        String previousVersionsLatestPatch, String latestToolVersion) {
        //Test installation
        TestUtils.testInstallation(executor, version, specVersion, toolVersion, VERSION_DISPLAY_TEXT);

        //Test `ballerina dist list`
        String actualOutput = executor.executeCommand("dist list", false, toolVersion);
        Assert.assertTrue(actualOutput.contains("1.0.0"));
        Assert.assertTrue(actualOutput.contains("1.1.0"));
        Assert.assertTrue(actualOutput.contains("1.2.0"));
        Assert.assertTrue(actualOutput.contains("slp1"));

        //Test `ballerina dist pull`
        executor.executeCommand("dist pull "
                + TestUtils.getSupportedVersion(toolVersion, previousVersion), true, toolVersion);

        TestUtils.testInstallation(executor, previousVersion, previousSpecVersion, toolVersion, previousVersion);

        //Test Update notification message
        if (isSupportedRelease(previousVersion)) {
            //TODO : This is a bug and have fixed in the update tool. Need to update here once new version is released.
            String expectedOutput = "A new version of Ballerina is available: jballerina-" + previousVersionsLatestPatch
                    + "\nUse 'ballerina dist pull jballerina-" + previousVersionsLatestPatch
                    + "' to download and use the distribution\n\n";
            //  Assert.assertEquals(executor.executeCommand("ballerina build help", false), expectedOutput);
        }

        //Test `ballerina dist use`
        executor.executeCommand("dist use " + TestUtils.getSupportedVersion(toolVersion, version), true,
                toolVersion);

        //Verify the the installation
        TestUtils.testInstallation(executor, version, specVersion, toolVersion, VERSION_DISPLAY_TEXT);

        //Test `ballerina dist update`
        executor.executeCommand("dist use " + TestUtils.getSupportedVersion(toolVersion, previousVersion),
                true, toolVersion);
        executor.executeCommand("dist remove " + TestUtils.getSupportedVersion(toolVersion, version), true,
                toolVersion);


        //TODO: Temporary attempt
        executor.executeCommand("update", true, toolVersion);

        executor.executeCommand("dist update", true, latestToolVersion);
        TestUtils.testInstallation(executor, previousVersionsLatestPatch, previousSpecVersion, latestToolVersion,
                previousVersionsLatestPatch);

        //Try `ballerina dist remove`
        executor.executeCommand("dist remove " + TestUtils.getSupportedVersion(toolVersion, previousVersion),
                true, latestToolVersion);
    }

    /**
     * Execute smoke testing to verify installation.
     *
     * @param executor    Executor for relevant operating system
     * @param version     Installed jBallerina version
     * @param specVersion Installed language specification
     * @param toolVersion Installed tool version
     */
    public static void testInstallation(Executor executor, String version, String specVersion, String toolVersion,
                                        String versionDisplayText) {
        Assert.assertEquals(executor.executeCommand("-v", false, toolVersion),
                TestUtils.getVersionOutput(version, specVersion, toolVersion, versionDisplayText));
    }

    /**
     * Execute smoke testing to verify fetching dependencies.
     *
     * @param executor    Executor for relevant operating system
     * @param toolVersion Installed tool version
     */
    public static void testDependencyFetch(Executor executor, String toolVersion) {
        executor.executeCommand("dist list", false, toolVersion);
        //Test `Fetching compatible JRE dependency`
        String output = executor.executeCommand("dist pull slp1", true, toolVersion);
        Assert.assertTrue(output.contains("Downloading slp1"));
        Assert.assertTrue(output.contains("Fetching the dependencies for 'slp1' from the remote server..."));
        Assert.assertTrue(output.contains("jdk8u202-b08-jre"));
        Assert.assertTrue(output.contains("'slp1' successfully set as the active distribution"));
        TestUtils.testInstallation(executor, "swan-lake-preview1", "v2020-06-18", toolVersion, "Preview 1");
        String cmdName = Utils.getCommandName(toolVersion);
        Path userDir = Paths.get(System.getProperty("user.dir"));
        executor.executeCommand("new project1 && cd project1 && " + cmdName + "add module1 && " +
                cmdName + "build module1", false, toolVersion);
        Path projectPath = userDir.resolve("project1");
        Assert.assertTrue(Files.isDirectory(projectPath));
        Assert.assertTrue(Files.isDirectory(projectPath.resolve("src").resolve("module1")));
        Assert.assertTrue(Files.exists(projectPath.resolve("target/bin/module1.jar")));

        output = executor.executeCommand("dist pull slp7", true, toolVersion);
        Assert.assertTrue(output.contains("Downloading slp7"));
        Assert.assertTrue(output.contains("Fetching the dependencies for 'slp7' from the remote server..."));
        Assert.assertTrue(output.contains("Downloading jdk-11.0.8+10-jre"));
        Assert.assertTrue(output.contains("'slp7' successfully set as the active distribution"));
        TestUtils.testInstallation(executor, "swan-lake-preview7", "v2020-09-22", toolVersion, "Preview 7");

        executor.executeCommand("new project2 && cd project2 &&" + cmdName +  "add module1 && " +
                cmdName + "build", false, toolVersion);
        projectPath = userDir.resolve("project2");
        Assert.assertTrue(Files.isDirectory(projectPath));
        Assert.assertTrue(Files.isDirectory(projectPath.resolve("modules").resolve("module1")));
        Assert.assertTrue(Files.exists(projectPath.resolve("target/bin/project2.jar")));
    }

    /**
     * Execute smoke testing to verify dist list.
     *
     * @param executor    Executor for relevant operating system
     */
    public static void verifyDistList(Executor executor, String toolVersion) {
        String actualOutput = executor.executeCommand("dist list", false, toolVersion);
        Assert.assertTrue(actualOutput.contains("1.0.0"));
        Assert.assertTrue(actualOutput.contains("1.1.0"));
        Assert.assertTrue(actualOutput.contains("1.2.0"));
        Assert.assertTrue(actualOutput.contains("slp1"));
    }

    /**
     * To check whether installation is a 1.0.x release.
     *
     * @return returns is a 1.0.x release
     */
    public static boolean isSupportedRelease(String version) {
        if (version.contains(TestUtils.SWAN_LAKE_KEYWORD)) {
            return true;
        }

        String[] versions = version.split("\\.");
        return !(versions[0].equals("1") && versions[1].equals("0"));
    }

    /**
     * To check whether older tool version before swan lake support
     *
     * @param toolVersion
     * @return returns is a older version
     */
    public static boolean isOldToolVersion(String toolVersion) {
        return toolVersion.equals("0.8.5") || toolVersion.equals("0.8.0");
    }

    /**
     * Test project and module creation.
     *
     * @param executor Executor for relevant operating system
     * @param previousVersion Ballerina version to be installed
     * @param previousSpecVersion Installed language specification
     * @param toolVersion Installed tool version
     */
    public static void testProject(Executor executor, String previousVersion, String previousSpecVersion, String toolVersion) {
        String cmdName = Utils.getCommandName(toolVersion);
        executor.executeCommand("new sampleProject1 && cd sampleProject1 && " + cmdName + "add module1 && " +
                        cmdName + "build", false, toolVersion);
        Path userDir = Paths.get(System.getProperty("user.dir"));
        Path projectPath = userDir.resolve("sampleProject1");
        Assert.assertTrue(Files.exists(projectPath));
        Assert.assertTrue(Files.isDirectory(projectPath.resolve("modules").resolve("module1")));
        Assert.assertTrue(Files.exists(projectPath.resolve("target/bin/sampleProject1.jar")));

        executor.executeCommand("dist pull " + previousVersion, true, toolVersion);
        testInstallation(executor, previousVersion, previousSpecVersion, toolVersion, previousVersion);
        executor.executeCommand("new sampleProject2 && cd sampleProject2 && " + cmdName + "add module1 && " +
                        cmdName + "build module1", false, toolVersion);
        projectPath = userDir.resolve("sampleProject2");
        Assert.assertTrue(Files.exists(projectPath));
        Assert.assertTrue(Files.isDirectory(projectPath.resolve("src").resolve("module1")));
        Assert.assertTrue(Files.exists(projectPath.resolve("target/bin/module1.jar")));
    }

    private static String getSupportedVersion(String toolVersion, String version) {
        if (TestUtils.isOldToolVersion(toolVersion)) {
            return "jballerina-" + version;
        }
        if (version.contains(TestUtils.SWAN_LAKE_KEYWORD)) {
            if (version.contains("alpha") || version.contains("beta")) {
                return "sl" + version.split("-")[2];
            }
            return "slp" + version.replace("swan-lake-preview", "");
        }
        return version;
    }
}
