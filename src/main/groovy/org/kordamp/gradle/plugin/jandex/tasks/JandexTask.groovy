/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * Copyright 2019-2025 Andres Almiray.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.kordamp.gradle.plugin.jandex.tasks

import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import org.gradle.api.Action
import org.gradle.api.DefaultTask
import org.gradle.api.Transformer
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.ProjectLayout
import org.gradle.api.file.RegularFile
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.logging.Logging
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.options.Option
import org.gradle.workers.ClassLoaderWorkerSpec
import org.gradle.workers.WorkQueue
import org.gradle.workers.WorkerExecutor

import javax.inject.Inject

/**
 * @author Andres Almiray
 */
@CompileStatic
class JandexTask extends DefaultTask {
    private final Property<Boolean> processDefaultFileSet
    private final Property<Boolean> includeInJar
    private final Property<String> indexName
    private final Property<Integer> indexVersion
    private final WorkerExecutor workerExecutor

    @Classpath
    final ConfigurableFileCollection classpathFiles

    @InputFiles
    final ConfigurableFileCollection sources

    @InputFiles
    final ConfigurableFileCollection mainClassesDirs

    @OutputFile
    final RegularFileProperty destination

    @Internal
    final DirectoryProperty processResourcesDir

    @Internal
    final Property<ProjectLayout> layout

    private final ProviderFactory providerFactory

    @Inject
    JandexTask(WorkerExecutor workerExecutor, ObjectFactory objects, ProviderFactory providerFactory) {
        // Use proper logging instead of System.out.println
        def logger = Logging.getLogger(JandexTask)
        logger.debug("JandexTask constructor called from " + this.getClass().getProtectionDomain().getCodeSource().getLocation())

        layout = objects.property(ProjectLayout)

        this.workerExecutor = workerExecutor
        this.providerFactory = providerFactory

        // Create properties with support for command line flags, env vars, and system properties
        processDefaultFileSet = objects.property(Boolean)
        // Set default value
        processDefaultFileSet.convention(true)
        
        includeInJar = objects.property(Boolean)
        // Set default value
        includeInJar.convention(true)
        
        indexName = objects.property(String)
        // Set default value
        indexName.convention('jandex.idx')
        
        indexVersion = objects.property(Integer)
        // No default value for indexVersion

        sources = objects.fileCollection()
        mainClassesDirs = objects.fileCollection()
        classpathFiles = objects.fileCollection()
        processResourcesDir = objects.directoryProperty()
        destination = objects.fileProperty()

        destination.convention(indexName.flatMap(new Transformer<Provider<RegularFile>, String>() {
            @Override
            Provider<RegularFile> transform(String s) {
                return includeInJar.flatMap(new Transformer<Provider<RegularFile>, Boolean>() {
                    @Override
                    Provider<RegularFile> transform(Boolean includeInJar) {
                        if (includeInJar) {
                            return processResourcesDir.file("META-INF/jandex.idx")
                        }
                        return layout.flatMap(new Transformer<Provider<RegularFile>, ProjectLayout>() {
                            @Override
                            Provider<RegularFile> transform(ProjectLayout l) {
                                return l.getBuildDirectory().file('jandex/' + s)
                            }
                        })
                    }
                })
            }
        }))
    }

    @Option(option = 'jandex-process-default-file-set', description = "Include the 'main' source set. Defaults to true")
    void setProcessDefaultFileSet(boolean value) { 
        // Command line options have highest precedence
        processDefaultFileSet.set(value) 
    }

    @Option(option = 'jandex-include-in-jar', description = "Include the generated index in the default JAR. Defaults to true")
    void setIncludeInJar(boolean value) { 
        // Command line options have highest precedence
        includeInJar.set(value) 
    }

    @Option(option = 'jandex-index-name', description = "The name of the index file. Defaults to jandex.idx")
    void setIndexName(String value) { 
        // Command line options have highest precedence
        indexName.set(value) 
    }

    @Option(option = 'jandex-index-version', description = "The version of the index file. Defaults to the latest version supported by the invoked Jandex version")
    void setIndexVersion(Integer value) { 
        // Command line options have highest precedence
        indexVersion.set(value) 
    }
    
    // Add methods to check system properties and environment variables
    // These will be called during task execution, not configuration
    
