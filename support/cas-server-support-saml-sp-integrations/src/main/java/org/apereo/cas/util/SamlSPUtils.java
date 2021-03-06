package org.apereo.cas.util;

import com.google.common.base.Throwables;
import org.apache.commons.lang3.StringUtils;
import org.apereo.cas.configuration.model.support.saml.sps.AbstractSamlSPProperties;
import org.apereo.cas.services.PrincipalAttributeRegisteredServiceUsernameProvider;
import org.apereo.cas.services.RegisteredService;
import org.apereo.cas.services.ReturnAllowedAttributeReleasePolicy;
import org.apereo.cas.services.ServicesManager;
import org.apereo.cas.support.saml.services.SamlRegisteredService;
import org.apereo.cas.support.saml.services.idp.metadata.cache.SamlRegisteredServiceCachingMetadataResolver;
import org.opensaml.saml.common.xml.SAMLConstants;
import org.opensaml.saml.metadata.resolver.ChainingMetadataResolver;
import org.opensaml.saml.metadata.resolver.impl.AbstractBatchMetadataResolver;
import org.opensaml.saml.saml2.metadata.EntityDescriptor;
import org.opensaml.saml.saml2.metadata.SPSSODescriptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.StreamSupport;

/**
 * This is {@link SamlSPUtils}.
 *
 * @author Misagh Moayyed
 * @since 5.0.0
 */
public final class SamlSPUtils {
    private static final Logger LOGGER = LoggerFactory.getLogger(SamlSPUtils.class);

    private SamlSPUtils() {
    }

    /**
     * New saml service provider registration.
     *
     * @param sp       the properties
     * @param resolver the resolver
     * @return the saml registered service
     */
    public static SamlRegisteredService newSamlServiceProviderService(final AbstractSamlSPProperties sp,
                                                                      final SamlRegisteredServiceCachingMetadataResolver resolver) {

        if (StringUtils.isBlank(sp.getMetadata())) {
            LOGGER.debug("Skipped registration of {} since no metadata location is found", sp.getName());
            return null;
        }

        try {
            final SamlRegisteredService service = new SamlRegisteredService();
            service.setName(sp.getName());
            service.setDescription(sp.getDescription());
            service.setEvaluationOrder(Integer.MIN_VALUE);
            service.setMetadataLocation(sp.getMetadata());

            final List<String> attributesToRelease = new ArrayList<>(sp.getAttributes());
            if (StringUtils.isNotBlank(sp.getNameIdAttribute())) {
                attributesToRelease.add(sp.getNameIdAttribute());
                service.setUsernameAttributeProvider(new PrincipalAttributeRegisteredServiceUsernameProvider(sp.getNameIdAttribute()));
            }
            service.setAttributeReleasePolicy(new ReturnAllowedAttributeReleasePolicy(attributesToRelease));
            service.setMetadataCriteriaRoles(SPSSODescriptor.DEFAULT_ELEMENT_NAME.getLocalPart());
            service.setMetadataCriteriaRemoveEmptyEntitiesDescriptors(true);
            service.setMetadataCriteriaRemoveRolelessEntityDescriptors(true);
            
            final ChainingMetadataResolver chainingResolver = resolver.resolve(service);
            if (chainingResolver.getResolvers().isEmpty()) {
                LOGGER.warn("Skipped registration of {} since no metadata resolver could be constructed", sp.getName());
                return null;
            }
            
            final List<String> builder = new ArrayList<>();
            chainingResolver.getResolvers().forEach(r -> {
                if (r instanceof AbstractBatchMetadataResolver) {
                    final Iterator<EntityDescriptor> it = ((AbstractBatchMetadataResolver) r).iterator();
                    final Optional<EntityDescriptor> descriptor =
                            StreamSupport.stream(Spliterators.spliteratorUnknownSize(it, Spliterator.ORDERED), false)
                                    .filter(e -> e.getSPSSODescriptor(SAMLConstants.SAML20P_NS) != null)
                                    .findFirst();
                    if (descriptor.isPresent()) {
                        builder.add(descriptor.get().getEntityID());
                    } else {
                        LOGGER.warn("Skipped registration of {} since no entity id could be found", sp.getName());
                    }
                }
            });
            
            if (builder.isEmpty()) {
                LOGGER.warn("Skipped registration of {} since no metadata entity ids could be found", sp.getName());
                return null;
            }
            final String entityIds = org.springframework.util.StringUtils.collectionToDelimitedString(builder, "|");
            LOGGER.debug("Registering saml service {} by entity id {}", sp.getName(), entityIds);
            service.setServiceId(entityIds);
            return service;
        } catch (final Exception e) {
            throw Throwables.propagate(e);
        }
    }

    /**
     * Save service only if it's not already found in the registry.
     *
     * @param service         the service
     * @param servicesManager the services manager
     */
    public static void saveService(final RegisteredService service, final ServicesManager servicesManager) {
        servicesManager.load();
        if (!servicesManager.matchesExistingService(service.getServiceId())) {
            LOGGER.debug("Service {} does not exist in the registry and will be added.", service.getServiceId());
            servicesManager.save(service);
            servicesManager.load();
        } else {
            LOGGER.debug("Service {} exists in the registry and will not be added again.", service.getServiceId());
        }
    }
}
