package com.kafka.signaling.signaling;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * One signaling message relayed between the two peers of a 1:1 call.
 *
 * <p>{@code type} drives the client call state machine:
 * INVITE / ACCEPT / REJECT / CANCEL / OFFER / ANSWER / ICE / HANGUP.
 * {@code data} carries the opaque WebRTC payload (SDP for OFFER/ANSWER, an ICE
 * candidate for ICE) — the server never inspects it, it only forwards.
 *
 * <p>{@code fromEmail} is always overwritten server-side from the authenticated
 * Principal, so a client cannot spoof who a signal came from. {@code fromName} and
 * {@code fromImage} are display-only hints the caller includes on INVITE.
 */
public record CallSignal(
        String type,
        String toEmail,
        String fromEmail,
        String fromName,
        String fromImage,
        String roomId,
        Boolean video,
        JsonNode data
) {
    public CallSignal withFrom(String email) {
        return new CallSignal(type, toEmail, email, fromName, fromImage, roomId, video, data);
    }
}
