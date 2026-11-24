"""Apply the cloud identity adapter to the pinned teacher source."""
from pathlib import Path
import sys


def replace(path, original, replacement):
    contents = path.read_text(encoding="utf-8")
    if contents.count(original) != 1:
        raise RuntimeError("Teacher source contract changed: " + str(path))
    path.write_text(contents.replace(original, replacement), encoding="utf-8")


source = Path(sys.argv[1])
java = source / "backend/src/main/java/cn/edu/xmu/projectmgt"
gateway = java / "infrastructure/identity/HttpExchangeUnifiedIdentityGateway.java"
client = java / "infrastructure/identity/client/UnifiedIdentityClient.java"
security_filter = java / "infrastructure/security/BearerAuthenticationFilter.java"
replace(java / "service/port/common/UnifiedIdentityGateway.java",
        "    boolean hasApiPermission(String operationId);",
        """    default List<String> effectiveAuthorities(String authorization, CurrentIdentity identity) {
        return identity.permissions();
    }

    boolean hasApiPermission(String operationId);""")
replace(client, "public interface UnifiedIdentityClient {",
        """public interface UnifiedIdentityClient {

    record EffectiveAuthorities(java.util.List<String> authorities) {}

    @GetExchange("/integration/authorities")
    EffectiveAuthorities effectiveAuthorities(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestHeader("X-Audit-Service-Key") String serviceKey);""")
for stub in (
    source / "backend/src/test/java/cn/edu/xmu/projectmgt/shared/TestUnifiedIdentityClient.java",
    source / "backend/src/mock/java/cn/edu/xmu/projectmgt/infrastructure/mock/MockUnifiedIdentityClient.java",
):
    replace(stub, "implements UnifiedIdentityClient {",
            """implements UnifiedIdentityClient {

    @Override
    public EffectiveAuthorities effectiveAuthorities(String authorization, String serviceKey) {
        return new EffectiveAuthorities(currentUser(authorization).permissions());
    }""")
replace(gateway, "    private final UnifiedIdentityClient unifiedIdentityClient;",
        """    private final UnifiedIdentityClient unifiedIdentityClient;

    @org.springframework.beans.factory.annotation.Value("${app.identity.authorities-service-key:}")
    private String authoritiesServiceKey;

    @Override
    public List<String> effectiveAuthorities(String authorization, CurrentIdentity identity) {
        if (authoritiesServiceKey == null || authoritiesServiceKey.isBlank()) throw unavailable();
        try {
            var response = unifiedIdentityClient.effectiveAuthorities(authorization, authoritiesServiceKey);
            if (response == null || response.authorities() == null) throw unavailable();
            return response.authorities();
        } catch (HttpClientErrorException exception) {
            if (exception.getStatusCode().value() == 401) throw translate(exception);
            throw unavailable();
        } catch (RestClientException exception) {
            throw unavailable();
        }
    }""")
replace(security_filter,
        "        CurrentIdentity identity;\n        try {\n            identity = identityGateway.currentIdentity();",
        """        CurrentIdentity identity;
        List<String> effectiveAuthorities;
        try {
            identity = identityGateway.currentIdentity();
            effectiveAuthorities = identityGateway.effectiveAuthorities(authorization, identity);""")
replace(security_filter,
        "List<SimpleGrantedAuthority> authorities = Objects.requireNonNullElse(identity.permissions(), List.<String>of()).stream()",
        "List<SimpleGrantedAuthority> authorities = Objects.requireNonNullElse(effectiveAuthorities, List.<String>of()).stream()")
replace(java / "infrastructure/audit/client/AuditHttpClientConfiguration.java",
        "            String baseUrl\n    ) {\n        RestClient restClient = restClientFactory.create(baseUrl);",
        """            String baseUrl,
            @Value("${app.identity.authorities-service-key:}")
            String serviceKey
    ) {
        RestClient restClient = restClientFactory.create(baseUrl);
        if (!serviceKey.isBlank()) {
            restClient = restClient.mutate().defaultHeader("X-Audit-Service-Key", serviceKey).build();
        }""")
replace(source / "web/src/router.ts", "history: createWebHistory(),",
        "history: createWebHistory(import.meta.env.BASE_URL),")
