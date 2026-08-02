package com.example.payment.domain.model;

/**
 * The outcome of an authorization attempt, in Payment's vocabulary.
 *
 * <p>Note this matches the wire enum names Order's
 * {@code PaymentRestClient.Outcome} expects. That alignment is the
 * Module 5 distinction: Payment is closely-coupled-by-vocabulary with
 * Order, which is why it earns only a thin client adapter rather than a
 * full ACL. An ACL would be busywork.
 */
public enum AuthorizationOutcome {
    AUTHORIZED,
    DECLINED
}
