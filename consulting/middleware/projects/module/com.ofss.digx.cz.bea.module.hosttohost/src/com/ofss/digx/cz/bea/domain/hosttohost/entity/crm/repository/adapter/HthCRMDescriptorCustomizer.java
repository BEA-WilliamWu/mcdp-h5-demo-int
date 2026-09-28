package com.ofss.digx.cz.bea.domain.hosttohost.entity.crm.repository.adapter;

import org.eclipse.persistence.config.DescriptorCustomizer;
import org.eclipse.persistence.descriptors.ClassDescriptor;

/** Applies the existing five-second JDBC timeout to HTH CRM persistence only. */
public class HthCRMDescriptorCustomizer implements DescriptorCustomizer {

    @Override
    public void customize(ClassDescriptor descriptor) {
        descriptor.getQueryManager().setQueryTimeout(5);
    }
}
