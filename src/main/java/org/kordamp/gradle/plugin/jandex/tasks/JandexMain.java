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
package org.kordamp.gradle.plugin.jandex.tasks;

import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.Index;
import org.jboss.jandex.IndexWriter;
import org.jboss.jandex.Indexer;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;

/**
 * @author Andres Almiray
 */
public class JandexMain {
    public static void main(String[] args) {
        run(new PrintWriter(System.out), new PrintWriter(System.err), args);
    }

    public static int run(PrintWriter out, PrintWriter err, String... args) {
        if (null == args || args.length < 2) {
            err.println("Invalid arguments. Must define destination and at least one source");
            return 1;
        }

        try {
            String[] cargs = new String[args.length - 1];
            System.arraycopy(args, 1, cargs, 0, cargs.length);
            createIndex(out, err, Paths.get(args[0]), cargs);
        } catch (IOException e) {
            e.printStackTrace(err);
            return 1;
        }

        return 0;
    }

    private static void createIndex(PrintWriter out, PrintWriter err, Path destination, String... sources) throws IOException {
        Indexer indexer = new Indexer();

        ClassFileVisitor classFileVisitor = new ClassFileVisitor(out, err, indexer);
        for (String src : sources) {
            Files.walkFileTree(Paths.get(src), classFileVisitor);
        }

        try (FileOutputStream output = new FileOutputStream(destination.toFile())) {
            destination.toFile().getParentFile().mkdirs();
            IndexWriter writer = new IndexWriter(output);
            Index index = indexer.complete();
            writer.write(index);
            out.println("Index has been written to " + destination.toAbsolutePath());
        }
    }

    private static class ClassFileVisitor extends SimpleFileVisitor<Path> {
        private final PrintWriter out;
        private final PrintWriter err;
        private final Indexer indexer;

        ClassFileVisitor(PrintWriter out, PrintWriter err, Indexer indexer) {
            this.out = out;
            this.err = err;
            this.indexer = indexer;
        }

        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
            indexFile(file.toFile());
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitFileFailed(Path file, IOException e) throws IOException {
            e.printStackTrace(err);
            return FileVisitResult.CONTINUE;
        }

        private void indexFile(File file) throws IOException {
            if (file.getName().endsWith(".class")) {
                try (FileInputStream fis = new FileInputStream(file)) {
                    ClassInfo info = indexer.index(fis);
                    if (info != null) {
                        out.println("Indexed " + info.name() + " (" + info.annotations().size() + " annotations)");
                    }
                } catch (final Exception e) {
                    throw new IllegalStateException(e.getMessage(), e);
                }
            }
        }
    }
}
