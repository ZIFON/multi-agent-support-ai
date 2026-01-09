package com.example.multiagent.retrieval;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Component
public class Retriever {
    private static final int TOP_K = 4;
    private final DocLoader docLoader;
    private final Chunker chunker;
    private List<Chunk> allChunks = null;

    @Autowired
    public Retriever(DocLoader docLoader, Chunker chunker) {
        this.docLoader = docLoader;
        this.chunker = chunker;
        loadChunks();
    }

    private void loadChunks() {
        if (allChunks == null) {
            Map<String, String> documents = docLoader.loadAllDocuments();
            allChunks = chunker.chunkAll(documents);
        }
    }

    public List<Chunk> retrieve(String query, int topK) {
        loadChunks();
        if (allChunks.isEmpty()) {
            return new ArrayList<>();
        }

        List<ScoredChunk> scoredChunks = new ArrayList<>();
        Set<String> queryTerms = tokenize(query.toLowerCase());

        for (Chunk chunk : allChunks) {
            double score = scoreChunk(chunk, queryTerms);
            scoredChunks.add(new ScoredChunk(chunk, score));
        }

        return scoredChunks.stream()
                .sorted((a, b) -> Double.compare(b.score, a.score))
                .limit(topK)
                .map(sc -> sc.chunk)
                .collect(Collectors.toList());
    }

    public List<Chunk> retrieve(String query) {
        return retrieve(query, TOP_K);
    }

    private double scoreChunk(Chunk chunk, Set<String> queryTerms) {
        Set<String> chunkTokens = tokenize(chunk.getText().toLowerCase());
        Set<String> titleTokens = tokenize(chunk.getSectionTitle().toLowerCase());

        double score = 0.0;

        // Count matches in text
        int matches = 0;
        int uniqueMatches = 0;
        for (String term : queryTerms) {
            boolean found = false;
            for (String token : chunkTokens) {
                if (token.contains(term) || term.contains(token)) {
                    matches++;
                    found = true;
                }
            }
            if (found) uniqueMatches++;
        }

        score += matches * 1.0;
        score += uniqueMatches * 2.0; // Boost for unique matches

        // Boost if title contains query terms
        int titleMatches = 0;
        for (String term : queryTerms) {
            for (String titleToken : titleTokens) {
                if (titleToken.contains(term) || term.contains(titleToken)) {
                    titleMatches++;
                    break;
                }
            }
        }
        score += titleMatches * 3.0; // Higher boost for title matches

        return score;
    }

    private Set<String> tokenize(String text) {
        Set<String> tokens = new HashSet<>();
        Pattern pattern = Pattern.compile("\\w+");
        pattern.matcher(text).results().forEach(m -> tokens.add(m.group().toLowerCase()));
        return tokens;
    }

    private static class ScoredChunk {
        Chunk chunk;
        double score;

        ScoredChunk(Chunk chunk, double score) {
            this.chunk = chunk;
            this.score = score;
        }
    }
}
