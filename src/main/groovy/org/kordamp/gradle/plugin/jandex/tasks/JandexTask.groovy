/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * Copyright 2019 Andres Almiray.
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
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.Transformer
import org.gradle.api.file.ConfigurableFileTree
import org.gradle.api.file.FileTree
import org.gradle.api.file.FileVisitDetails
import org.gradle.api.file.FileVisitor
import org.gradle.api.file.RegularFile
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.TaskAction
import org.jboss.jandex.ClassInfo
import org.jboss.jandex.Index
import org.jboss.jandex.IndexWriter
import org.jboss.jandex.Indexer

/**
 * @author Andres Almiray
 */
@CompileStatic
class JandexTask extends DefaultTask {
    @Input
    final Property<Boolean> processDefaultFileSet = project.objects.property(Boolean)
    @Input
    final Property<String> indexName = project.objects.property(String).convention('jandex.idx')
    @OutputFile
    final RegularFileProperty destination = project.objects.fileProperty()
    private ConfigurableFileTree fileSets

    JandexTask() {
        this.processDefaultFileSet.set(true)
        destination.convention(indexName.map(new Transformer<RegularFile, String>() {
            @Override
            RegularFile transform(String s) {
                project.layout.buildDirectory.file('jandex/META-INF/' + s).get()
            }
        }))
    }

    @InputFiles
    @CompileDynamic
    FileTree getFileSets() {
        FileTree files = null
        if (processDefaultFileSet.get()) {
            SourceSetContainer sourceSets = (SourceSetContainer) project.extensions.findByType(SourceSetContainer)
            List<FileTree> fileTrees = sourceSets.findByName('main').output.files.collect({ File dir ->
                project.fileTree(dir: dir, include: '**/*.class')
            })
            files = fileTrees[1..-1].inject(fileTrees[0]) { a, b -> a + b }
        }
        if (this.@fileSets) {
            files = files ? files + this.@fileSets : this.@fileSets
        }
        files
    }

    void setFileSets(FileTree fileSets) {
        this.@fileSets = project.fileTree(fileSets)
    }

    @TaskAction
    void generateIndex() {
        createIndex(project, getFileSets(), destination.get())
    }

    static final createIndex(Project project, FileTree fileSets, RegularFile destination) {
        Indexer indexer = new Indexer()
        fileSets.visit(new FileVisitor() {
            @Override
            void visitDir(FileVisitDetails dir) {
                // ignore
            }

            @Override
            void visitFile(FileVisitDetails file) {
                indexFile(project, indexer, file.file)
            }
        })

        File idx = destination.asFile
        idx.parentFile.mkdirs()

        FileOutputStream out = null
        try {
            out = new FileOutputStream(idx)
            IndexWriter writer = new IndexWriter(out)
            Index index = indexer.complete()
            writer.write(index)
            println("Index has been written to ${idx.absolutePath}")
        } catch (IOException e) {
            throw new IllegalStateException(e.message, e)
        } finally {
            out?.close()
        }
    }

    private static void indexFile(Project project, Indexer indexer, File file) {
        if (file.name.endsWith('.class')) {
            FileInputStream fis = null
            try {
                fis = new FileInputStream(file)

                ClassInfo info = indexer.index(fis)
                if (info != null) {
                    project.logger.info("Indexed ${info.name()} (${info.annotations().size()} annotations)")
                }
            } catch (final Exception e) {
                throw new IllegalStateException(e.message, e)
            } finally {
                fis?.close()
            }
        }
    }
}
