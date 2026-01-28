package org.swy.zuelfinmind.utils;

import org.apache.tika.Tika;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 文档加工厂：负责把文件变成能吃的向量块
 */
public class DocumentUtils {

    private static final Tika tika = new Tika(); // Tika实例很重，复用它

    /**
     * 1.【咀嚼】解析任意文件
     */
    public static String parseFile(MultipartFile file) {
        try {
            System.out.println("📄 正在解析文件: " + file.getOriginalFilename());
            // Tika自动识别文件类型，提取纯文本
            return tika.parseToString(file.getInputStream());
        } catch (IOException | org.apache.tika.exception.TikaException e) {
            e.printStackTrace();
            return "";
        }
    }

    /**
     * 2. 【切割】把长文本切成小块（Chunking）
     * @param text 原始长文本
     * @param chunkSize 每块的大小（比如500字）
     * @param overlap 重叠部分（比如50字，防止上下文丢失）
     */
    public static List<String> splitText(String text, int chunkSize, int overlap) {
        List<String> chunks = new ArrayList<>();
        if (text == null || text.isEmpty()) return chunks;

        // 清洗一下：去掉多余换行和空格，变成紧凑的文本
        String cleanText = text.replaceAll("\\s+", " ").trim();

        int length = cleanText.length();
        int start = 0;

        while (start < length) {
            // 计算结束位置
            int end = Math.min(start + chunkSize, length);

            // 切割
            String chunk = cleanText.substring(start, end);
            chunks.add(chunk);

            // 移动指针（向前进chunkSize，但要倒退一点overlap）
            // 如果已经到了最后，就跳出
            if (end == length) break;
            start +=(chunkSize - overlap);
        }

        System.out.println("✂️ 文本已切割为 " + chunks.size() + " 块");
        return chunks;
    }
}
