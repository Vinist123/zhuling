package com.vinist.domain.agent.service.matter.mcp.server;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @title: MyBizMcpService
 * @description: <本地测试BizMcp服务>
 * @author: hd
 * @date: 2026/7/20 10:50
 */
@Slf4j
@Service
public class MyBizMcpService {

    private List<XxxResponse> musicListGen() {
        List<XxxResponse> xxxResponseList = new ArrayList<>();
        XxxResponse xxxResponse1 = new XxxResponse();
        xxxResponse1.setName("七里香");
        xxxResponse1.setAuthor("周杰伦");
        xxxResponseList.add(xxxResponse1);

        XxxResponse xxxResponse2 = new XxxResponse();
        xxxResponse2.setName("天地龙鳞");
        xxxResponse2.setAuthor("王力宏");
        xxxResponseList.add(xxxResponse2);

        XxxResponse xxxResponse3 = new XxxResponse();
        xxxResponse3.setName("夜曲");
        xxxResponse3.setAuthor("周杰伦");
        xxxResponseList.add(xxxResponse3);

        return xxxResponseList;
    }

    @Tool(description = "根据歌曲作者名称查询歌曲信息")
    public List<XxxResponse> getMusicByAuthor(XxxRequest request) {
        log.info("进入mcp工具，方法：根据歌曲作者名称查询歌曲信息");
        XxxResponse xxxResponse = new XxxResponse();
        String word = request.getAuthor();
        List<XxxResponse> xxxResponseList = this.musicListGen();
        xxxResponseList = xxxResponseList.stream().filter(r -> r.getAuthor().equals(word)).toList();
        log.info("结束mcp工具，方法：根据歌曲作者名称查询歌曲信息");
        return xxxResponseList;
    }



    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class XxxRequest {
        @JsonProperty(required = true, value = "author")
        @JsonPropertyDescription("任意歌曲作者名称，例如：林俊杰，周杰伦，王力宏")
        private String author;
    }

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class XxxResponse {
        @JsonProperty(required = true, value = "name")
        @JsonPropertyDescription("歌曲名称")
        private String name;

        @JsonProperty(required = true, value = "author")
        @JsonPropertyDescription("歌曲作者")
        private String author;
    }

}
