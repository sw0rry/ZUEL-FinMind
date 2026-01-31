package org.swy.zuelfinmind.service.strategy;

import org.springframework.web.multipart.MultipartFile;
import org.swy.zuelfinmind.model.VectorSearchResult;

import java.util.List;

public interface VectorStoreStrategy {
    /**
     * 存资料
     * @param file 上传的文件
     * @return 成功消息
     */
    String store(MultipartFile file);

    /**
     * 取资料
     * @param query 用户问题
     * @return 标准化的搜索结果列表
     */
    List<VectorSearchResult> search(String query);
}
