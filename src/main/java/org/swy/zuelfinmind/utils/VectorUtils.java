package org.swy.zuelfinmind.utils;

import java.util.List;

public class VectorUtils {
    /**
     * 计算两个向量的余弦相似度
     * @param v1 向量1
     * @param v2 向量2
     * @return 相似度（0.0~1.0），越接近1越相似
     */
    public static double cosineSimilarity(float[] v1, float[] v2) {
        if (v1.length != v2.length) {
            throw new IllegalArgumentException();
        }

        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;

        for (int i = 0; i < v1.length; i++) {
            dotProduct += v1[i] * v2[i];
            normA += Math.pow(v1[i], 2);
            normB += Math.pow(v2[i], 2);
        }

        if (normA == 0 || normB == 0) {
            return 0.0;
        }

        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    // 为了兼容List<Double>，我们重载个List<Double>版本备用
    public static double cosineSimilarity(List<Double> v1, List<Double> v2) {
        // ... 原理一样
        if (v1.size() != v2.size()) {
            throw new IllegalArgumentException();
        }

        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;

        for (int i = 0; i < v1.size(); i++) {
            dotProduct += v1.get(i) * v2.get(i);
            normA += Math.pow(v1.get(i), 2);
            normB += Math.pow(v2.get(i), 2);
        }

        if (normA == 0 || normB == 0) {
            return 0.0;
        }

        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }
}
