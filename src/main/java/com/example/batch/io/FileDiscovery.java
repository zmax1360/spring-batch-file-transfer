package com.example.batch.io;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.BiPredicate;
import java.util.stream.Stream;

public class FileDiscovery {
    private static final String REQUIRED_KEYWORD = "nomatch";

    public static List<Path> discoverFiles(Path archiveDir, String globPattern) throws IOException {
        String glob = globPattern == null || globPattern.isBlank() ? "*.gz" : globPattern;
        
        BiPredicate<Path, java.nio.file.attribute.BasicFileAttributes> matcher = (p, attr) -> {
            if (!attr.isRegularFile()) return false;
            String fileName = p.getFileName().toString().toLowerCase();
            if (!fileName.endsWith(".gz")) return false;
            if (!fileName.contains(REQUIRED_KEYWORD)) return false;
            
            Path parent = p.getParent();
            if (parent == null) return false;
            Path parentName = parent.getFileName();
            if (parentName == null || !"archive".equals(parentName.toString())) return false;
            
            Path grandParent = parent.getParent();
            if (grandParent == null || grandParent.getFileName() == null) return false;
            if (!"report".equals(grandParent.getFileName().toString())) return false;
            
            return true;
        };

        try (Stream<Path> stream = Files.find(archiveDir, Integer.MAX_VALUE, matcher)) {
            return stream.filter(p -> {
                try {
                    return Files.size(p) > 0;
                } catch (IOException e) {
                    return false;
                }
            }).sorted().toList();
        }
    }
}

