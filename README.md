# 🧠 ZUEL-FinMind | 金融 AI 助手

> 基于 **Spring Boot + DeepSeek-V3 + RAG** 架构的垂直领域智能问答系统，专为解决金融领域长尾知识检索与“幻觉”问题而设计。

## ✨ 核心功能
- **📚 混合检索 (Hybrid Rerank)**: 自研 Java 版重排序算法 (70% 向量 + 30% 关键词)，Top-5 召回准确率从 70% 提升至 95%。
- **💾 多轮对话记忆**: 基于 MyBatis-Plus 实现会话持久化，支持连续追问。
- **🐳 容器化部署**: 提供标准 Dockerfile，支持一键启动与环境隔离。
- **📂 异构文档解析**: 集成 Apache Tika，支持 PDF/Word/TXT 自动切片与向量化。

## 🛠 技术栈
- **Backend**: Java 17, Spring Boot 3
- **LLM**: DeepSeek-V3 (via ZhipuAI SDK)
- **Vector DB**: Pinecone
- **DevOps**: Docker, Maven
- **Frontend**: Vue 3, Axios, Tailwind CSS

## 🚀 快速启动 (Docker)

1. **克隆仓库**
   ```bash
   git clone [https://github.com/sw0rry/ZUEL-FinMind.git](https://github.com/sw0rry/ZUEL-FinMind.git)
   
2. **配置密钥**
在根目录创建 env.list 文件：

   ```Properties
   YOUR_AI_KEY=your_key_here
   YOUR_EB_KEY=your_key_here
   YOUR_EDB_KEY=your_key_here
   SPRING_DATASOURCE_URL=jdbc:mysql://host.docker.internal:3306/zuel_finmind...

3. **构建并运行**
   ```Bash
   docker build -t zuel-finmind:v1.0 .
   docker run -d -p 8080:8080 --env-file ./env.list zuel-finmind:v1.0
