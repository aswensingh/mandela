package com.marketinghub.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.embedding.EmbeddingResponseMetadata;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Deterministic, bag-of-words 1536-dim embedding so RAG plumbing (insert + similarity
 * search + tenant metadata filter) can be exercised locally without an OpenAI key.
 *
 * Vectors are NOT semantically meaningful — they're a simple hashing-trick over
 * word tokens. Two texts that share a vocabulary will land near each other (good
 * enough to make the Phase 17 gate's "whitening" question retrieve the "whitening"
 * chunk); two semantically similar texts with disjoint vocabularies will NOT.
 *
 * Flip OPENAI_MOCK=false + OPENAI_EMBEDDING_AUTOCONFIG=openai to swap in real
 * text-embedding-3-small via Spring AI's OpenAI starter.
 */
@Component
@ConditionalOnProperty(name = "ai.mock", havingValue = "true", matchIfMissing = true)
public class MockEmbeddingModel implements EmbeddingModel {

    private static final Logger log = LoggerFactory.getLogger(MockEmbeddingModel.class);
    private static final int DIMENSIONS = 1536;
    private static final Pattern WORD = Pattern.compile("[A-Za-z0-9]+");

    public MockEmbeddingModel() {
        log.info("EmbeddingModel: MOCK mode active — using deterministic bag-of-words hashes");
    }

    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        List<Embedding> out = new ArrayList<>(request.getInstructions().size());
        int idx = 0;
        for (String input : request.getInstructions()) {
            out.add(new Embedding(embedText(input), idx++));
        }
        return new EmbeddingResponse(out, new EmbeddingResponseMetadata());
    }

    @Override
    public float[] embed(Document document) {
        return embedText(document.getText() == null ? "" : document.getText());
    }

    @Override
    public int dimensions() {
        return DIMENSIONS;
    }

    private float[] embedText(String text) {
        float[] vec = new float[DIMENSIONS];
        if (text == null || text.isEmpty()) return vec;
        var matcher = WORD.matcher(text.toLowerCase(Locale.ROOT));
        while (matcher.find()) {
            int pos = Math.floorMod(matcher.group().hashCode(), DIMENSIONS);
            vec[pos] += 1.0f;
        }
        return l2Normalize(vec);
    }

    private static float[] l2Normalize(float[] v) {
        double sumSquares = 0;
        for (float f : v) sumSquares += (double) f * f;
        if (sumSquares == 0) return v;
        float norm = (float) Math.sqrt(sumSquares);
        for (int i = 0; i < v.length; i++) v[i] /= norm;
        return v;
    }
}
