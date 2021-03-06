package org.apereo.cas.support.events.listener;

import org.apereo.cas.authentication.adaptive.geo.GeoLocationRequest;
import org.apereo.cas.support.events.CasRiskyAuthenticationDetectedEvent;
import org.apereo.cas.support.events.CasTicketGrantingTicketCreatedEvent;
import org.apereo.cas.support.events.dao.CasEvent;
import org.apereo.cas.support.events.dao.CasEventRepository;
import org.apereo.cas.util.DateTimeUtils;
import org.apereo.cas.util.serialization.TicketIdSanitizationUtils;
import org.apereo.cas.web.support.WebUtils;
import org.apereo.inspektr.common.web.ClientInfo;
import org.apereo.inspektr.common.web.ClientInfoHolder;
import org.springframework.context.event.EventListener;

/**
 * This is {@link DefaultCasEventListener} that attempts to consume CAS events
 * upon various authentication events. Event data is persisted into a repository
 * via {@link CasEventRepository}.
 *
 * @author Misagh Moayyed
 * @since 5.0.0
 */
public class DefaultCasEventListener {

    private final CasEventRepository casEventRepository;

    public DefaultCasEventListener(final CasEventRepository casEventRepository) {
        this.casEventRepository = casEventRepository;
    }

    /**
     * Handle TGT creation event.
     *
     * @param event the event
     */
    @EventListener
    public void handleCasTicketGrantingTicketCreatedEvent(final CasTicketGrantingTicketCreatedEvent event) {
        if (this.casEventRepository != null) {

            final CasEvent dto = new CasEvent();
            dto.setType(event.getClass().getCanonicalName());
            dto.putTimestamp(event.getTimestamp());
            dto.putCreationTime(event.getTicketGrantingTicket().getCreationTime());
            dto.putId(TicketIdSanitizationUtils.sanitize(event.getTicketGrantingTicket().getId()));
            dto.setPrincipalId(event.getTicketGrantingTicket().getAuthentication().getPrincipal().getId());

            final ClientInfo clientInfo = ClientInfoHolder.getClientInfo();
            dto.putClientIpAddress(clientInfo.getClientIpAddress());
            dto.putServerIpAddress(clientInfo.getServerIpAddress());
            dto.putAgent(WebUtils.getHttpServletRequestUserAgent());

            final GeoLocationRequest location = WebUtils.getHttpServletRequestGeoLocation();
            dto.putGeoLocation(location);

            this.casEventRepository.save(dto);
        }
    }

    /**
     * Handle cas risky authentication detected event.
     *
     * @param event the event
     */
    @EventListener
    public void handleCasRiskyAuthenticationDetectedEvent(final CasRiskyAuthenticationDetectedEvent event) {
        if (this.casEventRepository != null) {

            final CasEvent dto = new CasEvent();
            dto.setType(event.getClass().getCanonicalName());
            dto.putTimestamp(event.getTimestamp());
            dto.putCreationTime(DateTimeUtils.zonedDateTimeOf(event.getTimestamp()));
            dto.putId(event.getService().getName());
            dto.setPrincipalId(event.getAuthentication().getPrincipal().getId());

            final ClientInfo clientInfo = ClientInfoHolder.getClientInfo();
            dto.putClientIpAddress(clientInfo.getClientIpAddress());
            dto.putServerIpAddress(clientInfo.getServerIpAddress());
            dto.putAgent(WebUtils.getHttpServletRequestUserAgent());

            final GeoLocationRequest location = WebUtils.getHttpServletRequestGeoLocation();
            dto.putGeoLocation(location);

            this.casEventRepository.save(dto);
        }
    }
    
    public CasEventRepository getCasEventRepository() {
        return casEventRepository;
    }
}
