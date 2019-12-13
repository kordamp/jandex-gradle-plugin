package org.kordamp.gradle.plugin.jandex.tasks

import org.gradle.api.Project
import org.gradle.api.file.FileTree
import org.gradle.api.file.FileVisitDetails
import org.gradle.api.file.FileVisitor
import org.jboss.jandex.ClassInfo
import org.jboss.jandex.Index
import org.jboss.jandex.IndexReader
import org.jboss.jandex.IndexWriter
import org.jboss.jandex.Indexer

/**
 * @author Andres Almiray
 */
class JandexHelper {
    static final Index readIndex(Project project, File source) {
        FileInputStream input = null
        try {
            input = new FileInputStream(source)
            IndexReader reader = new IndexReader(input)
            return reader.read()
        } catch (IOException e) {
            throw new IllegalStateException(e.message, e)
        } finally {
            input?.close()
        }
    }

    static final createIndex(Project project, FileTree fileSets, File destination) {
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

        destination.parentFile.mkdirs()

        FileOutputStream out = null
        try {
            out = new FileOutputStream(destination)
            IndexWriter writer = new IndexWriter(out)
            Index index = indexer.complete()
            writer.write(index)
            println("Index has been written to ${destination.absolutePath}")
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
