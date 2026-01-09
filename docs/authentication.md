# Authentication Guide

## ## API Keys

All API requests require authentication using an API key. Include your API key in the `Authorization` header:

```
Authorization: Bearer YOUR_API_KEY
```

### Getting Your API Key

1. Log in to your account
2. Navigate to Settings > API Keys
3. Click "Generate New API Key"
4. Copy and securely store your API key

**Important**: API keys are only shown once when generated. Keep them secure and never commit them to version control.

## ## OAuth 2.0

For third-party integrations, we support OAuth 2.0 authentication.

### Authorization Flow

1. **Redirect user to authorization endpoint:**
   ```
   GET https://api.example.com/oauth/authorize
     ?client_id=YOUR_CLIENT_ID
     &redirect_uri=YOUR_REDIRECT_URI
     &response_type=code
     &scope=read write
   ```

2. **User grants permission** and is redirected to your `redirect_uri` with an authorization code

3. **Exchange code for access token:**
   ```
   POST https://api.example.com/oauth/token
     grant_type=authorization_code
     &code=AUTHORIZATION_CODE
     &client_id=YOUR_CLIENT_ID
     &client_secret=YOUR_CLIENT_SECRET
     &redirect_uri=YOUR_REDIRECT_URI
   ```

4. **Use access token** in subsequent API requests:
   ```
   Authorization: Bearer ACCESS_TOKEN
   ```

### Token Refresh

Access tokens expire after 1 hour. Refresh them using the refresh token:

```
POST https://api.example.com/oauth/token
  grant_type=refresh_token
  &refresh_token=REFRESH_TOKEN
  &client_id=YOUR_CLIENT_ID
  &client_secret=YOUR_CLIENT_SECRET
```

## ## Rate Limiting

API requests are rate-limited to prevent abuse:

- **Free tier**: 100 requests per hour
- **Paid tier**: 1000 requests per hour

Rate limit information is included in response headers:

```
X-RateLimit-Limit: 1000
X-RateLimit-Remaining: 999
X-RateLimit-Reset: 1640995200
```

If you exceed the rate limit, you'll receive a `429 Too Many Requests` response.

## ## Error Responses

Authentication errors return a `401 Unauthorized` status:

```json
{
  "error": "unauthorized",
  "message": "Invalid or missing API key"
}
```
