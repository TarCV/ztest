package converter;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;

import static converter.AcsConverter.convertAcs;
import static converter.DecorateConverter.convertDecorate;
import static java.nio.file.FileVisitResult.CONTINUE;
import static java.nio.file.FileVisitResult.SKIP_SUBTREE;

public class Main {
    public static void main(String[] args) throws IOException {
        Path outputDir = new File(".").toPath();
        Files.walkFileTree(Paths.get(args[0]), new FileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                String lumpName = file.getFileName().toString().toUpperCase();
                if (lumpName.endsWith(".ACS")) {
                    convertAcs(file, outputDir);
                } if (lumpName.startsWith("DECORATE")) {
                    convertDecorate(file, outputDir);
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
