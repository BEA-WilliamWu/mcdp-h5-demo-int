package com.hkbea.hth.filter;


import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.hkbea.common.exception.ParameterException;
import com.hkbea.common.header.CommonHeader;
import com.hkbea.common.toolkit.AccessTokenToolkit;
import com.hkbea.hth.entity.Management;
import com.hkbea.hth.exception.HTHSupportErrorMapping;
import com.hkbea.hth.mapper.HthManagementApiMapper;
import com.hkbea.hth.mapper.HthManagementMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Priority;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.ext.Provider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.rmi.ServerException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * HTH API entitlement validation filter.
 *
 * Validation flow:
 * 1. Verify request channel.
 * 2. Parse clientId from access token.
 * 3. the user's company is enrolled in HTH API services
 * 4. the user's company has the api functionality enabled
 */
@Component
@Provider
@Priority(Priorities.USER)
public class CoarseEntitlementFilter implements ContainerRequestFilter {

    @Autowired
    private CommonHeader headers;

    @Autowired
    private HthManagementMapper managementMapper;

    @Autowired
    private HthManagementApiMapper managementApiMapper;

    @Value("#{'${security.public-paths:}'.split(',')}")
    private List<String> publicPaths;

    private List<String> processedPublicPaths = new ArrayList<>();

    /**
     * Enabled status for HTH client configuration.
     */
    public static final String API_STATUS_ENABLE = "ENABLE";

    private static final Logger logger = LoggerFactory.getLogger(CoarseEntitlementFilter.class);

    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    /**
     * Perform entitlement validation before the request reaches
     * the target endpoint.
     *
     * @param requestContext request context
     * @throws IOException request processing exception
     */
    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {

        // Retrieve request channel and API path
        String channel = requestContext.getHeaderString(CommonHeader.CHANNEL);
        String path = requestContext.getUriInfo().getPath();

        logger.info("[Entitlement] Incoming request - Channel: {}, Path: {}", channel, path);

        // Skip entitlement validation for non-HTH requests
        if (!CommonHeader.CHANNEL_VALUE.HTH.name().equalsIgnoreCase(channel)) {
            logger.debug("[Entitlement] Skip non-HTH channel request");
            return;
        }

        if (isPublicPath(path)) {
            logger.debug("[Entitlement] Skip public path request, path={}", path);
            return;
        }

        // Retrieve access token from request header
        String accessToken = headers.getAccessToken();

//        try {
            logger.debug("[Entitlement] Start parsing access token");

            // Extract clientId from access token ,the CommonHeader has not  clientId
         String clientId="";
            try {
                clientId= AccessTokenToolkit.parseClientId(accessToken);
            }catch (Exception e){
                throw new ParameterException(HTHSupportErrorMapping.INVALID_REQUEST_PARTID_EXCEPTION);
            }
            logger.info("[Entitlement] Parsed clientId: {}", clientId);

            // Retrieve active client configuration
            Management management =
                    selectOne(clientId, API_STATUS_ENABLE);

            // Reject request if clientId is not enabled
            if (ObjectUtils.isEmpty(management)) {
                logger.error("[Entitlement] ClientId not enabled, clientId={}", clientId);

                throw new ParameterException(
                        HTHSupportErrorMapping.CLIENT_ID_NOT_ENABLED_EXCEPTION);
            }

            logger.debug("[Entitlement] Found managementId={}", management.getId());

            // BCOH2H-881: own audit logs are available to every enabled HTH company.
            // Keep token/company checks above; do not make this a public path or include operations APIs.
            if ("POST".equals(requestContext.getMethod())
                    && "/audit-log/v1".equals(withLeadingSlash(path))) {
                logger.debug("[Entitlement] Default own audit log permission granted");
                return;
            }

            // Retrieve authorized API list for the client
            List<String> uriList =
                    managementApiMapper.selectApiUriByManagementId(management.getId());

            boolean hasPermission = false;
            if (!CollectionUtils.isEmpty(uriList)) {
                String normalizedPath = withLeadingSlash(path);
                hasPermission = uriList.stream()
                        .filter(uri -> uri != null)
                        .anyMatch(uri -> matchesUri(uri, normalizedPath));
            }

            // Reject request if permission is not granted
            if (!hasPermission) {
                logger.error("[Entitlement] No permission, clientId={}, path={}",
                        clientId, path);

                throw new ParameterException(
                        HTHSupportErrorMapping.CLIENT_NOT_PERMISSIONS_EXCEPTION);
            }

            logger.info("[Entitlement] Permission granted, clientId={}, path={}",
                    clientId, path);
    }

    /**
     * Retrieve active HTH management configuration.
     *
     * Query conditions:
     * - UAM client id matches.
     * - HTH status is ENABLE.
     * - Object status is A.
     *
     * @param uamClientId client identifier
     * @param status HTH status
     * @return matching HTH management record
     */
    private Management selectOne(String uamClientId, String status) {

        LambdaQueryWrapper<Management> queryWrapper = Wrappers.lambdaQuery();

        queryWrapper.eq(Management::getUamClientId, uamClientId)
                .eq(Management::getHthStatus, status)
                .eq(Management::getObjectStatus, "A");

        return managementMapper.selectOne(queryWrapper);
    }

    /**
     * Test whether the request path matches an authorized URI (exact path or template).
     */
    private boolean matchesUri(String authorizedUri, String normalizedPath) {
        try {
            return PATH_MATCHER.match(withLeadingSlash(authorizedUri), normalizedPath);
        } catch (IllegalArgumentException e) {
            logger.warn("[Entitlement] Invalid URI pattern in authorized list: {}", authorizedUri);
            return false;
        }
    }

    @PostConstruct
    public void init() {
        if (publicPaths != null) {
            processedPublicPaths = publicPaths.stream()
                    .filter(StringUtils::hasText)
                    .map(String::trim)
                    .map(CoarseEntitlementFilter::withLeadingSlash)
                    .collect(Collectors.toList());
        }
    }

    private boolean isPublicPath(String path) {
        String normalizedPath = withLeadingSlash(path);
        return processedPublicPaths.stream()
                .anyMatch(pattern -> PATH_MATCHER.match(pattern, normalizedPath));
    }

    private static String withLeadingSlash(String value) {
        return value != null && !value.startsWith("/") ? "/" + value : value;
    }
}
