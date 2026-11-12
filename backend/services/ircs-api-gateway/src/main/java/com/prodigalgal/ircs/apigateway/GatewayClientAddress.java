package com.prodigalgal.ircs.apigateway;

import jakarta.servlet.http.HttpServletRequest;
import java.net.InetAddress;
import java.util.Arrays;

/** Forwarded addresses are used only at an explicitly configured and network-isolated proxy boundary. */
final class GatewayClientAddress {
    private GatewayClientAddress() { }

    static String resolve(HttpServletRequest request, String trustedProxyCidrs) {
        String remote = request.getRemoteAddr();
        if (trustedProxyCidrs == null || Arrays.stream(trustedProxyCidrs.split(","))
                .noneMatch(cidr -> contains(cidr.trim(), remote))) return remote;
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded == null) return remote;
        // Envoy appends its directly observed downstream address; client-supplied entries precede it.
        String[] chain = forwarded.split(",");
        String candidate = chain[chain.length - 1].trim();
        return literal(candidate) ? candidate : remote;
    }

    private static boolean contains(String cidr, String address) {
        try {
            String[] parts = cidr.split("/", -1);
            if (!literal(parts[0]) || !literal(address)) return false;
            byte[] network = InetAddress.getByName(parts[0]).getAddress();
            byte[] peer = InetAddress.getByName(address).getAddress();
            int bits = parts.length == 1 ? network.length * 8 : Integer.parseInt(parts[1]);
            if (parts.length > 2 || network.length != peer.length || bits < 0 || bits > network.length * 8) return false;
            for (int index = 0; index < network.length; index++) {
                int mask = index * 8 >= bits ? 0 : 0xff << (8 - Math.min(8, bits - index * 8));
                if ((network[index] & mask) != (peer[index] & mask)) return false;
            }
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    private static boolean literal(String value) {
        return value != null && !value.isBlank() && (value.matches("[0-9.]+") || value.matches("[0-9a-fA-F:]+"));
    }
}
