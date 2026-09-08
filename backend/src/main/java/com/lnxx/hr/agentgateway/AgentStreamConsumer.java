package com.lnxx.hr.agentgateway;

import java.io.IOException;

@FunctionalInterface
public interface AgentStreamConsumer {
    void accept(AgentStreamEvent event) throws IOException;
}
