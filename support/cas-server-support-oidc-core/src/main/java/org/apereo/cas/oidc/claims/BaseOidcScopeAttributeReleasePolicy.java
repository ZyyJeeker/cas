package org.apereo.cas.oidc.claims;

import org.apereo.cas.configuration.CasConfigurationProperties;
import org.apereo.cas.oidc.claims.mapping.OidcAttributeToScopeClaimMapper;
import org.apereo.cas.services.AbstractRegisteredServiceAttributeReleasePolicy;
import org.apereo.cas.services.RegisteredServiceAttributeReleasePolicyContext;
import org.apereo.cas.util.CollectionUtils;
import org.apereo.cas.util.spring.ApplicationContextProvider;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.Maps;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.apache.commons.lang3.tuple.Pair;

import java.io.Serial;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

/**
 * This is {@link BaseOidcScopeAttributeReleasePolicy}.
 *
 * @author Misagh Moayyed
 * @since 5.1.0
 */
@Slf4j
@ToString(callSuper = true)
@Getter
@EqualsAndHashCode(callSuper = true)
@Setter
@JsonInclude(JsonInclude.Include.NON_DEFAULT)
public abstract class BaseOidcScopeAttributeReleasePolicy extends AbstractRegisteredServiceAttributeReleasePolicy
    implements OidcRegisteredServiceAttributeReleasePolicy {

    @Serial
    private static final long serialVersionUID = -7302163334687300920L;

    @JsonProperty
    private List<String> allowedAttributes;

    @JsonProperty("claimMappings")
    private Map<String, String> claimMappings = new TreeMap<>();

    @JsonIgnore
    private String scopeType;

    protected BaseOidcScopeAttributeReleasePolicy(final String scopeType) {
        this.scopeType = scopeType;
    }

    protected Optional<String> getMappedClaim(final String claim,
                                              final RegisteredServiceAttributeReleasePolicyContext context) {
        val applicationContext = ApplicationContextProvider.getApplicationContext();
        val mapper = applicationContext.getBean(OidcAttributeToScopeClaimMapper.DEFAULT_BEAN_NAME,
            OidcAttributeToScopeClaimMapper.class);
        LOGGER.debug("Attempting to process claim [{}]", claim);
        return mapper.containsMappedAttribute(claim, context.getRegisteredService())
            ? Optional.of(mapper.getMappedAttribute(claim, context.getRegisteredService()))
            : Optional.empty();
    }

    protected Pair<String, Object> mapClaimToAttribute(final String claim,
                                                       final RegisteredServiceAttributeReleasePolicyContext context,
                                                       final Map<String, List<Object>> resolvedAttributes) {
        val mappedClaimResult = getMappedClaim(claim, context);
        if (mappedClaimResult.isPresent()) {
            val mappedAttr = mappedClaimResult.get();
            LOGGER.trace("Attribute [{}] is mapped to claim [{}]", mappedAttr, claim);

            if (resolvedAttributes.containsKey(mappedAttr)) {
                val value = resolvedAttributes.get(mappedAttr);
                LOGGER.debug("Found mapped attribute [{}] with value [{}] for claim [{}]", mappedAttr, value, claim);
                return Pair.of(claim, value);
            }
            if (resolvedAttributes.containsKey(claim)) {
                val value = resolvedAttributes.get(claim);
                LOGGER.debug("CAS is unable to find the attribute [{}] that is mapped to claim [{}]. "
                             + "However, since resolved attributes [{}] already contain this claim, "
                             + "CAS will use [{}] with value(s) [{}]",
                    mappedAttr, claim, resolvedAttributes, claim, value);
                return Pair.of(claim, value);
            }
            LOGGER.warn("Located claim [{}] mapped to attribute [{}], yet "
                        + "resolved attributes [{}] do not contain attribute [{}]",
                claim, mappedAttr, resolvedAttributes, mappedAttr);
        }

        val value = resolvedAttributes.get(claim);
        LOGGER.debug("No mapped attribute is defined for claim [{}]; Used [{}] to locate value [{}]", claim, claim, value);
        return Pair.of(claim, value);
    }

    @Override
    public Map<String, List<Object>> getAttributesInternal(final RegisteredServiceAttributeReleasePolicyContext context,
                                                           final Map<String, List<Object>> attributes) {
        val applicationContext = ApplicationContextProvider.getApplicationContext();
        if (applicationContext == null) {
            LOGGER.warn("Could not locate the application context to process attributes");
            return new HashMap<>(0);
        }
        val resolvedAttributes = new TreeMap<String, List<Object>>(String.CASE_INSENSITIVE_ORDER);
        resolvedAttributes.putAll(attributes);

        val attributesToRelease = Maps.<String, List<Object>>newHashMapWithExpectedSize(attributes.size());
        LOGGER.debug("Attempting to map and filter claims based on resolved attributes [{}]", resolvedAttributes);

        val properties = applicationContext.getBean(CasConfigurationProperties.class);
        val supportedClaims = properties.getAuthn().getOidc().getDiscovery().getClaims();

        val allowedClaims = new LinkedHashSet<>(getAllowedAttributes());
        allowedClaims.retainAll(supportedClaims);
        LOGGER.debug("[{}] is designed to allow claims [{}] for scope [{}]. After cross-checking with "
                     + "supported claims [{}], the final collection of allowed attributes is [{}]",
            getClass().getSimpleName(), getAllowedAttributes(), getScopeType(), supportedClaims, allowedClaims);

        allowedClaims
            .stream()
            .map(claim -> mapClaimToAttribute(claim, context, resolvedAttributes))
            .filter(p -> Objects.nonNull(p.getValue()))
            .forEach(p -> attributesToRelease.put(p.getKey(), CollectionUtils.toCollection(p.getValue(), ArrayList.class)));
        return attributesToRelease;
    }

    @Override
    public List<String> determineRequestedAttributeDefinitions(final RegisteredServiceAttributeReleasePolicyContext context) {
        val attributes = getAllowedAttributes();
        return attributes != null ? attributes : new ArrayList<>();
    }
}
