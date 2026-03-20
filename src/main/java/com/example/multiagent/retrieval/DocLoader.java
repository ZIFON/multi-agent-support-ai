package com.example.multiagent.retrieval;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

@Component
public class DocLoader {
    private static final String DOCS_DIR = "./docs";

    public Map<String, String> loadAllDocuments() {
        Map<String, String> documents = new HashMap<>();
        Path docsPath = Paths.get(DOCS_DIR);
        
        if (!Files.exists(docsPath)) {
            return documents;
        }

        try (Stream<Path> paths = Files.walk(docsPath)) {
            paths.filter(Files::isRegularFile)
                 .filter(p -> p.toString().endsWith(".md") || p.toString().endsWith(".txt"))
                 .forEach(path -> {
                     try {
                         String content = Files.readString(path);
                         String docId = path.getFileName().toString().replaceFirst("[.][^.]+$", "");
                         documents.put(docId, content);
                     } catch (IOException e) {
                         System.err.println("Error loading document " + path + ": " + e.getMessage());
                     }
                 });
        } catch (IOException e) {
            System.err.println("Error reading docs directory: " + e.getMessage());
        }

        return documents;
    }

    public List<String> getDocumentIds() {
        return new ArrayList<>(loadAllDocuments().keySet());
    }
}
