package com.example.notification.domain.model;

/**
 * The kinds of notifications Notification sends. Used as metric labels
 * and span attributes - the bounded set means cardinality stays sane.
 */
public enum NotificationKind {

    /** Acknowledgment that an order was received and is being processed. */
    PLACED_ACK,

    /** Confirmation that an order completed all saga steps. */
    CONFIRMATION,

    /** Apology that an order was cancelled. */
    CANCELLATION
}
