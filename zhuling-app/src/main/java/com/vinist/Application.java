package com.vinist;

import com.vinist.domain.agent.service.matter.mcp.server.MyBizMcpService;
import com.vinist.domain.agent.service.matter.mcp.server.MyTestMcpService;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.beans.factory.annotation.Configurable;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
@Configurable
public class Application {

    public static void main(String[] args){
        SpringApplication.run(Application.class);
    }

    @Bean("myToolCallbackProvider")
    public ToolCallbackProvider testTools(MyTestMcpService testService) {
        return MethodToolCallbackProvider.builder().toolObjects(testService).build();
    }

    @Bean("myBizToolCallbackProvider")
    public ToolCallbackProvider musicTools(MyBizMcpService bizMcpService) {
        return MethodToolCallbackProvider.builder().toolObjects(bizMcpService).build();
    }

}
