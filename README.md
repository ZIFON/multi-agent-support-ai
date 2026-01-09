# Multi-Agent Support AI

A multi-turn conversational system with dynamic routing between specialized agents for technical support and billing inquiries.

## Overview

This system provides intelligent routing between two specialized agents:

- **Tech Agent**: Answers technical questions using documentation snippets. Always cites sources and asks for clarification when documentation doesn't cover the question.
- **Billing Agent**: Handles billing-related inquiries using tool-calling with three tools:
  - Open refund cases
  - Get plan information
  - Estimate refund timelines

The system maintains conversation history and uses LLM-based routing to classify incoming messages into TECH, BILLING, or OUT_OF_SCOPE categories.

## Features

- **Dynamic Routing**: LLM-based classification of user messages
- **Multi-turn Conversations**: Maintains conversation history by conversationId
- **Retrieval Pipeline**: Document loading, chunking, and keyword-based retrieval
- **Tool Calling**: Billing agent uses OpenAI tool calling for structured operations
- **Citation Support**: Tech agent cites documentation sources in [docId:sectionTitle] format
- **In-memory Storage**: Fast, ephemeral storage for conversations and billing data

## Prerequisites

- Java 17 or higher
- Maven 3.6+
- Docker (optional, for containerized deployment)
- OpenAI API key

## Environment Variables

Set the following environment variables:

```bash
export OPENAI_API_KEY=your_openai_api_key_here
export OPENAI_MODEL=gpt-4o-mini  # Optional, defaults to gpt-4o-mini
```

## Running Locally

### Option 1: Using Maven Wrapper

```bash
./mvnw spring-boot:run
```

### Option 2: Using Maven (if installed)

```bash
mvn spring-boot:run
```

### Option 3: Build and Run JAR

```bash
mvn clean package
java -jar target/multi-agent-support-ai-1.0.0.jar
```

The application will start on `http://localhost:8080`.

## Running with Docker

### Build Docker Image

```bash
docker build -t multi-agent-support-ai .
```

### Run Container

```bash
docker run -p 8080:8080 \
  -e OPENAI_API_KEY=your_openai_api_key_here \
  -e OPENAI_MODEL=gpt-4o-mini \
  multi-agent-support-ai
```

The application will be available at `http://localhost:8080`.

## API Endpoint

### POST /chat

Sends a message to the multi-agent system.

**Request:**
```json
{
  "conversationId": "string",
  "message": "string"
}
```

**Response:**
```json
{
  "conversationId": "string",
  "agent": "TECH|BILLING|OUT_OF_SCOPE",
  "response": "string",
  "citations": ["docId:sectionTitle", ...],
  "toolUsed": "string|null",
  "caseId": "string|null",
  "meta": {
    "snippetsFound": 4,
    "needsClarification": false
  }
}
```

## Example Usage

### Example 1: Technical Question (TECH Agent)

**Initial Question:**
```bash
curl -X POST http://localhost:8080/chat \
  -H "Content-Type: application/json" \
  -d '{
    "conversationId": "conv-001",
    "message": "How do I authenticate API requests?"
  }'
```

**Response:**
```json
{
  "conversationId": "conv-001",
  "agent": "TECH",
  "response": "All API requests require authentication using an API key. Include your API key in the Authorization header as: Authorization: Bearer YOUR_API_KEY. You can generate an API key from Settings > API Keys in your account dashboard.",
  "citations": ["authentication:API Keys"],
  "toolUsed": null,
  "meta": {
    "snippetsFound": 3,
    "needsClarification": false
  }
}
```

**Follow-up Question:**
```bash
curl -X POST http://localhost:8080/chat \
  -H "Content-Type: application/json" \
  -d '{
    "conversationId": "conv-001",
    "message": "What about webhooks? How do I set them up?"
  }'
```

**Response:**
```json
{
  "conversationId": "conv-001",
  "agent": "TECH",
  "response": "To set up webhooks, log in to your account dashboard, navigate to Settings > Webhooks, add a new webhook URL, select the events you want to receive, and save your webhook secret for signature verification. All webhook requests are authenticated using a signature in the X-Webhook-Signature header.",
  "citations": ["api_webhooks:Webhook Configuration", "api_webhooks:Authentication"],
  "toolUsed": null,
  "meta": {
    "snippetsFound": 4,
    "needsClarification": false
  }
}
```

