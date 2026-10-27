package com.tcc.api_visa.security;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;

/** Resolves a host name to its addresses. An interface so tests do not depend on real DNS. */
@FunctionalInterface
public interface HostResolver {

    List<InetAddress> resolve(String host) throws UnknownHostException;

    static HostResolver system() {
        return host -> List.of(InetAddress.getAllByName(host));
    }
}
