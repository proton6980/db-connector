package com.feiyu.dbconnector;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.feiyu.dbconnector.common.BizException;
import com.feiyu.dbconnector.web.AccessPolicyService;
import io.micronaut.http.HttpRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AccessPolicyServiceTest {

    @TempDir
    Path tempDir;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void defaultsToLocalhostAndPersistsIpAndCidrRules() throws Exception {
        AccessPolicyService service = new AccessPolicyService(objectMapper, tempDir);

        assertEquals(List.of("127.0.0.1", "::1"), service.getEntries());

        service.replace(List.of("192.168.1.20", "192.168.1.0/24", "2001:db8::/32"));

        assertEquals(List.of("127.0.0.1", "::1", "192.168.1.20", "192.168.1.0/24", "2001:db8::/32"),
                service.getEntries());
        assertTrue(Files.exists(tempDir.resolve("access-policy.json")));

        AccessPolicyService reloaded = new AccessPolicyService(objectMapper, tempDir);
        reloaded.load();
        assertEquals(service.getEntries(), reloaded.getEntries());
    }

    @Test
    void matchesIpAndCidrRulesButRejectsOutsideAddresses() throws Exception {
        AccessPolicyService service = new AccessPolicyService(objectMapper, tempDir);
        service.replace(List.of("192.168.1.0/24", "2001:db8::/32"));

        assertTrue(service.isAllowed(requestFrom("127.0.0.1")));
        assertTrue(service.isAllowed(requestFrom("192.168.1.55")));
        assertTrue(service.isAllowed(requestFrom("2001:db8::10")));
        assertFalse(service.isAllowed(requestFrom("192.168.2.10")));
    }

    @Test
    void rejectsInvalidRulesWithoutChangingCurrentPolicy() {
        AccessPolicyService service = new AccessPolicyService(objectMapper, tempDir);
        List<String> initial = service.getEntries();

        assertThrows(BizException.class, () -> service.replace(List.of("192.168.1.0/33")));
        assertEquals(initial, service.getEntries());
    }

    @Test
    void rejectsHostNamesInsteadOfResolvingThem() {
        AccessPolicyService service = new AccessPolicyService(objectMapper, tempDir);

        assertThrows(BizException.class, () -> service.replace(List.of("localhost")));
        assertEquals(List.of("127.0.0.1", "::1"), service.getEntries());
    }

    private static HttpRequest<?> requestFrom(String address) {
        String host = address.contains(":") ? "[" + address + "]" : address;
        return HttpRequest.GET("http://" + host + "/");
    }
}
