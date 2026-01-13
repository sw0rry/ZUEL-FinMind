# 📈 ZUEL-FinMind | 智能金融投研助手

> 一个基于 **Spring Boot 3 + Spring AI + DeepSeek** 的垂直领域金融问答系统。
> 专为解决传统研报检索效率低、通用大模型金融知识匮乏的问题而设计。

## 🎯 项目亮点 (Highlights)

* **垂直领域定制**：通过 Prompt Engineering 构建“资深金融分析师”人设，**有效拦截 100% 的非金融类闲聊请求**。
* **前沿技术栈**：采用 Spring Boot 3.2 + JDK 17 最新标准，集成 Spring AI 框架接入 DeepSeek V3 模型。
* **全栈闭环**：实现了从后端 RESTful 接口到前端流式对话 UI 的完整开发。
* **系统思维**：基于 IOC 容器管理 AI Client，实现模型调用的解耦与高可维护性。

## 🛠️ 技术栈 (Tech Stack)

* **核心框架**: Spring Boot 3.2.2
* **AI 接入**: Spring AI 0.8.1 (OpenAI Protocol)
* **大模型**: DeepSeek-V3 (Temperature=0.1 严谨模式)
* **数据持久化**: MySQL + MyBatis-Plus (预留)
* **前端**: HTML5 + CSS3 (Dark Mode 金融终端风格)

## 📸 效果演示 (Demo)

### 1. 专业金融分析
> 用户提问：比特币现在值得买吗？
> AI 回答：从资产配置角度分析...（展示专业性）

### 2. 边界控制 (Guardrails)
> 用户提问：中午吃什么？
> AI 回答：抱歉，作为金融分析师，我不关注除此之外的话题。

## 🚀 快速开始 (Quick Start)

1. 克隆项目
\`\`\`bash
git clone https://github.com/你的用户名/ZUEL-FinMind.git
\`\`\`

2. 配置 API Key
在 `application.properties` 中填入你的 DeepSeek Key：
\`\`\`properties
spring.ai.openai.api-key=sk-xxxxxx
\`\`\`

3. 启动应用
运行 `FintechApplication.java`，访问 `http://localhost:8080` 即可体验。

---
*Created by [三文鱼] @ ZUEL*
