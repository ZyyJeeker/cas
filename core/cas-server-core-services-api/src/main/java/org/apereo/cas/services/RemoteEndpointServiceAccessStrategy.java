package org.apereo.cas.services;

import org.apereo.cas.util.CollectionUtils;
import org.apereo.cas.util.HttpUtils;
import org.apereo.cas.util.serialization.JacksonObjectMapperFactory;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.Accessors;
import lombok.val;
import org.jooq.lambda.Unchecked;
import org.springframework.http.HttpMethod;
import org.springframework.util.StringUtils;

import java.io.Serial;

/**
 * This is {@link RemoteEndpointServiceAccessStrategy} that reaches out
 * to a remote endpoint, passing the CAS principal id to determine if access is allowed.
 * If the status code returned in the final response is not accepted by the policy here,
 * access shall be denied.
 *
 * @author Misagh Moayyed
 * @since 5.0.0
 */
@ToString(callSuper = true)
@Getter
@Setter
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@Accessors(chain = true)
@JsonInclude(JsonInclude.Include.NON_DEFAULT)
public class RemoteEndpointServiceAccessStrategy extends BaseRegisteredServiceAccessStrategy {
    private static final ObjectMapper MAPPER = JacksonObjectMapperFactory.builder()
        .defaultTypingEnabled(true).build().toObjectMapper();

    @Serial
    private static final long serialVersionUID = -1108201604115278440L;

    private String endpointUrl;

    private String acceptableResponseCodes;

    @Override
    public boolean doPrincipalAttributesAllowServiceAccess(final RegisteredServiceAccessStrategyRequest request) {
        return Unchecked.supplier(() -> {
            val exec = HttpUtils.HttpExecutionRequest.builder()
                .method(HttpMethod.GET)
                .url(this.endpointUrl)
                .parameters(CollectionUtils.wrap("username", request.getPrincipalId()))
                .entity(MAPPER.writeValueAsString(request))
                .build();
            val response = HttpUtils.execute(exec);
            val currentCodes = StringUtils.commaDelimitedListToSet(this.acceptableResponseCodes);
            return response != null && currentCodes.contains(String.valueOf(response.getStatusLine().getStatusCode()));
        }).get();
    }

}
