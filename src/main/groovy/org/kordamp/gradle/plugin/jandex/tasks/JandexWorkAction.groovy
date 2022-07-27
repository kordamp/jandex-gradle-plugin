/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * Copyright 2019-2022 Andres Almiray.
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

import groovy.transform.CompileStatic
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import org.gradle.workers.WorkAction
import org.jboss.jandex.ClassInfo
import org.jboss.jandex.Index
import org.jboss.jandex.IndexWriter
import org.jboss.jandex.Indexer

import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes

/**
 * @author Andres Almiray
 */
@CompileStatic
abstract class JandexWorkAction implements WorkAction<JandexWorkParameters> {
    private final Logger logger = Logging.getLogger(JandexWorkAction)

    @Override
    void execute() {
        Indexer indexer = new Indexer()

        ClassFileVisitor classFileVisitor = new ClassFileVisitor(indexer)
        for (String src : parameters.sources.get()) {
            logger.info("Indexing files at " + Paths.get(src).toAbsolutePath())

            Files.walkFileTree(Paths.get(src), classFileVisitor)
        }

        File destination = parameters.destination.asFile.get()
        FileOutputStream output = new FileOutputStream(destination)

        try {
            destination.parentFile.mkdirs()
            IndexWriter writer = new IndexWriter(output)
            Index index = indexer.complete()
            writer.write(index)
            logger.info('Index has been written to ' + destination.absolutePath)
        } finally {
            output?.close()
        }
    }

    private class ClassFileVisitor extends SimpleFileVisitor<Path> {
        private final Indexer indexer

        ClassFileVisitor(Indexer indexer) {
            this.indexer = indexer
        }

        @Override
        FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
            indexFile(file.toFile())
            return FileVisitResult.CONTINUE
        }

        @Override
        FileVisitResult visitFileFailed(Path file, IOException e) throws IOException {
            e.printStackTrace()
            return FileVisitResult.CONTINUE
        }

        private void indexFile(File file) throws IOException {
            if (file.name.endsWith('.class')) {
                FileInputStream fis = new FileInputStream(file)
                try {
                    ClassInfo info = indexer.index(fis)
                    if (info != null) {
                        logger.info('Indexed ' + info.name() + ' (' + info.annotations().size() + ' annotations)')
                    }
                } catch (Exception e) {
                    throw new IOException('Unexpected error while indexing ' + file.absolutePath, e)
                } finally {
                    fis?.close()
                }
            }
        }
    }
}