### Example 2: Billing Question (BILLING Agent) - Multi-turn Refund Flow

**Initial Request:**
```bash
curl -X POST http://localhost:8080/chat \
  -H "Content-Type: application/json" \
  -d '{
    "conversationId": "conv-002",
    "message": "I want a refund"
  }'
```

**Response:**
```json
{
  "conversationId": "conv-002",
  "agent": "BILLING",
  "response": "I'd be happy to help you with a refund. To process your refund, I'll need some information. Could you please provide:\n1. Your email address\n2. Your order ID\n3. The reason for the refund request",
  "toolUsed": null,
  "meta": {}
}
```

**Providing Information:**
```bash
curl -X POST http://localhost:8080/chat \
  -H "Content-Type: application/json" \
  -d '{
    "conversationId": "conv-002",
    "message": "My email is user1@example.com, order ID is ORD-12345, and I want to cancel my subscription"
  }'
```

**Response:**
```json
{
  "conversationId": "conv-002",
  "agent": "BILLING",
  "response": "I've opened a refund case for you. Your case ID is REF-1000. Please complete the refund form using this link: https://example.com/refund-form/uuid-here. Our team will review and process your request within 2-3 business days.",
  "toolUsed": "openRefundCase",
  "caseId": "REF-1000",
  "meta": {
    "caseId": "REF-1000",
    "formLink": "https://example.com/refund-form/uuid-here",
    "status": "OPEN"
  }
}
```

**Checking Plan Info:**
```bash
curl -X POST http://localhost:8080/chat \
  -H "Content-Type: application/json" \
  -d '{
    "conversationId": "conv-002",
    "message": "What is my current plan?"
  }'
```

**Response:**
```json
{
  "conversationId": "conv-002",
  "agent": "BILLING",
  "response": "Your current plan is Premium, priced at $29.99/month. Your next renewal date is 2024-02-15.",
  "toolUsed": "getPlanInfo",
  "meta": {
    "email": "user1@example.com",
    "planName": "Premium",
    "price": 29.99,
    "renewalDate": "2024-02-15"
  }
}
```

### Example 3: Out of Scope Question

```bash
curl -X POST http://localhost:8080/chat \
  -H "Content-Type: application/json" \
  -d '{
    "conversationId": "conv-003",
    "message": "What is the weather today?"
  }'
```

**Response:**
```json
{
  "conversationId": "conv-003",
  "agent": "OUT_OF_SCOPE",
  "response": "I apologize, but I'm specifically trained to help with technical questions and billing inquiries. Could you please rephrase your question, or let me know if you have a technical or billing-related question I can help with?",
  "citations": null,
  "toolUsed": null
}
```

## Documentation

The system uses documentation files in the `./docs` directory:

- `api_webhooks.md` - Webhook setup and configuration
- `authentication.md` - API authentication methods
- `integration_guide.md` - Integration patterns and best practices
- `billing_policy.md` - Refund policies and processing times

The Tech Agent retrieves relevant snippets from these documents to answer questions.

## Architecture

### Components

1. **ConversationOrchestrator**: Main orchestrator that routes messages and coordinates agents
2. **Router**: LLM-based routing classifier (TECH/BILLING/OUT_OF_SCOPE)
3. **TechAgent**: Answers technical questions using retrieved documentation snippets
4. **BillingAgent**: Handles billing inquiries with tool-calling
5. **Retriever**: Keyword-based document retrieval (DocLoader → Chunker → Retriever)
6. **Storage**: In-memory stores for conversations and billing data

### Flow

1. User sends message with conversationId
2. Orchestrator gets conversation history
3. Router classifies message using LLM
4. Based on route:
   - **TECH**: Retrieve docs → TechAgent answers with citations
   - **BILLING**: BillingAgent handles with tool calling
   - **OUT_OF_SCOPE**: Polite decline response
5. Save conversation history
6. Return response

## Seed Data

The system includes seed data for testing:

- `user1@example.com`: Premium plan ($29.99/month)
- `user2@example.com`: Basic plan ($9.99/month)
- `user3@example.com`: Enterprise plan ($99.99/month)

## Limitations

- In-memory storage (data is lost on restart)
- Simple keyword-based retrieval (no vector embeddings)
- Basic error handling
- No authentication/authorization on API

## License

MIT
