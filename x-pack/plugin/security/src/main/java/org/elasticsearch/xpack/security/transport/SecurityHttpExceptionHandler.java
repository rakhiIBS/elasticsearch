/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License;
 * you may not use this file except in compliance with the Elastic License.
 */
package org.elasticsearch.xpack.security.transport;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.message.ParameterizedMessage;
import org.elasticsearch.common.component.Lifecycle;
import org.elasticsearch.common.network.CloseableChannel;
import org.elasticsearch.http.HttpChannel;

import java.util.function.BiConsumer;

import static org.elasticsearch.xpack.core.security.transport.SSLExceptionHelper.isCloseDuringHandshakeException;
import static org.elasticsearch.xpack.core.security.transport.SSLExceptionHelper.isNotSslRecordException;
import static org.elasticsearch.xpack.core.security.transport.SSLExceptionHelper.isReceivedCertificateUnknownException;

public final class SecurityHttpExceptionHandler implements BiConsumer<HttpChannel, Exception> {

    private final Lifecycle lifecycle;
    private final Logger logger;
    private final BiConsumer<HttpChannel, Exception> fallback;

    public SecurityHttpExceptionHandler(Logger logger, Lifecycle lifecycle, BiConsumer<HttpChannel, Exception> fallback) {
        this.lifecycle = lifecycle;
        this.logger = logger;
        this.fallback = fallback;
    }

    public void accept(HttpChannel channel, Exception e) {
        if (!lifecycle.started()) {
            return;
        }

        if (isNotSslRecordException(e)) {
            if (logger.isTraceEnabled()) {
                logger.trace(new ParameterizedMessage("received plaintext http traffic on a https channel, closing connection {}",
                    channel), e);
            } else {
                logger.warn("received plaintext http traffic on a https channel, closing connection {}", channel);
            }
            CloseableChannel.closeChannel(channel);
        } else if (isCloseDuringHandshakeException(e)) {
            if (logger.isTraceEnabled()) {
                logger.trace(new ParameterizedMessage("connection {} closed during ssl handshake", channel), e);
            } else {
                logger.warn("connection {} closed during ssl handshake", channel);
            }
            CloseableChannel.closeChannel(channel);
        } else if (isReceivedCertificateUnknownException(e)) {
            if (logger.isTraceEnabled()) {
                logger.trace(new ParameterizedMessage("http client did not trust server's certificate, closing connection {}",
                    channel), e);
            } else {
                logger.warn("http client did not trust this server's certificate, closing connection {}", channel);
            }
            CloseableChannel.closeChannel(channel);
        } else {
            fallback.accept(channel, e);
        }
    }
}
