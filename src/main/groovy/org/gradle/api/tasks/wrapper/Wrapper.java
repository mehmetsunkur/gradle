/*
 * Copyright 2007 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.api.tasks.wrapper;

import org.gradle.api.*;
import org.gradle.api.internal.DefaultTask;
import org.gradle.util.GradleVersion;
import org.gradle.util.CompressUtil;
import org.gradle.util.GradleUtil;
import org.gradle.wrapper.Install;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.io.FileOutputStream;
import java.util.Properties;

/**
 * The wrapper task generates scripts (for *nix and windows) which enable to build your project with Gradle, without having to install Gradle.
 * The scripts generated by this task are supposed to be commited to your version control system. This tasks also copies
 * a gradle-wrapper.jar to your project dir which needs also be commited into your VCS.
 * The scripts delegates to this jar. If a user execute a wrapper script the first time, the script downloads the gradle-distribution and
 * runs the build agisnt the downloaded distribution. Any installed Gradle distribution is ignored when using the wrapper scripts. 
 *
 * @author Hans Dockter
 */
public class Wrapper extends DefaultTask {
    
    public static final String DEFAULT_URL_ROOT = "http://dist.codehaus.org/gradle";
    public static final String WRAPPER_JAR_BASE_NAME = "gradle-wrapper";
    public static final String DEFAULT_DISTRIBUTION_PARENT_NAME = "wrapper/dists";
    public static final String DEFAULT_DISTRIBUTION_NAME = "gradle-bin";

    public enum PathBase { PROJECT, GRADLE_USER_HOME }

    private String scriptDestinationPath;

    private String jarPath;

    private String distributionPath;

    private String distributionName;

    private PathBase distributionBase = PathBase.GRADLE_USER_HOME;

    private String gradleVersion;

    private String urlRoot;

    private String zipPath;

    private PathBase zipBase = PathBase.GRADLE_USER_HOME;

    private UnixWrapperScriptGenerator unixWrapperScriptGenerator = new UnixWrapperScriptGenerator();

    public Wrapper(Project project, String name) {
        super(project, name);
        doFirst(new TaskAction() {
            public void execute(Task task) {
                generate(task);
            }
        });
        scriptDestinationPath = "";
        jarPath = "";
        distributionPath = DEFAULT_DISTRIBUTION_PARENT_NAME;
        distributionName = DEFAULT_DISTRIBUTION_NAME;
        zipPath = DEFAULT_DISTRIBUTION_PARENT_NAME;
        urlRoot = DEFAULT_URL_ROOT;
    }

