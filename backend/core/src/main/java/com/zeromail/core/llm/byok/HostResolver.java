package com.zeromail.core.llm.byok;

import java.net.InetAddress;
import java.net.UnknownHostException;
import org.springframework.stereotype.Component;

@Component
public class HostResolver {

    public InetAddress[] resolve(String host) throws UnknownHostException {
        return InetAddress.getAllByName(host);
    }
}
