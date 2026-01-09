# API Webhooks Documentation

## Overview

Webhooks allow you to receive real-time notifications about events in our system. When an event occurs, we send an HTTP POST request to your configured webhook URL.

## ## Authentication

All webhook requests are authenticated using a signature in the `X-Webhook-Signature` header. Verify this signature to ensure the request is from our system.

### Signature Verification

The signature is computed using HMAC-SHA256:

```
signature = HMAC-SHA256(webhook_secret, request_body)
```

Compare the computed signature with the value in the `X-Webhook-Signature` header.

## ## Webhook Events

### payment.completed

Triggered when a payment is successfully completed.

**Payload:**
```json
{
  "event": "payment.completed",
  "data": {
    "orderId": "ORD-12345",
    "amount": 29.99,
    "currency": "USD",
    "timestamp": "2024-01-15T10:30:00Z"
  }
}
```

### subscription.created

Triggered when a new subscription is created.

**Payload:**
```json
{
  "event": "subscription.created",
  "data": {
    "subscriptionId": "SUB-67890",
    "planName": "Premium",
    "customerEmail": "user@example.com"
  }
}
```

### subscription.cancelled

Triggered when a subscription is cancelled.

**Payload:**
```json
{
  "event": "subscription.cancelled",
  "data": {
    "subscriptionId": "SUB-67890",
    "reason": "user_request",
    "effectiveDate": "2024-02-15"
  }
}
```

## ## Webhook Configuration

### Setting Up Webhooks

1. Log in to your account dashboard
2. Navigate to Settings > Webhooks
3. Add a new webhook URL
4. Select the events you want to receive
5. Save your webhook secret for signature verification

### Retry Policy

If a webhook delivery fails (non-2xx response), we will retry:
- After 1 minute
- After 5 minutes
- After 30 minutes
- After 2 hours

After 4 failed attempts, the webhook will be disabled and you'll be notified.

## ## Best Practices

1. **Always verify the signature** to prevent spoofing
2. **Respond quickly** - webhook handlers should respond within 5 seconds
3. **Handle duplicate events** - webhooks may be sent multiple times for the same event
4. **Use HTTPS** - webhook URLs must use HTTPS
5. **Log all events** for debugging and auditing purposes