    private void generate(Task task) {
        if (scriptDestinationPath == null) {
            throw new InvalidUserDataException("The scriptDestinationPath property must be specified!");
        }
        File jarFileDestination = new File(getProject().getProjectDir(), getJarPath() + "/" + Install.WRAPPER_JAR);

        File jarFileSource = new File(System.getProperty("gradle.home") + "/lib",
                WRAPPER_JAR_BASE_NAME + "-" + new GradleVersion().getVersion() + ".jar");
        File tmpExplodedSourceJar = GradleUtil.makeNewDir(new File(getProject().getBuildDir(), "wrapperJar"));
        CompressUtil.unzip(jarFileSource, tmpExplodedSourceJar);
        File propFile = new File(tmpExplodedSourceJar.getAbsolutePath() + "/org/gradle/wrapper/wrapper.properties");
        propFile.getParentFile().mkdirs();
        Properties wrapperProperties = new Properties();
        wrapperProperties.put(org.gradle.wrapper.Wrapper.URL_ROOT_PROPERTY, urlRoot);
        wrapperProperties.put(org.gradle.wrapper.Wrapper.DISTRIBUTION_BASE_PROPERTY, distributionBase.toString());
        wrapperProperties.put(org.gradle.wrapper.Wrapper.DISTRIBUTION_PATH_PROPERTY, distributionPath);
        wrapperProperties.put(org.gradle.wrapper.Wrapper.DISTRIBUTION_NAME_PROPERTY, distributionName);
        wrapperProperties.put(org.gradle.wrapper.Wrapper.DISTRIBUTION_VERSION_PROPERTY, gradleVersion);
        wrapperProperties.put(org.gradle.wrapper.Wrapper.ZIP_STORE_BASE_PROPERTY, zipBase.toString());
        wrapperProperties.put(org.gradle.wrapper.Wrapper.ZIP_STORE_PATH_PROPERTY, zipPath);
        try {
            wrapperProperties.store(new FileOutputStream(propFile), "");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        CompressUtil.zip(tmpExplodedSourceJar, jarFileDestination);

        unixWrapperScriptGenerator.generate(
                jarPath,
                new File(getProject().getProjectDir(), scriptDestinationPath));
    }

    public String getScriptDestinationPath() {
        return scriptDestinationPath.toString();
    }

    /**
     * A path specifying a directory relative to the project dir. This path is the parent dir of the scripts which are generated when
     * executing the wrapper task. Defaults to empty string, i.e. the project dir is the parent dir for the scripts then.
     *
     * @param scriptDestinationPath Any object which <code>toString</code> method specifies the path. Most likely a String of File object.
     */
    public void setScriptDestinationPath(String scriptDestinationPath) {
        this.scriptDestinationPath = scriptDestinationPath;
    }

    public String getJarPath() {
        return jarPath.toString();
    }

    /**
     * When executing the wrapper task, the jar path specifies the path where the gradle-wrapper.jar is copied to. The
     * jar path must be a path relative to the project dir. The gradle-wrapper.jar must be submitted to your version
     * control system.
     *
     * @param jarPath
     */
    public void setJarPath(String jarPath) {
        this.jarPath = jarPath;
    }

    public String getDistributionPath() {
        return distributionPath.toString();
    }

    /**
     * Set's the path where the gradle distributions needed by the wrapper are unzipped. The path is relative to the
     * {@link #distributionBase}.
     *
     * @param distributionPath  
     */
    public void setDistributionPath(String distributionPath) {
        this.distributionPath = distributionPath;
    }

    public String getGradleVersion() {
        return gradleVersion;
    }

    /**
     * The version of the gradle distribution needed to build the project.
     *
     * @param gradleVersion
     */
    public void setGradleVersion(String gradleVersion) {
        this.gradleVersion = gradleVersion;
    }

    public String getUrlRoot() {
        return urlRoot;
    }

    /**
     * A URL where to download the gradle distribution. The pattern used by the wrapper for downloading is:
     * <code><i>{@link #getUrlRoot()}</i>/gradle-bin-<i>{@link #getGradleVersion()}</i>.zip</code>. The default is
     * {@link #DEFAULT_URL_ROOT}.
     *
     * The wrapper downloads a certain distribution only ones and caches it.
     * If your {@link #getDistributionBase()} is the project, you might check in the distribution into your version control system.
     * That way no download is necessary at all. This might be in particular interesting, if you provide a custom gradle snapshot to the wrapper,
     * because you don't need to provide a download server then.
     *  
     * @param urlRoot
     */
    public void setUrlRoot(String urlRoot) {
        this.urlRoot = urlRoot;
    }

    public PathBase getDistributionBase() {
        return distributionBase;
    }

    /**
     * The distribution base is either the project or the gradle user home dir. The path specified in {@link #distributionPath}
     * is a relative path to the distribution base.  
     *
     * @param distributionBase
     */
    public void setDistributionBase(PathBase distributionBase) {
        this.distributionBase = distributionBase;
    }

    public String getZipPath() {
        return zipPath;
    }

    /**
     * Set's the path where the gradle distributions archive should be saved (i.e. the parent dir). The path is relative to the
     * {@link #getZipBase()}.
     *
     * @param zipPath
     */
    public void setZipPath(String zipPath) {
        this.zipPath = zipPath;
    }

    public PathBase getZipBase() {
        return zipBase;
    }

    /**
     * The distribution base is either the project or the gradle user home dir. The path specified in {@link #getZipPath()}
     * is a relative path to the zip base.
     *
     * @param zipBase
     */
    public void setZipBase(PathBase zipBase) {
        this.zipBase = zipBase;
    }

    public String getDistributionName() {
        return distributionName;
    }

    public void setDistributionName(String distributionName) {
        this.distributionName = distributionName;
    }
}
