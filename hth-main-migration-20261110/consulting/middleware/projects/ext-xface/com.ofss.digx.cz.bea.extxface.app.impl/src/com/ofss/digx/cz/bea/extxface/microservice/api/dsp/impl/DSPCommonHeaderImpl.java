package com.ofss.digx.cz.bea.extxface.microservice.api.dsp.impl;

import com.hkbea.common.header.CommonHeader;
import com.ofss.digx.cz.bea.extxface.microservice.api.dsp.constant.DSPHeader;
import com.ofss.fc.infra.thread.ThreadAttribute;

import java.util.UUID;

public class DSPCommonHeaderImpl implements CommonHeader {

    private String apicClientId = null;
    private String apicClientSecret = null;
    private String channel = null;

    @Override
    public String getChannel() {
        return this.channel;
    }

    @Override
    public String setChannel(String channel) {
        this.channel = channel;
        return this.channel;
    }

    @Override
    public String getApicClientId() {
        return this.apicClientId;
    }

    @Override
    public String setApicClientId(String apicClientId) {
        this.apicClientId = apicClientId;
        return this.apicClientId;
    }

    @Override
    public String getApicClientSecret() {
        return this.apicClientSecret;
    }

    @Override
    public String setApicClientSecret(String apicClientSecret) {
        this.apicClientSecret = apicClientSecret;
        return this.apicClientSecret;
    }

    @Override
    public String getAccessToken() {
        return (String) ThreadAttribute.get(DSPHeader.ACCESS_TOKEN);
    }

    @Override
    public String setAccessToken(String token) {
        ThreadAttribute.set(DSPHeader.ACCESS_TOKEN, token);
        return token;
    }

    @Override
    public String getMessageId() {
        String messageId = (String) ThreadAttribute.get(DSPHeader.MESSAGE_ID);
        if (messageId == null) {
            messageId = UUID.randomUUID().toString();
            ThreadAttribute.set(DSPHeader.MESSAGE_ID, messageId);
        }
        return messageId;
    }

    @Override
    public String setMessageId(String messageId) {
        ThreadAttribute.set(DSPHeader.MESSAGE_ID, messageId);
        return messageId;
    }

    @Override
    public void reset() {
        ThreadAttribute.set(DSPHeader.ACCESS_TOKEN, null);
        ThreadAttribute.set(DSPHeader.MESSAGE_ID, null);
    }
}