# Troubleshooting Notes

## Authentication / 401 Unauthorized

If you receive `401 Unauthorized` responses, double-check that you are sending the API key in the request headers:

```
Authorization: Bearer YOUR_API_KEY
```

Also make sure you are not mixing sandbox and production keys.

## Webhook Signature Failures

If your webhook handler rejects events (for example, because of an invalid signature), verify that:

1. You read the **raw request body** (before JSON parsing).
2. You compute the signature using `HMAC-SHA256(webhook_secret, request_body)`.
3. You compare your computed signature with the value in the `X-Webhook-Signature` header.

## Rate Limits (429)

If you get `429 Too Many Requests`, follow these steps:

1. Implement retries with backoff (wait some time and try again).
2. Check response headers for `X-RateLimit-Limit`, `X-RateLimit-Remaining`, and `X-RateLimit-Reset`.
3. Reduce request frequency if your usage is near the limit.

