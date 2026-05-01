package cn.itcast.embedding;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.TextReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 城市信息向量化处理组件
 * 在应用启动时将城市数据文件转换为向量并存储到向量数据库
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CityEmbedding {

    @Value("classpath:citys.txt")
    private Resource resource;
    private final VectorStore vectorStore;

    // @PostConstruct
    public void init(){
        //1. 读：读取文件内容
        TextReader textReader = new TextReader(this.resource);
        textReader.getCustomMetadata().put("filename", "citys.txt");

        //2. 切：
        List<Document> documentList = textReader.get();
        //参数分别是：默认分块大小、最小分块字符数、最小向量化长度（太小的忽略）、最大分块数量、不保留分隔符（\n啥的）
        TokenTextSplitter tokenTextSplitter = new TokenTextSplitter(200, 100, 5, 10000, false);
        List<Document> splitDocumentList = tokenTextSplitter.split(documentList);

        //3. 存：存储到向量库
        this.vectorStore.add(splitDocumentList);
        log.info("数据写入向量库成功，数据条数：{}", splitDocumentList.size());
    }

}
