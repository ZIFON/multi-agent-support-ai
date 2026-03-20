package com.example.multiagent.retrieval;

import com.example.multiagent.llm.LlmClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Pattern;

@Component
public class Retriever {
    private static final int TOP_K = 4;
    private static final int EMBEDDING_BATCH_SIZE = 32;
    private static final double MIN_COSINE_SIMILARITY = 0.15;

    private final DocLoader docLoader;
    private final Chunker chunker;
    private final LlmClient llmClient;
    private List<Chunk> allChunks = null;
    private List<EmbeddedChunk> embeddedChunks = null;
    private boolean embeddingsAttempted = false;

    @Autowired
    public Retriever(DocLoader docLoader, Chunker chunker, LlmClient llmClient) {
        this.docLoader = docLoader;
        this.chunker = chunker;
        this.llmClient = llmClient;
        loadChunks();
    }

    private void loadChunks() {
        if (allChunks == null) {
            Map<String, String> documents = docLoader.loadAllDocuments();
            documents.keySet().removeIf(docId -> {
                String id = docId.toLowerCase(Locale.ROOT);
                return id.contains("billing") || id.equals("billing_policy");
            });
            allChunks = chunker.chunkAll(documents);
        }

        if (!embeddingsAttempted) {
            embeddingsAttempted = true;
            try {
                embeddedChunks = createEmbeddedChunks(allChunks);
            } catch (Exception e) {
                // Якщо embeddings не вдалося ініціалізувати — зберігаємо keyword-fallback.
                System.err.println("Embeddings init failed, falling back to keyword retrieval: " + e.getMessage());
                embeddedChunks = null;
            }
        }
    }

    public List<Chunk> retrieve(String query, int topK) {
        loadChunks();
        if (allChunks.isEmpty()) {
            return new ArrayList<>();
        }

        // 1) Спробувати vector RAG через embeddings.
        if (embeddedChunks != null && !embeddedChunks.isEmpty()) {
            List<Chunk> embeddingResults = retrieveByEmbeddings(query, topK);
            if (embeddingResults != null) {
                return embeddingResults;
            }
        }

        // 2) Fallback: keyword-based retrieval (щоб система не падала).
        return retrieveByKeyword(query, topK);
    }

    private List<Chunk> retrieveByEmbeddings(String query, int topK) {
        try {
            if (query == null || query.trim().isEmpty()) {
                return new ArrayList<>();
            }

            double[] queryVector = llmClient.embedTexts(List.of(query)).get(0);
            double queryNorm = vectorNorm(queryVector);
            if (queryNorm == 0.0) {
                return new ArrayList<>();
            }

            List<ScoredChunk> scoredChunks = new ArrayList<>();
            for (EmbeddedChunk embedded : embeddedChunks) {
                double similarity = cosineSimilarity(queryVector, queryNorm, embedded.vector, embedded.norm);
                if (similarity >= MIN_COSINE_SIMILARITY) {
                    scoredChunks.add(new ScoredChunk(embedded.chunk, similarity));
                }
            }

            scoredChunks.sort((a, b) -> Double.compare(b.score, a.score));
            List<Chunk> result = new ArrayList<>();
            int limit = Math.min(topK, scoredChunks.size());
            for (int i = 0; i < limit; i++) {
                result.add(scoredChunks.get(i).chunk);
            }
            return result;
        } catch (Exception e) {
            return null; // signal "failed to use embeddings"
        }
    }

    private List<Chunk> retrieveByKeyword(String query, int topK) {
        List<ScoredChunk> scoredChunks = new ArrayList<>();
        Set<String> queryTerms = tokenize(query.toLowerCase(Locale.ROOT));

        for (Chunk chunk : allChunks) {
            double score = scoreChunk(chunk, queryTerms);
            scoredChunks.add(new ScoredChunk(chunk, score));
        }

        scoredChunks.sort((a, b) -> Double.compare(b.score, a.score));
        List<Chunk> result = new ArrayList<>();
        int limit = Math.min(topK, scoredChunks.size());
        for (int i = 0; i < limit; i++) {
            result.add(scoredChunks.get(i).chunk);
        }
        return result;
    }

    private List<EmbeddedChunk> createEmbeddedChunks(List<Chunk> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return new ArrayList<>();
        }

        int n = chunks.size();
        double[][] vectors = new double[n][];

        // Векторизуємо контент + секцію, щоб embeddings краще "розуміли" структуру документа.
        List<String> texts = new ArrayList<>(n);
        for (Chunk chunk : chunks) {
            texts.add(chunk.getSectionTitle() + "\n" + chunk.getText());
        }

        for (int start = 0; start < texts.size(); start += EMBEDDING_BATCH_SIZE) {
            int end = Math.min(start + EMBEDDING_BATCH_SIZE, texts.size());
            List<String> batch = texts.subList(start, end);
            List<double[]> batchVectors = llmClient.embedTexts(batch);
            for (int i = 0; i < batchVectors.size(); i++) {
                vectors[start + i] = batchVectors.get(i);
            }
        }

        List<EmbeddedChunk> embedded = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            double[] vector = vectors[i];
            if (vector == null) {
                throw new RuntimeException("Missing embedding vector for chunk index " + i);
            }
            double norm = vectorNorm(vector);
            embedded.add(new EmbeddedChunk(chunks.get(i), vector, norm));
        }

        return embedded;
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

    private static double vectorNorm(double[] vector) {
        double sum = 0.0;
        for (double v : vector) {
            sum += v * v;
        }
        return Math.sqrt(sum);
    }

    private static double cosineSimilarity(double[] a, double aNorm, double[] b, double bNorm) {
        double dot = 0.0;
        int len = Math.min(a.length, b.length);
        for (int i = 0; i < len; i++) {
            dot += a[i] * b[i];
        }
        return dot / (aNorm * bNorm + 1e-12);
    }

    private static class ScoredChunk {
        Chunk chunk;
        double score;

        ScoredChunk(Chunk chunk, double score) {
            this.chunk = chunk;
            this.score = score;
        }
    }

    private static class EmbeddedChunk {
        Chunk chunk;
        double[] vector;
        double norm;

        EmbeddedChunk(Chunk chunk, double[] vector, double norm) {
            this.chunk = chunk;
            this.vector = vector;
            this.norm = norm;
        }
    }
}
