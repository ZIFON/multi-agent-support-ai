package com.example.multiagent.retrieval;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Component
public class Chunker {
    private static final Pattern HEADING_PATTERN = Pattern.compile("^## (.+)$", Pattern.MULTILINE);
    private static final int DEFAULT_CHUNK_SIZE = 1000;
    private static final int MIN_CHUNK_SIZE = 800;
    private static final int MAX_CHUNK_SIZE = 1200;

    public List<Chunk> chunk(String docId, String content) {
        List<Chunk> chunks = new ArrayList<>();
        
        // Check if we have markdown headings
        java.util.regex.Matcher headingMatcher = HEADING_PATTERN.matcher(content);
        boolean hasHeadings = headingMatcher.find();
        
        if (hasHeadings) {
            // Reset matcher
            headingMatcher = HEADING_PATTERN.matcher(content);
            
            int lastEnd = 0;
            String lastHeading = null;
            
            while (headingMatcher.find()) {
                // Process section before this heading
                if (lastEnd < headingMatcher.start()) {
                    String sectionText = content.substring(lastEnd, headingMatcher.start()).trim();
                    if (!sectionText.isEmpty()) {
                        String sectionTitle = lastHeading != null ? lastHeading : "Introduction";
                        if (sectionText.length() > MAX_CHUNK_SIZE) {
                            chunks.addAll(splitBySize(sectionText, sectionTitle, docId));
                        } else {
                            chunks.add(new Chunk(docId, sectionTitle, sectionText));
                        }
                    }
                }
                
                lastHeading = headingMatcher.group(1);
                lastEnd = headingMatcher.end();
            }
            
            // Process final section
            if (lastEnd < content.length()) {
                String sectionText = content.substring(lastEnd).trim();
                if (!sectionText.isEmpty()) {
                    String sectionTitle = lastHeading != null ? lastHeading : "Content";
                    if (sectionText.length() > MAX_CHUNK_SIZE) {
                        chunks.addAll(splitBySize(sectionText, sectionTitle, docId));
                    } else {
                        chunks.add(new Chunk(docId, sectionTitle, sectionText));
                    }
                }
            }
            
            // Handle content before first heading
            headingMatcher = HEADING_PATTERN.matcher(content);
            if (headingMatcher.find()) {
                String introText = content.substring(0, headingMatcher.start()).trim();
                if (!introText.isEmpty() && chunks.isEmpty()) {
                    // No content was added before first heading, add intro
                    if (introText.length() > MAX_CHUNK_SIZE) {
                        chunks.addAll(splitBySize(introText, "Introduction", docId));
                    } else {
                        chunks.add(0, new Chunk(docId, "Introduction", introText));
                    }
                }
            }
        }
        
        // If no headings or no chunks created, chunk by size
        if (chunks.isEmpty()) {
            chunks.addAll(splitBySize(content, "Content", docId));
        }
        
        return chunks;
    }

    private List<Chunk> splitBySize(String text, String baseTitle, String docId) {
        List<Chunk> chunks = new ArrayList<>();
        int start = 0;
        int chunkIndex = 0;

        while (start < text.length()) {
            int end = Math.min(start + DEFAULT_CHUNK_SIZE, text.length());
            
            // Try to break at sentence or paragraph boundary
            if (end < text.length()) {
                // Look for paragraph break first
                int paraBreak = text.lastIndexOf("\n\n", end);
                if (paraBreak > start + MIN_CHUNK_SIZE) {
                    end = paraBreak + 2;
                } else {
                    // Look for sentence boundary
                    int sentenceBreak = Math.max(
                        text.lastIndexOf(". ", end),
                        Math.max(
                            text.lastIndexOf(".\n", end),
                            text.lastIndexOf("! ", end)
                        )
                    );
                    if (sentenceBreak > start + MIN_CHUNK_SIZE) {
                        end = sentenceBreak + 2;
                    }
                }
            }

            String chunkText = text.substring(start, end).trim();
            if (!chunkText.isEmpty()) {
                String sectionTitle = chunks.size() == 0 ? baseTitle : baseTitle + " (Part " + (chunkIndex + 1) + ")";
                chunks.add(new Chunk(docId, sectionTitle, chunkText));
                chunkIndex++;
            }
            start = end;
        }

        return chunks;
    }

    public List<Chunk> chunkAll(Map<String, String> documents) {
        List<Chunk> allChunks = new ArrayList<>();
        for (Map.Entry<String, String> entry : documents.entrySet()) {
            allChunks.addAll(chunk(entry.getKey(), entry.getValue()));
        }
        return allChunks;
    }
}
