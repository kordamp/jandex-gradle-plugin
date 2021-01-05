/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * Copyright 2019-2021 Andres Almiray.
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
import org.gradle.api.artifacts.Configuration
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.FileCollection
import org.gradle.api.file.RegularFile
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.options.Option
import org.gradle.process.JavaExecSpec
import org.kordamp.gradle.property.BooleanState
import org.kordamp.gradle.property.SimpleBooleanState
import org.kordamp.gradle.property.SimpleStringState
import org.kordamp.gradle.property.StringState

/**
 * @author Andres Almiray
 */
@CompileStatic
class JandexTask extends DefaultTask {
    private final BooleanState processDefaultFileSet
    private final BooleanState includeInJar
    private final StringState indexName

    @Classpath
    Configuration classpath

    @InputFiles
    final ConfigurableFileCollection sources

    @OutputFile
    final RegularFileProperty destination = project.objects.fileProperty()

    JandexTask() {
        processDefaultFileSet = SimpleBooleanState.of(this, 'jandex.process.default.file.set', true)
        includeInJar = SimpleBooleanState.of(this, 'jandex.include.in.jar', true)
        indexName = SimpleStringState.of(this, 'jandex.index.name', 'jandex.idx')
        sources = project.objects.fileCollection()

        destination.convention(indexName.provider.map(new Transformer<RegularFile, String>() {
            @Override
            RegularFile transform(String s) {
                project.layout.buildDirectory.file('jandex/' + s).get()
            }
        }))
    }

    @Option(option = 'jandex-process-default-file-set', description = "Include the 'main' source set. Defaults to true")
    void setProcessDefaultFileSet(boolean value) { processDefaultFileSet.property.set(value) }

    @Option(option = 'jandex-include-in-jar', description = "Include the generated index in the default JAR. Defaults to true")
    void setIncludeInJar(boolean value) { includeInJar.property.set(value) }

    @Option(option = 'jandex-index-name', description = "The name of the index file. Defaults to jandex.idx")
    void setIndexName(String value) { indexName.property.set(value) }

    @Internal
    Property<Boolean> getProcessDefaultFileSet() { processDefaultFileSet.property }

    @Internal
    Property<Boolean> getIncludeInJar() { includeInJar.property }

    @Input
    Provider<Boolean> getResolvedProcessDefaultFileSet() { processDefaultFileSet.provider }

    @Input
    Provider<Boolean> getResolvedIncludeInJar() { includeInJar.provider }

    @Internal
    Property<String> getIndexName() { indexName.property }

    @Input
    Provider<String> getResolvedIndexName() { indexName.provider }

    @TaskAction
    @CompileDynamic
    void generateIndex() {
        project.javaexec(new Action<JavaExecSpec>() {
            @Override
            void execute(JavaExecSpec jes) {
                List<String> args = []
                if (project.logger.infoEnabled ||
                    project.logger.debugEnabled ||
                    project.logger.traceEnabled) {
                    args << '-v'
                }
                args << '-o'
                args << destination.asFile.get().absolutePath
                args.addAll(resolveSources())

                jes.main = JandexMain.class.name
                jes.classpath(resolveClasspath())
                jes.args(args)
            }
        })
    }

    private FileCollection resolveClasspath() {
        project.files(
            new File(JandexMain.protectionDomain.codeSource.location.toURI()).absoluteFile,
            classpath)
    }

    private List<String> resolveSources() {
        List<String> files = []

        if (resolvedProcessDefaultFileSet.get()) {
            SourceSetContainer sourceSets = (SourceSetContainer) project.extensions.findByType(SourceSetContainer)
            files.addAll((Collection<String>) sourceSets.findByName('main').output.classesDirs*.absolutePath.flatten())
        }

        files.addAll((Collection<String>) sources.files*.absolutePath.flatten())

        files
    }
}
