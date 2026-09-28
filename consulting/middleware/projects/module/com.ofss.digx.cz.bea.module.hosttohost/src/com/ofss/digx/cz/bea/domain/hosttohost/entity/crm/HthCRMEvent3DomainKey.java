package com.ofss.digx.cz.bea.domain.hosttohost.entity.crm;

import com.ofss.fc.framework.domain.AbstractDomainObjectKey;

/** Primary key of an HTH CRM event, using the same domain key contract as BCO. */
public class HthCRMEvent3DomainKey extends AbstractDomainObjectKey {
    private static final long serialVersionUID = 1L;

    private String eventId;

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    @Override
    public String keyAsString() {
        return eventId;
    }
}
