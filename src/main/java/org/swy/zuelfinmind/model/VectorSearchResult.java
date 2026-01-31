package org.swy.zuelfinmind.model;

import lombok.Data;

@Data
public class VectorSearchResult {
    private String text; // 文本内容
    private Float score; // 相似度得分
    private String source; // 来源文件名

    public VectorSearchResult(String text, Float score, String source) {
        this.text = text;
        this.score = score;
        this.source = source;
    }
}
