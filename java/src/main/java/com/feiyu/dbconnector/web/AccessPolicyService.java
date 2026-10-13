package com.feiyu.dbconnector.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.feiyu.dbconnector.common.BizException;
import com.feiyu.dbconnector.common.ErrorCode;
import io.micronaut.http.HttpRequest;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Singleton;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Singleton
public class AccessPolicyService {

    private static final List<String> DEFAULT_ALLOWED = List.of("127.0.0.1", "::1");

    private final ObjectMapper objectMapper;
    private final Path policyFile;
    private volatile List<Rule> rules = List.of(new Rule(parseAddress("127.0.0.1"), 32),
            new Rule(parseAddress("::1"), 128));
    private volatile List<String> entries = DEFAULT_ALLOWED;

    public AccessPolicyService(ObjectMapper objectMapper) {
        this(objectMapper, defaultDataDir());
    }

    public AccessPolicyService(ObjectMapper objectMapper, Path dataDir) {
        this.objectMapper = objectMapper;
        this.policyFile = dataDir.resolve("access-policy.json");
    }

    private static Path defaultDataDir() {
        String dataDir = System.getenv("DBCONNECTOR_DATA_DIR");
        if (dataDir == null || dataDir.isBlank()) {
            return Path.of(System.getProperty("user.home"), ".db-connector-mcp", "data");
        }
        return Path.of(dataDir);
    }

    @PostConstruct
    public void load() {
        if (!Files.exists(policyFile)) {
            return;
        }
        try {
            JsonNode root = objectMapper.readTree(Files.readString(policyFile));
            JsonNode allowed = root == null ? null : root.get("allowed");
            if (allowed == null || !allowed.isArray()) {
                return;
            }
            List<String> loaded = new ArrayList<>();
            allowed.forEach(node -> loaded.add(node.asText()));
            apply(loaded, false);
        } catch (Exception e) {
            System.err.println("[access-policy] Invalid policy file; using localhost-only access: " + e.getMessage());
        }
    }

    public List<String> getEntries() {
        return entries;
    }

    public synchronized List<String> replace(List<String> requested) {
        List<String> normalized = normalize(requested);
        apply(normalized, true);
        return entries;
    }

    public boolean isAllowed(HttpRequest<?> request) {
        InetSocketAddress remoteAddress = request.getRemoteAddress();
        InetAddress address = remoteAddress == null ? null : remoteAddress.getAddress();
        if (address == null) {
            return false;
        }
        for (Rule rule : rules) {
            if (rule.matches(address)) {
                return true;
            }
        }
        return false;
    }

    public String policyFilePath() {
        return policyFile.toString();
    }

    private synchronized void apply(List<String> requested, boolean persist) {
        List<String> normalized = normalize(requested);
        List<Rule> parsed = normalized.stream().map(this::parseRule).toList();
        entries = List.copyOf(normalized);
        rules = List.copyOf(parsed);
        if (persist) {
            writePolicy(normalized);
        }
    }

    private List<String> normalize(List<String> requested) {
        List<String> normalized = new ArrayList<>();
        for (String raw : DEFAULT_ALLOWED) {
            normalized.add(raw);
        }
        for (String raw : requested == null ? List.<String>of() : requested) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            String entry = raw.trim();
            parseRule(entry);
            if (!normalized.contains(entry)) {
                normalized.add(entry);
            }
        }
        return normalized;
    }

    private Rule parseRule(String value) {
        String addressText = value;
        Integer prefix = null;
        int slash = value.indexOf('/');
        if (slash >= 0) {
            if (value.indexOf('/', slash + 1) >= 0) {
                throw invalid(value);
            }
            addressText = value.substring(0, slash).trim();
            try {
                prefix = Integer.parseInt(value.substring(slash + 1).trim());
            } catch (NumberFormatException e) {
                throw invalid(value);
            }
        }
        InetAddress address = parseAddress(addressText);
        int maxPrefix = address instanceof Inet4Address ? 32 : 128;
        if (prefix == null) {
            prefix = maxPrefix;
        }
        if (prefix < 0 || prefix > maxPrefix) {
            throw invalid(value);
        }
        return new Rule(networkAddress(address, prefix), prefix);
    }

    private static InetAddress parseAddress(String value) {
        if (value == null || value.isBlank()) {
            throw invalid(value);
        }

        // InetAddress.getByName also resolves host names. The access policy is
        // intentionally IP/CIDR-only, so parse dotted IPv4 literals ourselves
        // and only delegate colon-containing IPv6 literals to the JDK parser.
        if (!value.contains(":")) {
            if (!value.matches("(?:[0-9]{1,3}\\.){3}[0-9]{1,3}")) {
                throw invalid(value);
            }
            String[] octets = value.split("\\.", -1);
            byte[] bytes = new byte[4];
            try {
                for (int i = 0; i < octets.length; i++) {
                    int octet = Integer.parseInt(octets[i]);
                    if (octet < 0 || octet > 255) {
                        throw invalid(value);
                    }
                    bytes[i] = (byte) octet;
                }
                return InetAddress.getByAddress(bytes);
            } catch (NumberFormatException | IOException e) {
                throw invalid(value);
            }
        }

        if (!value.matches("[0-9a-fA-F:.]+")) {
            throw invalid(value);
        }
        try {
            InetAddress address = InetAddress.getByName(value);
            if (!(address instanceof Inet4Address) && !(address instanceof Inet6Address)) {
                throw invalid(value);
            }
            return address;
        } catch (IOException e) {
            throw invalid(value);
        }
    }

    private static InetAddress networkAddress(InetAddress address, int prefix) {
        byte[] bytes = address.getAddress();
        int fullBytes = prefix / 8;
        int remainingBits = prefix % 8;
        for (int i = fullBytes + (remainingBits > 0 ? 1 : 0); i < bytes.length; i++) {
            bytes[i] = 0;
        }
        if (remainingBits > 0) {
            bytes[fullBytes] = (byte) (bytes[fullBytes] & (0xff << (8 - remainingBits)));
        }
        try {
            return InetAddress.getByAddress(bytes);
        } catch (IOException e) {
            throw new IllegalArgumentException("Invalid network address", e);
        }
    }

    private void writePolicy(List<String> allowed) {
        try {
            Files.createDirectories(policyFile.getParent());
            Map<String, Object> root = new LinkedHashMap<>();
            root.put("allowed", allowed);
            Path temp = policyFile.resolveSibling(policyFile.getFileName() + ".tmp");
            Files.writeString(temp, objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(root));
            try {
                Files.move(temp, policyFile, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, policyFile, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new BizException(ErrorCode.INTERNAL_ERROR, "保存访问白名单失败: " + e.getMessage());
        }
    }

    private static BizException invalid(String value) {
        return new BizException(ErrorCode.VALIDATION_ERROR,
                "无效的 IP 或 CIDR 网段: " + (value == null ? "" : value));
    }

    private record Rule(InetAddress network, int prefix) {
        boolean matches(InetAddress candidate) {
            byte[] networkBytes = network.getAddress();
            byte[] candidateBytes = candidate.getAddress();
            if (networkBytes.length != candidateBytes.length) {
                return false;
            }
            int fullBytes = prefix / 8;
            int remainingBits = prefix % 8;
            for (int i = 0; i < fullBytes; i++) {
                if (networkBytes[i] != candidateBytes[i]) {
                    return false;
                }
            }
            return remainingBits == 0
                    || (networkBytes[fullBytes] & (0xff << (8 - remainingBits)))
                    == (candidateBytes[fullBytes] & (0xff << (8 - remainingBits)));
        }

    }
}