    private boolean getProcessDefaultFileSetValue() {
        // Check command line option first (already set via setProcessDefaultFileSet)
        if (processDefaultFileSet.isPresent()) {
            return processDefaultFileSet.get()
        }
        
        // Check environment variable
        String envValue = System.getenv("JANDEX_PROCESS_DEFAULT_FILE_SET")
        if (envValue != null) {
            return Boolean.parseBoolean(envValue)
        }
        
        // Check system property
        String sysPropValue = System.getProperty("jandex.process.default.file.set")
        if (sysPropValue != null) {
            return Boolean.parseBoolean(sysPropValue)
        }
        
        // Fall back to default
        return true
    }
    
    private boolean getIncludeInJarValue() {
        // Check command line option first (already set via setIncludeInJar)
        if (includeInJar.isPresent()) {
            return includeInJar.get()
        }
        
        // Check environment variable
        String envValue = System.getenv("JANDEX_INCLUDE_IN_JAR")
        if (envValue != null) {
            return Boolean.parseBoolean(envValue)
        }
        
        // Check system property
        String sysPropValue = System.getProperty("jandex.include.in.jar")
        if (sysPropValue != null) {
            return Boolean.parseBoolean(sysPropValue)
        }
        
        // Fall back to default
        return true
    }
    
    private String getIndexNameValue() {
        // Check command line option first (already set via setIndexName)
        if (indexName.isPresent()) {
            return indexName.get()
        }
        
        // Check environment variable
        String envValue = System.getenv("JANDEX_INDEX_NAME")
        if (envValue != null) {
            return envValue
        }
        
        // Check system property
        String sysPropValue = System.getProperty("jandex.index.name")
        if (sysPropValue != null) {
            return sysPropValue
        }
        
        // Fall back to default
        return 'jandex.idx'
    }
    
    private Integer getIndexVersionValue() {
        // Check command line option first (already set via setIndexVersion)
        if (indexVersion.isPresent()) {
            return indexVersion.get()
        }
        
        // Check environment variable
        String envValue = System.getenv("JANDEX_INDEX_VERSION")
        if (envValue != null) {
            try {
                return Integer.parseInt(envValue)
            } catch (NumberFormatException ignored) {
                // Ignore invalid values
            }
        }
        
        // Check system property
        String sysPropValue = System.getProperty("jandex.index.version")
        if (sysPropValue != null) {
            try {
                return Integer.parseInt(sysPropValue)
            } catch (NumberFormatException ignored) {
                // Ignore invalid values
            }
        }
        
        // No default for indexVersion
        return null
    }

    @Internal
    Property<Boolean> getProcessDefaultFileSet() { processDefaultFileSet }

    @Internal
    Property<Boolean> getIncludeInJar() { includeInJar }

    @Input
    Provider<Boolean> getResolvedProcessDefaultFileSet() { 
        return providerFactory.provider({ getProcessDefaultFileSetValue() })
    }

    @Input
    Provider<Boolean> getResolvedIncludeInJar() { 
        return providerFactory.provider({ getIncludeInJarValue() })
    }

    @Internal
    Property<String> getIndexName() { indexName }

    @Input
    @Optional
    Property<Integer> getIndexVersion() { 
        return indexVersion
    }

    @Input
    Provider<String> getResolvedIndexName() { 
        return providerFactory.provider({ getIndexNameValue() })
    }

    @TaskAction
    @CompileDynamic
    void generateIndex() {
        WorkQueue workQueue = workerExecutor.classLoaderIsolation(new Action<ClassLoaderWorkerSpec>() {
            @Override
            void execute(ClassLoaderWorkerSpec classLoaderWorkerSpec) {
                classLoaderWorkerSpec.classpath.from(classpathFiles)
            }
        })

        workQueue.submit(JandexWorkAction, new Action<JandexWorkParameters>() {
            @Override
            void execute(JandexWorkParameters parameters) {
                parameters.sources.set(resolveSources())
                parameters.destination.set(destination)
                
                // Use the method that checks system properties and environment variables
                Integer version = getIndexVersionValue()
                if (version != null) {
                    parameters.indexVersion.set(version)
                }
            }
        })
    }

    private List<String> resolveSources() {
        List<String> files = []

        if (resolvedProcessDefaultFileSet) {
            files.addAll((Collection<String>) mainClassesDirs.files*.absolutePath.flatten())
        }

        files.addAll((Collection<String>) sources.files*.absolutePath.flatten())

        files
    }
}
