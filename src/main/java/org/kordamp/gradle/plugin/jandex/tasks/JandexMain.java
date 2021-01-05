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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author Andres Almiray
 */
public class JandexMain {
    private final String[] sources;
    private boolean verbose;
    private Path destination;

    private JandexMain(String... args) {
        List<String> srcs = new ArrayList<>();

        for (int i = 0; i < args.length; i++) {
            String arg = args[i];

            if (arg.charAt(0) == '-') {
                switch (arg.charAt(1)) {
                    case 'v':
                        verbose = true;
                        break;
                    case 'o':
                        if (i >= args.length) {
                            throw new IllegalArgumentException("-o requires an output file name");
                        }

                        String name = args[++i];
                        if (name.length() < 1) {
                            throw new IllegalArgumentException("-o requires an output file name");
                        }

                        destination = Paths.get(name);
                        break;
                    default:
                        throw new IllegalArgumentException("Option not understood: " + arg);
                }
            } else {
                srcs.add(arg);
            }
        }

        sources = srcs.toArray(new String[0]);

        if (sources.length == 0) {
            throw new IllegalArgumentException("Source location not specified");
        }

        if (destination == null) {
            throw new IllegalArgumentException("Missing index location");
        }
    }

    private void execute(PrintWriter out, PrintWriter err) throws IOException {
        Indexer indexer = new Indexer();

        ClassFileVisitor classFileVisitor = new ClassFileVisitor(out, err, indexer);
        for (String src : sources) {
            if (verbose) {
                out.println("Indexing files at " + Paths.get(src).toAbsolutePath());
            }
            Files.walkFileTree(Paths.get(src), classFileVisitor);
        }

        try (FileOutputStream output = new FileOutputStream(destination.toFile())) {
            destination.toFile().getParentFile().mkdirs();
            IndexWriter writer = new IndexWriter(output);
            Index index = indexer.complete();
            writer.write(index);
            if (verbose) out.println("Index has been written to " + destination.toAbsolutePath());
        }
    }

    public static void main(String[] args) {
        run(new PrintWriter(System.out), new PrintWriter(System.err), args);
    }

    public static int run(PrintWriter out, PrintWriter err, String... args) {
        if (args.length == 0) {
            printUsage(out);
            return 1;
        }

        boolean printUsage = true;
        try {
            JandexMain jandex = new JandexMain(args);
            printUsage = false;
            jandex.execute(out, err);
        } catch (Exception e) {
            e.printStackTrace(err);

            if (printUsage) {
                printUsage(out);
            }

            return 1;
        } finally {
            out.flush();
        }

        return 0;
    }

    private static void printUsage(PrintWriter out) {
        out.println("Usage: jandex [-v] [-o index-file-name] <directory> [<directory>]");
        out.println("Options:");
        out.println("  -v  verbose output");
        out.println("  -o  name the external index file file-name");
    }

    private class ClassFileVisitor extends SimpleFileVisitor<Path> {
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
                    if (verbose && info != null) {
                        out.println("Indexed " + info.name() + " (" + info.annotations().size() + " annotations)");
                    }
                } catch (Exception e) {
                    throw new IOException("Unexpected error while indexing " + file.getAbsolutePath(), e);
                }
            }
        }
    }
}
