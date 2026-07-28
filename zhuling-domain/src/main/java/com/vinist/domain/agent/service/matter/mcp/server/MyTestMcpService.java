package com.vinist.domain.agent.service.matter.mcp.server;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Service;

/**
 * @title: MyTestMcpService
 * @description: <本地测试mcp服务>
 * @author: hd
 * @date: 2026/7/20 10:50
 */
@Slf4j
@Service
public class MyTestMcpService {

    @Tool(description = "小写字母转换为泽邦电码")
    public XxxResponse toZbCase(XxxRequest request) {
        log.info("进入mcp工具，方法：小写字母转换为泽邦电码");
        XxxResponse xxxResponse = new XxxResponse();
        String word = request.getWord();
        // 给每个字符后面增加一个随机的特殊符号
        word = word.chars().mapToObj(c -> (char) c + "*").collect(StringBuilder::new, StringBuilder::append, StringBuilder::append).toString();
        xxxResponse.setContent(word);
        log.info("结束mcp工具，方法：小写字母转换为泽邦电码");
        return xxxResponse;
    }

    @Tool(description = "泽邦电码转换为喵星语言")
    public XxxResponse toMiao(XxxRequest request) {
        log.info("进入mcp工具，方法：泽邦电码转换为喵星语言");
        XxxResponse xxxResponse = new XxxResponse();
        String word = request.getWord();
        int length = word.length();
        StringBuilder stringBuilder = new StringBuilder();
        for (int i = 0; i < length; i++) {
            stringBuilder.append("喵");
        }

        xxxResponse.setContent(stringBuilder.toString());
        log.info("结束mcp工具，方法：泽邦电码转换为喵星语言");
        return xxxResponse;
    }

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class XxxRequest {
        @JsonProperty(required = true, value = "word")
        @JsonPropertyDescription("任意字符串，字母。例如: good,hd,f*d")
        private String word;
    }

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class XxxResponse {
        @JsonProperty(required = true, value = "content")
        @JsonPropertyDescription("单词转换结果")
        private String content;
    }

}
