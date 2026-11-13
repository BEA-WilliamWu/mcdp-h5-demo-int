package com.ofss.digx.cz.bea.extxface.microservice.api.dsp;

import com.hkbea.dsp.client.DspSupportClient;
import com.hkbea.common.header.CommonHeader;
import com.ofss.digx.cz.bea.extxface.microservice.api.dsp.api.DspPasswordApi;
import com.ofss.digx.cz.bea.extxface.microservice.api.dsp.api.DspClientExistenceApi;
import feign.Feign;
import feign.Logger;
import feign.Request;
import feign.Retryer;
import feign.jackson.JacksonEncoder;
import feign.jaxrs.JAXRSContract;
import java.net.URI;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.util.concurrent.TimeUnit;
import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;

/** Reuses the original BCO DSP transport, including its certificate/hostname verification bypass. */
public class DspClient extends DspSupportClient {
    private volatile DspPasswordApi passwordApi;

    /** Preserve the original QTES2 TLS behavior for all DSP APIs using this client. */
    @Override
    protected OkHttpClient getOkHttpClient() {
        OkHttpClient okHttpClient = super.getOkHttpClient();
        try {
            final X509TrustManager trustAllCerts = new X509TrustManager() {
                @Override
                public void checkClientTrusted(X509Certificate[] chain, String authType) {
                }

                @Override
                public void checkServerTrusted(X509Certificate[] chain, String authType) {
                }

                @Override
                public X509Certificate[] getAcceptedIssuers() {
                    return new X509Certificate[]{};
                }
            };

            final SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, new TrustManager[]{trustAllCerts}, new SecureRandom());
            final SSLSocketFactory sslSocketFactory = sslContext.getSocketFactory();

            return okHttpClient.newBuilder()
                    .sslSocketFactory(sslSocketFactory, trustAllCerts)
                    .hostnameVerifier((hostname, session) -> true)
                    .build();
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize OkHttpClient with SSL bypass", e);
        }
    }

    /** Use the same SDK transport, APIC base URL and headers as the old client API. */
    public DspClientExistenceApi clientExistenceApi() {
        return getFeignConfig(DspClientExistenceApi.class);
    }

    /** CM-side API declaration until the provider SDK includes PasswordApi. */
    public DspPasswordApi passwordApi() {
        DspPasswordApi api = passwordApi;
        if (api == null) {
            synchronized (this) {
                api = passwordApi;
                if (api == null) {
                    api = createPasswordApi();
                    passwordApi = api;
                }
            }
        }
        return api;
    }

    private DspPasswordApi createPasswordApi() {
        String serviceUrl = required(getServiceUrl());
        URI uri = URI.create(serviceUrl);
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                || uri.getRawUserInfo() != null || uri.getRawQuery() != null
                || uri.getRawFragment() != null) {
            throw new IllegalArgumentException("Invalid DSP service URL");
        }
        CommonHeader header = getCommonHeader();
        if (header == null) {
            throw new IllegalArgumentException("Missing DSP common header");
        }
        required(header.getApicClientId());
        required(header.getApicClientSecret());
        required(header.getChannel());

        // Reuse the original DSP pool, timeouts and TLS bypass. Only password calls suppress
        // the old SDK's BODY logger and automatic retries/redirects; other APIs are unchanged.
        OkHttpClient.Builder transport = getOkHttpClient().newBuilder()
                .retryOnConnectionFailure(false).followRedirects(false).followSslRedirects(false);
        transport.interceptors().removeIf(interceptor -> interceptor instanceof HttpLoggingInterceptor);
        transport.networkInterceptors().removeIf(interceptor -> interceptor instanceof HttpLoggingInterceptor);
        OkHttpClient http = transport.build();
        return Feign.builder()
                .contract(new JAXRSContract())
                .encoder(new JacksonEncoder())
                .client(new feign.okhttp.OkHttpClient(http))
                .requestInterceptors(getHeaderInterceptors())
                .options(new Request.Options(http.connectTimeoutMillis(), TimeUnit.MILLISECONDS,
                        http.readTimeoutMillis(), TimeUnit.MILLISECONDS, false))
                .retryer(Retryer.NEVER_RETRY)
                .logger(new Logger.NoOpLogger()).logLevel(Logger.Level.NONE)
                .target(DspPasswordApi.class, serviceUrl.replaceAll("/+$", ""));
    }

    private static String required(String value) {
        if (value == null || value.trim().isEmpty() || value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0) {
            throw new IllegalArgumentException("Missing or invalid DSP configuration");
        }
        return value.trim();
    }
}
