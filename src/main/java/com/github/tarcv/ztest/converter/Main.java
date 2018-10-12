package com.github.tarcv.ztest.converter;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;

import static com.github.tarcv.ztest.converter.AcsConverter.convertAcs;
import static com.github.tarcv.ztest.converter.DecorateConverter.convertDecorate;
import static java.nio.file.FileVisitResult.CONTINUE;
import static java.nio.file.FileVisitResult.SKIP_SUBTREE;

public class Main {
    public static void main(String[] args) throws IOException {
        Files.walkFileTree(Paths.get(args[0]), new FileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                String lumpName = file.getFileName().toString().toUpperCase();
                if (lumpName.endsWith(".ACS")) {
                    convertAcs(file);
                } if (lumpName.startsWith("DECORATE")) {
                    convertDecorate(file);
                }
                return CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
                throw exc;
            }

            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                if (dir.getFileName().toString().startsWith(".")) return SKIP_SUBTREE;
                return CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                return CONTINUE;
            }
        });
    }
}
