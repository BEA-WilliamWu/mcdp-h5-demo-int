package com.ofss.digx.cz.bea.extxface.microservice.api.dsp.factory;

import com.hkbea.common.header.CommonHeader;
import com.ofss.digx.cz.bea.extxface.microservice.api.dsp.DspClient;
import com.ofss.digx.cz.bea.extxface.microservice.api.dsp.impl.DSPCommonHeaderImpl;
import com.ofss.fc.infra.config.ConfigurationFactory;

import java.util.prefs.Preferences;

public class DSPApiClientFactory {

    // Reuse the released INQ01 internal APIC base, subscription and channel.
    private static final String CONFIG_CATEGORY = "H2HApiAdapterImplConfig";

    private static final String PROP_SERVICE_URL = "H2H.SERVICE_URL";
    private static final String PROP_APIC_CLIENT_ID = "H2H.CLIENT_ID";
    private static final String PROP_APIC_CLIENT_SECRET = "H2H.CLIENT_SECRET";
    private static final String PROP_CHANNEL = "H2H.CHANNEL";

    private static final String DEFAULT_CHANNEL = "BCO";
    private static final String EMPTY_STR = "";

    private static final Object LOCK = new Object();

    private static volatile DspClient dspClient;

    private DSPApiClientFactory() {
    }

    public static DspClient getDspClient() {
        DspClient client = dspClient;

        if (client == null) {
            synchronized (LOCK) {
                client = dspClient;

                if (client == null) {
                    client = createDspClient();
                    dspClient = client;
                }
            }
        }

        return client;
    }

    private static DspClient createDspClient() {
        Preferences preferences = ConfigurationFactory.getInstance()
                .getConfigurations(CONFIG_CATEGORY);

        String serviceUrl = preferences.get(PROP_SERVICE_URL, EMPTY_STR);
        String apicClientId = preferences.get(PROP_APIC_CLIENT_ID, EMPTY_STR);
        String apicClientSecret = preferences.get(PROP_APIC_CLIENT_SECRET, EMPTY_STR);
        String channel = preferences.get(PROP_CHANNEL, DEFAULT_CHANNEL);

        CommonHeader commonHeader = new DSPCommonHeaderImpl();
        commonHeader.setApicClientId(apicClientId);
        commonHeader.setApicClientSecret(apicClientSecret);
        commonHeader.setChannel(channel);

        DspClient client = new DspClient();
        client.setServiceUrl(serviceUrl);
        client.setCommonHeader(commonHeader);

        return client;
    }
}
