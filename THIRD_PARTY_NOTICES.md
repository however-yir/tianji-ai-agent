# Third-Party Notices

This software includes third-party open-source components. Generated SBOMs (SPDX) are
attached to the published container images (`ghcr.io/however-yir/tianji-ai-agent` via
docker/build-push-action `sbom: true`) and cover the runtime Java/Node dependencies
exactly. Key families:

- **Java:** Spring Boot / Spring Cloud / Spring AI 1.0.9 / Spring AI Alibaba BOM classpath
  (DashScope integration through OpenAI-compatible endpoints), MyBatis-Plus, Nacos client,
  Resilience4j, Jackson, Knife4j, Lombok, Hutool, Apache POI/PDFBox, Elasticsearch client.
- **Node:** React, react-markdown, remark/rehype plugins, katex, highlight.js, mermaid,
  dompurify, vite tooling.
- **Runtime images:** eclipse-temurin (JRE), tesseract-ocr (Apache 2.0).

Full license texts are available from each component's distribution; this notice links to
the SBOM rather than reproducing license text.
