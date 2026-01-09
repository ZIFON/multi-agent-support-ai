# ДЕТАЛЬНЕ ПОЯСНЕННЯ ПРОЕКТУ

## 🎯 ЩО РОБИТЬ ЦЕЙ ПРОЕКТ?

Це система автоматичної підтримки користувачів (AI чат-бот), яка:
- Приймає питання від користувачів через REST API
- Розуміє, про що запитує користувач (технічне питання чи питання про оплату)
- Дає відповіді використовуючи два спеціалізованих агента:
  - **Tech Agent** - відповідає на технічні питання, використовуючи документацію
  - **Billing Agent** - допомагає з оплатою, поверненням коштів, підписками
- Зберігає історію розмови для багаторазових діалогів

---

## 📊 ЗАГАЛЬНА АРХІТЕКТУРА

```
Користувач (Postman/curl)
    ↓ POST /chat
ChatController (REST API)
    ↓
ConversationOrchestrator (головний координатор)
    ↓
Router (визначає тип питання: TECH/BILLING/OUT_OF_SCOPE)
    ↓
┌─────────────────┬──────────────────┐
│                 │                  │
TechAgent         BillingAgent       OUT_OF_SCOPE
    ↓                  ↓                  ↓
Retriever → Docs  Tool Calling    Статична відповідь
    ↓                  ↓
TechAgent відповідає  BillingAgent виконує дії
    ↓                  ↓
ChatResponse (JSON відповідь)
    ↓
Користувач отримує відповідь
```

---

## 🔄 ПОВНИЙ ПОТІК РОБОТИ (крок за кроком)

### КРОК 1: Користувач надсилає запит

**Приклад запиту:**
```bash
curl -X POST http://localhost:8080/chat \
  -H "Content-Type: application/json" \
  -d '{
    "conversationId": "conv-001",
    "message": "Як налаштувати webhook?"
  }'
```

**Що відбувається:**
- Користувач відкриває Postman або використовує curl
- Надсилає HTTP POST запит на адресу `http://localhost:8080/chat`
- В тілі запиту передає JSON:
  - `conversationId` - унікальний ID розмови (для збереження історії)
  - `message` - текст питання користувача

---

### КРОК 2: ChatController приймає запит

**Файл:** `src/main/java/com/example/multiagent/controller/ChatController.java`

```java
@RestController  // Це Spring Boot анотація = "цей клас приймає HTTP запити"
public class ChatController {
    
    @PostMapping("/chat")  // Приймає POST запити на шлях /chat
    public ResponseEntity<ChatResponse> chat(@RequestBody ChatRequest request) {
        // @RequestBody = автоматично конвертує JSON з запиту в Java об'єкт ChatRequest
        // ChatRequest має два поля: conversationId та message
        
        // Валідація - перевіряємо чи все заповнено
        if (request.getConversationId() == null || request.getMessage() == null) {
            return ResponseEntity.badRequest().build();  // Повертаємо помилку 400
        }
        
        // Передаємо далі в Orchestrator
        ChatResponse response = orchestrator.handle(request.getConversationId(), request.getMessage());
        return ResponseEntity.ok(response);  // Повертаємо успішну відповідь (200 OK)
    }
}
```

**Що тут відбувається:**
- Spring Boot автоматично виявляє `@RestController` при старті
- Створює HTTP endpoint на `/chat`
- Коли приходить POST запит:
  1. Парсить JSON в об'єкт `ChatRequest`
  2. Перевіряє чи все заповнено
  3. Передає управління в `ConversationOrchestrator`
  4. Повертає відповідь як JSON

---

### КРОК 3: ConversationOrchestrator - головний координатор

**Файл:** `src/main/java/com/example/multiagent/orchestrator/ConversationOrchestrator.java`

**Що робить Orchestrator:**
Він як диспетчер - координує всю роботу системи.

```java
@Service  // Spring Boot: це сервіс, який можна використовувати в інших класах
public class ConversationOrchestrator {
    // Залежності (Spring Boot автоматично передає їх через @Autowired)
    private final Router router;                    // Визначає тип питання
    private final Retriever retriever;              // Шукає документацію
    private final TechAgent techAgent;              // Технічний агент
    private final BillingAgent billingAgent;        // Білінговий агент
    private final InMemoryConversationStore conversationStore;  // Зберігає історію
    
    public ChatResponse handle(String conversationId, String message) {
        // 1. Отримуємо історію попередніх повідомлень
        List<Message> history = conversationStore.getHistoryForLlm(conversationId);
        
        // 2. Зберігаємо повідомлення користувача в історію
        Message userMessage = new Message("user", message);
        conversationStore.append(conversationId, userMessage);
        
        // 3. ВИЗНАЧАЄМО ТИП ПИТАННЯ через Router
        RouteResult routeResult = router.route(history, message);
        String route = routeResult.getRoute();  // "TECH", "BILLING", або "OUT_OF_SCOPE"
        
        // 4. ВИБИРАЄМО АГЕНТА залежно від типу питання
        ChatResponse response;
        switch (route) {
            case "TECH":
                response = handleTechRequest(history, message);
                break;
            case "BILLING":
                response = handleBillingRequest(history, message);
                break;
            case "OUT_OF_SCOPE":
            default:
                response = handleOutOfScope();  // "Я не можу допомогти з цим"
                break;
        }
        
        // 5. Зберігаємо відповідь агента в історію
        Message assistantMessage = new Message("assistant", response.getResponse());
        conversationStore.append(conversationId, assistantMessage);
        
        // 6. Повертаємо відповідь
        return response;
    }
}
```

**Що відбувається:**
1. Отримує історію попередніх повідомлень (для багаторазових діалогів)
2. Зберігає нове повідомлення користувача
3. **Викликає Router** щоб зрозуміти тип питання
4. Залежно від типу - викликає відповідний агент
5. Зберігає відповідь в історію
6. Повертає відповідь

---

### КРОК 4: Router - визначає тип питання

**Файл:** `src/main/java/com/example/multiagent/orchestrator/Router.java`

**Проблема:** Як зрозуміти, чи питання технічне чи про оплату?

**Рішення:** Використовуємо OpenAI (GPT) для класифікації!

```java
@Component  // Spring Boot компонент
public class Router {
    private final LlmClient llmClient;  // Клієнт для викликів OpenAI
    
    public RouteResult route(List<Message> history, String userMessage) {
        // 1. Створюємо промпт (інструкцію) для AI
        String prompt = buildRoutingPrompt(history, userMessage);
        
        // 2. Створюємо повідомлення для OpenAI
        List<Message> messages = new ArrayList<>();
        messages.add(new Message("system", 
            "Ти асистент-роутер. Класифікуй повідомлення користувача як: " +
            "TECH (технічні питання), BILLING (оплата), або OUT_OF_SCOPE (не в моїй компетенції). " +
            "Поверни ТІЛЬКИ JSON: {\"route\":\"TECH|BILLING|OUT_OF_SCOPE\",\"why\":\"пояснення\"}"
        ));
        messages.add(new Message("user", prompt));
        
        // 3. ВИКЛИКАЄМО OPENAI для класифікації
        String response = llmClient.chatCompletion(messages);
        // OpenAI відповідає щось на кшталт: {"route":"TECH","why":"питання про webhook"}
        
        // 4. Парсимо відповідь і повертаємо результат
        return parseRouteResponse(response);
    }
    
    private String buildRoutingPrompt(List<Message> history, String userMessage) {
        // Будуємо детальний промпт з:
        // - Поточним повідомленням користувача
        // - Контекстом попередніх повідомлень
        // - Правилами класифікації
        StringBuilder prompt = new StringBuilder();
        prompt.append("Класифікуй наступне повідомлення:\n\n");
        prompt.append("Повідомлення: ").append(userMessage).append("\n\n");
        
        // Додаємо контекст розмови
        if (!history.isEmpty()) {
            prompt.append("Контекст розмови:\n");
            for (Message msg : history) {
                prompt.append(msg.getRole()).append(": ").append(msg.getContent()).append("\n");
            }
        }
        
        prompt.append("\nПравила:\n");
        prompt.append("- TECH: API, інтеграції, webhooks, аутентифікація, помилки\n");
        prompt.append("- BILLING: оплата, повернення, підписки, плани, рахунки\n");
        prompt.append("- OUT_OF_SCOPE: все інше\n");
        
        return prompt.toString();
    }
}
```

**Приклад роботи:**
- Користувач: "Як налаштувати webhook?"
- Router створює промпт для OpenAI
- OpenAI відповідає: `{"route":"TECH","why":"питання про webhook - це технічне питання"}`
- Router повертає `RouteResult("TECH", "питання про webhook")`

---

### КРОК 5A: Якщо питання TECH - TechAgent

**Файл:** `src/main/java/com/example/multiagent/orchestrator/ConversationOrchestrator.java`

```java
private ChatResponse handleTechRequest(List<Message> history, String message) {
    // 1. ШУКАЄМО РЕЛЕВАНТНУ ДОКУМЕНТАЦІЮ
    List<Chunk> snippets = retriever.retrieve(message, 4);
    // Retriever шукає в папці ./docs файли, які можуть містити відповідь
    // Повертає топ-4 найбільш релевантних фрагментів
    
    // 2. ПЕРЕДАЄМО TechAgent: питання + знайдені фрагменти документації
    TechAgent.TechAgentResult result = techAgent.answer(history, message, snippets);
    
    // 3. Формуємо відповідь
    ChatResponse response = new ChatResponse();
    response.setResponse(result.getAnswer());           // Текст відповіді
    response.setCitations(result.getCitations());       // Джерела: ["docId:sectionTitle"]
    response.setToolUsed(null);                         // Для TECH не використовуємо інструменти
    
    return response;
}
```

**Детально про TechAgent:**

**Файл:** `src/main/java/com/example/multiagent/agents/TechAgent.java`

```java
@Component
public class TechAgent {
    
    public TechAgentResult answer(List<Message> history, String message, List<Chunk> snippets) {
        // 1. СТВОРЮЄМО ПРОМПТ ДЛЯ AI з документацією
        List<Message> messages = new ArrayList<>();
        
        // Системна інструкція
        messages.add(new Message("system",
            "Ти технічний спеціаліст. Відповідай ТІЛЬКИ використовуючи надані фрагменти документації. " +
            "Якщо відповіді немає в документації - скажи це прямо і попроси уточнення. " +
            "НЕ вигадуй інформацію. Завжди вказуй джерела у форматі [docId:sectionTitle]. " +
            "Поверни JSON: {\"answer\":\"...\",\"citations\":[\"docId:sectionTitle\"],\"needs_clarification\":true/false}"
        ));
        
        // Додаємо контекст розмови
        for (Message msg : history) {
            messages.add(msg);
        }
        
        // Додаємо питання користувача + знайдені фрагменти документації
        StringBuilder userPrompt = new StringBuilder();
        userPrompt.append("Питання користувача: ").append(message).append("\n\n");
        userPrompt.append("Знайдені фрагменти документації:\n\n");
        for (Chunk chunk : snippets) {
            userPrompt.append("Фрагмент [").append(chunk.getDocId()).append(":")
                     .append(chunk.getSectionTitle()).append("]:\n");
            userPrompt.append(chunk.getText()).append("\n\n");
        }
        messages.add(new Message("user", userPrompt.toString()));
        
        // 2. ВИКЛИКАЄМО OPENAI
        String response = llmClient.chatCompletion(messages);
        // OpenAI генерує відповідь на основі наданих фрагментів
        
        // 3. ПАРСИМО ВІДПОВІДЬ
        return parseResponse(response, snippets);
        // Повертаємо: текст відповіді, список джерел, чи потрібне уточнення
    }
}
```

**Приклад:**
- Користувач: "Як налаштувати webhook?"
- Retriever знаходить фрагменти з `docs/api_webhooks.md`
- TechAgent отримує фрагменти + питання
- OpenAI генерує відповідь використовуючи ці фрагменти
- Відповідь: "Для налаштування webhook перейдіть до Settings > Webhooks..." + джерела: ["api_webhooks:Webhook Configuration"]

---

### КРОК 5B: Якщо питання BILLING - BillingAgent

**Файл:** `src/main/java/com/example/multiagent/orchestrator/ConversationOrchestrator.java`

```java
private ChatResponse handleBillingRequest(List<Message> history, String message) {
    // BillingAgent використовує tool calling - може ВИКОНУВАТИ ДІЇ
    BillingAgent.BillingAgentResult result = billingAgent.answer(history, message);
    
    ChatResponse response = new ChatResponse();
    response.setResponse(result.getResponse());
    response.setToolUsed(result.getToolUsed());    // Який інструмент використано
    response.setCaseId(result.getMeta().get("caseId"));  // ID справи (якщо відкрито)
    
    return response;
}
```

**Детально про BillingAgent з Tool Calling:**

**Файл:** `src/main/java/com/example/multiagent/agents/BillingAgent.java`

**Що таке Tool Calling?**
Це можливість для AI не просто відповісти, а **ВИКОНАТИ ДІЮ** (наприклад, відкрити справу про повернення, перевірити план).

```java
@Component
public class BillingAgent {
    
    public BillingAgentResult answer(List<Message> history, String message) {
        // 1. СТВОРЮЄМО ПРОМПТ
        List<Message> messages = buildMessages(history, message);
        
        // 2. ВИЗНАЧАЄМО ДОСТУПНІ ІНСТРУМЕНТИ (tools)
        List<ToolDefinition> tools = createToolDefinitions();
        // Інструменти:
        // - openRefundCase(email, orderId, reason) - відкрити справу про повернення
        // - getPlanInfo(email) - отримати інформацію про план
        // - estimateRefundTimeline(paymentMethod, purchaseDate) - оцінити терміни повернення
        
        // 3. ВИКЛИКАЄМО OPENAI З ІНСТРУМЕНТАМИ
        int maxIterations = 5;  // Максимум 5 ітерацій (на випадок якщо потрібно кілька викликів)
        String toolUsed = null;
        
        for (int i = 0; i < maxIterations; i++) {
            // Викликаємо OpenAI
            ChatCompletionResult result = llmClient.chatCompletionWithTools(messages, tools);
            
            // Перевіряємо: чи AI хоче використати інструмент?
            if (!result.hasToolCalls()) {
                // НІ - просто повертаємо текстову відповідь
                return new BillingAgentResult(result.getContent(), toolUsed, meta);
            }
            
            // ТАК - AI хоче використати інструмент
            for (ToolCall toolCall : result.getToolCalls()) {
                toolUsed = toolCall.getFunctionName();  // Наприклад, "openRefundCase"
                
                // ВИКОНУЄМО ІНСТРУМЕНТ
                Map<String, Object> toolResult = executeTool(toolCall);
                // Наприклад, openRefundCase повертає: {"caseId": "REF-1000", "formLink": "https://..."}
                
                // ДОДАЄМО РЕЗУЛЬТАТ В РОЗМОВУ
                Message toolResultMessage = new Message("tool", 
                    "Результат виконання: " + toolResult.toString());
                messages.add(new Message("assistant", null));  // Порожнє повідомлення агента
                messages.add(toolResultMessage);  // Результат виконання інструменту
            }
            
            // ПРОДОВЖУЄМО - OpenAI отримає результат і згенерує фінальну відповідь
        }
    }
    
    private Map<String, Object> executeTool(ToolCall toolCall) {
        // Парсимо аргументи інструменту
        JsonNode args = objectMapper.readTree(toolCall.getArguments());
        String functionName = toolCall.getFunctionName();
        
        switch (functionName) {
            case "openRefundCase":
                String email = args.get("email").asText();
                String orderId = args.get("orderId").asText();
                String reason = args.get("reason").asText();
                // ВИКЛИКАЄМО РЕАЛЬНИЙ МЕТОД
                return billingTools.openRefundCase(email, orderId, reason);
                
            case "getPlanInfo":
                String email2 = args.get("email").asText();
                return billingTools.getPlanInfo(email2);
                
            case "estimateRefundTimeline":
                String paymentMethod = args.get("paymentMethod").asText();
                String purchaseDate = args.get("purchaseDateIso").asText();
                return billingTools.estimateRefundTimeline(paymentMethod, purchaseDate);
        }
    }
}
```

**Приклад роботи Tool Calling:**

1. Користувач: "Хочу повернути кошти за замовлення ORD-12345"
2. BillingAgent викликає OpenAI з інструментами
3. OpenAI розуміє: потрібно використати `openRefundCase`, але бракує email
4. OpenAI відповідає: "Мені потрібен ваш email адреса"
5. Користувач: "Мій email: user@example.com"
6. BillingAgent знову викликає OpenAI
7. OpenAI викликає інструмент: `openRefundCase("user@example.com", "ORD-12345", "повернення")`
8. Система виконує інструмент → створюється справу, генерується посилання на форму
9. Результат повертається в OpenAI
10. OpenAI генерує фінальну відповідь: "Я відкрив справу REF-1000. Заповніть форму: https://..."

---

### КРОК 6: Retriever - пошук документації

**Файл:** `src/main/java/com/example/multiagent/retrieval/Retriever.java`

**Проблема:** Як знайти релевантні фрагменти документації для відповіді?

**Рішення:** Простий keyword-based пошук (не vector embeddings, щоб було простіше).

**Піpline:** DocLoader → Chunker → Retriever

#### 6.1. DocLoader - завантажує файли

**Файл:** `src/main/java/com/example/multiagent/retrieval/DocLoader.java`

```java
@Component
public class DocLoader {
    private static final String DOCS_DIR = "./docs";
    
    public Map<String, String> loadAllDocuments() {
        // Шукає всі .md та .txt файли в папці ./docs
        // Повертає Map: {"api_webhooks": "весь текст файлу", "authentication": "..."}
    }
}
```

#### 6.2. Chunker - розбиває на частини

**Файл:** `src/main/java/com/example/multiagent/retrieval/Chunker.java`

```java
@Component
public class Chunker {
    
    public List<Chunk> chunk(String docId, String content) {
        // Розбиває документ на частини (chunks)
        // Спробує спершу розбити за markdown заголовками (## Заголовок)
        // Якщо немає заголовків - розбиває по розміру (~800-1200 символів)
        
        // Кожен Chunk містить:
        // - docId: назва файлу (наприклад, "api_webhooks")
        // - sectionTitle: назва секції (наприклад, "Webhook Configuration")
        // - text: текст фрагменту
    }
}
```

**Приклад:**
```
Файл: docs/api_webhooks.md
  ↓ Chunker розбиває на:
Chunk 1: docId="api_webhooks", sectionTitle="Overview", text="Webhooks allow..."
Chunk 2: docId="api_webhooks", sectionTitle="Authentication", text="All webhook requests..."
Chunk 3: docId="api_webhooks", sectionTitle="Webhook Events", text="payment.completed..."
```

#### 6.3. Retriever - шукає релевантні фрагменти

**Файл:** `src/main/java/com/example/multiagent/retrieval/Retriever.java`

```java
@Component
public class Retriever {
    
    public List<Chunk> retrieve(String query, int topK) {
        // 1. Токенізуємо запит (розбиваємо на слова)
        Set<String> queryTerms = tokenize(query.toLowerCase());
        // "Як налаштувати webhook?" → ["як", "налаштувати", "webhook"]
        
        // 2. Для кожного Chunk рахуємо score (релевантність)
        for (Chunk chunk : allChunks) {
            double score = scoreChunk(chunk, queryTerms);
            // score рахується як:
            // - кількість співпадінь слів у тексті
            // - бонус якщо слово є в назві секції
            // - бонус за унікальні співпадіння
        }
        
        // 3. Сортуємо за score і повертаємо топ-K (найчастіше K=4)
        return sortedChunks.limit(topK);
    }
    
    private double scoreChunk(Chunk chunk, Set<String> queryTerms) {
        double score = 0.0;
        
        // Рахуємо співпадіння в тексті
        for (String term : queryTerms) {
            if (chunk.getText().contains(term)) {
                score += 1.0;  // Базовий score
            }
        }
        
        // Бонус якщо слово в назві секції
        if (chunk.getSectionTitle().contains("webhook")) {
            score += 3.0;  // Великий бонус
        }
        
        return score;
    }
}
```

**Приклад:**
- Запит: "webhook authentication"
- Retriever знаходить:
  1. Chunk з секції "Authentication" про webhooks (score: 10.0)
  2. Chunk з секції "Overview" про webhooks (score: 5.0)
  3. Chunk з секції "Webhook Events" (score: 3.0)
  4. Chunk про загальну аутентифікацію (score: 2.0)
- Повертає топ-4 найрелевантніших

---

## 🗄️ СХОВИЩА ДАНИХ

### InMemoryConversationStore - зберігає історію розмов

**Файл:** `src/main/java/com/example/multiagent/storage/InMemoryConversationStore.java`

```java
@Component
public class InMemoryConversationStore {
    // Map: conversationId → список повідомлень
    private final Map<String, List<Message>> conversations = new ConcurrentHashMap<>();
    
    public List<Message> getHistory(String conversationId) {
        // Отримує історію розмови за ID
        // Повертає: [Message("user", "Привіт"), Message("assistant", "Привіт!")...]
    }
    
    public void append(String conversationId, Message message) {
        // Додає нове повідомлення в історію
        conversations.get(conversationId).add(message);
    }
}
```

**Приклад:**
```
conversationId="conv-001":
  [
    Message(role="user", content="Привіт", timestamp=...),
    Message(role="assistant", content="Привіт! Чим допомогти?", timestamp=...),
    Message(role="user", content="Як налаштувати webhook?", timestamp=...),
    Message(role="assistant", content="Для налаштування...", timestamp=...)
  ]
```

**ВАЖЛИВО:** Це in-memory storage - дані втрачаються при перезапуску!

---

### InMemoryBillingStore - зберігає білінгові дані

**Файл:** `src/main/java/com/example/multiagent/storage/InMemoryBillingStore.java`

```java
@Component
public class InMemoryBillingStore {
    // Map: email → інформація про план
    private final Map<String, PlanInfo> plansByEmail = new ConcurrentHashMap<>();
    
    // Map: caseId → інформація про справу повернення
    private final Map<String, RefundCase> casesById = new ConcurrentHashMap<>();
    
    // Seed дані для демо (створюються при старті)
    public InMemoryBillingStore() {
        plansByEmail.put("user1@example.com", 
            new PlanInfo("Premium", 29.99, LocalDate.now().plusMonths(1)));
        plansByEmail.put("user2@example.com", 
            new PlanInfo("Basic", 9.99, LocalDate.now().plusDays(15)));
        // ...
    }
    
    public PlanInfo getPlanInfo(String email) {
        return plansByEmail.get(email);
    }
    
    public RefundCase createRefundCase(String email, String orderId, String reason, String formLink) {
        String caseId = "REF-" + (nextCaseId++);
        RefundCase refundCase = new RefundCase(caseId, email, orderId, reason, formLink);
        casesById.put(caseId, refundCase);
        return refundCase;
    }
}
```

---

## 🔧 ІНСТРУМЕНТИ BILLING AGENT

**Файл:** `src/main/java/com/example/multiagent/tools/BillingTools.java`

### 1. openRefundCase - відкрити справу про повернення

```java
public Map<String, Object> openRefundCase(String email, String orderId, String reason) {
    // Генерує унікальне посилання на форму
    String formLink = "https://example.com/refund-form/" + UUID.randomUUID();
    
    // Створює справу в базі
    RefundCase refundCase = billingStore.createRefundCase(email, orderId, reason, formLink);
    
    return Map.of(
        "caseId", refundCase.getCaseId(),      // "REF-1000"
        "formLink", refundCase.getFormLink(),  // "https://..."
        "status", "OPEN"
    );
}
```

### 2. getPlanInfo - отримати інформацію про план

```java
public Map<String, Object> getPlanInfo(String email) {
    PlanInfo planInfo = billingStore.getPlanInfo(email);
    
    if (planInfo == null) {
        return Map.of("error", "План не знайдено");
    }
    
    return Map.of(
        "email", email,
        "planName", planInfo.getPlanName(),      // "Premium"
        "price", planInfo.getPrice(),            // 29.99
        "renewalDate", planInfo.getRenewalDate() // "2024-02-15"
    );
}
```

### 3. estimateRefundTimeline - оцінити терміни повернення

```java
public Map<String, Object> estimateRefundTimeline(String paymentMethod, String purchaseDateIso) {
    // Читає політику з docs/billing_policy.md
    LocalDate purchaseDate = LocalDate.parse(purchaseDateIso);
    long daysSincePurchase = ChronoUnit.DAYS.between(purchaseDate, LocalDate.now());
    
    boolean eligible = daysSincePurchase <= 14;  // 14 днів вікно
    
    if (!eligible) {
        return Map.of(
            "eligible", false,
            "timelineText", "Повернення недоступне: пройшло " + daysSincePurchase + " днів"
        );
    }
    
    // Оцінює термін залежно від способу оплати
    String timelineText;
    if (paymentMethod.contains("card")) {
        timelineText = "Карткові повернення зазвичай займають 5-10 робочих днів";
    } else if (paymentMethod.contains("paypal")) {
        timelineText = "PayPal повернення зазвичай 1-3 робочих дні";
    }
    
    return Map.of("eligible", true, "timelineText", timelineText);
}
```

---

## 🔌 LLM КЛІЄНТИ

### LlmClient - обгортка для OpenAI

**Файл:** `src/main/java/com/example/multiagent/llm/LlmClient.java`

```java
public class LlmClient {
    private final OpenAiClient openAiClient;
    
    // Простий виклик без інструментів
    public String chatCompletion(List<Message> messages) {
        List<Map<String, String>> apiMessages = convertMessages(messages);
        ChatCompletionResponse response = openAiClient.chatCompletion(apiMessages, null);
        return response.getContent();
    }
    
    // Виклик З інструментами (tool calling)
    public ChatCompletionResult chatCompletionWithTools(
        List<Message> messages, 
        List<ToolDefinition> tools
    ) {
        ChatCompletionResponse response = openAiClient.chatCompletion(apiMessages, tools);
        return new ChatCompletionResult(
            response.getContent(),
            response.getToolCalls()  // Список інструментів, які хоче викликати AI
        );
    }
}
```

---

### OpenAiClient - HTTP клієнт для OpenAI API

**Файл:** `src/main/java/com/example/multiagent/llm/OpenAiClient.java`

```java
public class OpenAiClient {
    private static final String API_URL = "https://api.openai.com/v1/chat/completions";
    private final String apiKey;
    private final String model;  // "gpt-4o-mini" за замовчуванням
    private final HttpClient httpClient;
    
    public OpenAiClient() {
        // Читає API key з environment variable
        this.apiKey = System.getenv("OPENAI_API_KEY");
        this.model = System.getenv().getOrDefault("OPENAI_MODEL", "gpt-4o-mini");
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();
    }
    
    public ChatCompletionResponse chatCompletion(
        List<Map<String, String>> messages, 
        List<ToolDefinition> tools
    ) {
        // 1. Формуємо JSON запит
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", model);
        requestBody.put("messages", messages);
        if (tools != null) {
            requestBody.put("tools", tools);      // Список доступних інструментів
            requestBody.put("tool_choice", "auto");  // AI сам вирішує використовувати чи ні
        }
        
        String jsonBody = objectMapper.writeValueAsString(requestBody);
        
        // 2. Створюємо HTTP запит
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(API_URL))
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer " + apiKey)  // API key в заголовку
            .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
            .timeout(Duration.ofSeconds(60))
            .build();
        
        // 3. Відправляємо запит
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        
        // 4. Парсимо відповідь
        return parseResponse(response.body());
    }
    
    private ChatCompletionResponse parseResponse(String json) {
        JsonNode root = objectMapper.readTree(json);
        JsonNode message = root.get("choices").get(0).get("message");
        
        String content = message.has("content") ? message.get("content").asText() : null;
        
        // Перевіряємо чи є tool_calls (чи AI хоче викликати інструмент)
        List<ToolCall> toolCalls = new ArrayList<>();
        if (message.has("tool_calls")) {
            for (JsonNode toolCallNode : message.get("tool_calls")) {
                ToolCall toolCall = new ToolCall();
                toolCall.setId(toolCallNode.get("id").asText());
                toolCall.setFunctionName(toolCallNode.get("function").get("name").asText());
                toolCall.setArguments(toolCallNode.get("function").get("arguments").asText());
                toolCalls.add(toolCall);
            }
        }
        
        return new ChatCompletionResponse(content, toolCalls);
    }
}
```

**Формат запиту до OpenAI:**
```json
{
  "model": "gpt-4o-mini",
  "messages": [
    {"role": "system", "content": "Ти технічний спеціаліст..."},
    {"role": "user", "content": "Як налаштувати webhook?"}
  ],
  "tools": [
    {
      "type": "function",
      "function": {
        "name": "openRefundCase",
        "description": "Відкрити справу про повернення",
        "parameters": {
          "type": "object",
          "properties": {
            "email": {"type": "string", "description": "Email користувача"},
            "orderId": {"type": "string", "description": "ID замовлення"}
          },
          "required": ["email", "orderId"]
        }
      }
    }
  ],
  "tool_choice": "auto"
}
```

**Формат відповіді від OpenAI:**
```json
{
  "choices": [{
    "message": {
      "content": "Для налаштування webhook...",
      "tool_calls": [  // Якщо AI хоче викликати інструмент
        {
          "id": "call_123",
          "type": "function",
          "function": {
            "name": "openRefundCase",
            "arguments": "{\"email\":\"user@example.com\",\"orderId\":\"ORD-123\"}"
          }
        }
      ]
    }
  }]
}
```

---

## 📦 СТРУКТУРА ПРОЕКТУ

```
multi-agent-support-ai/
├── pom.xml                          # Maven конфігурація (залежності)
├── Dockerfile                       # Docker конфігурація
├── README.md                        # Документація
│
├── docs/                            # Документація для TechAgent
│   ├── api_webhooks.md             # Про webhooks
│   ├── authentication.md           # Про аутентифікацію
│   ├── integration_guide.md        # Гайд з інтеграції
│   └── billing_policy.md           # Політика повернень
│
└── src/main/
    ├── java/com/example/multiagent/
    │   ├── MultiAgentSupportApplication.java  # Точка входу (main)
    │   │
    │   ├── controller/             # REST API
    │   │   ├── ChatController.java            # POST /chat endpoint
    │   │   ├── ChatRequest.java               # JSON запит
    │   │   └── ChatResponse.java              # JSON відповідь
    │   │
    │   ├── orchestrator/           # Координація
    │   │   ├── ConversationOrchestrator.java  # Головний координатор
    │   │   ├── Router.java                    # Класифікація питання
    │   │   └── RouteResult.java               # Результат класифікації
    │   │
    │   ├── agents/                 # AI Агенти
    │   │   ├── TechAgent.java                 # Технічний агент
    │   │   └── BillingAgent.java              # Білінговий агент
    │   │
    │   ├── retrieval/              # Пошук документації
    │   │   ├── DocLoader.java                 # Завантаження файлів
    │   │   ├── Chunker.java                   # Розбиття на частини
    │   │   ├── Retriever.java                 # Пошук релевантних
    │   │   └── Chunk.java                     # Модель фрагменту
    │   │
    │   ├── storage/                # Сховища даних
    │   │   ├── InMemoryConversationStore.java # Історія розмов
    │   │   ├── InMemoryBillingStore.java      # Білінгові дані
    │   │   ├── PlanInfo.java                  # Модель плану
    │   │   └── RefundCase.java                # Модель справи
    │   │
    │   ├── tools/                  # Інструменти для BillingAgent
    │   │   └── BillingTools.java              # openRefundCase, getPlanInfo, etc.
    │   │
    │   └── llm/                    # LLM клієнти
    │       ├── LlmClient.java                  # Обгортка
    │       ├── OpenAiClient.java               # HTTP клієнт до OpenAI
    │       └── Message.java                    # Модель повідомлення
    │
    └── resources/
        └── application.properties   # Конфігурація (порт 8080)
```

---

## 🚀 ЯК ВСЕ ПРАЦЮЄ РАЗОМ - ПОВНИЙ ПРИКЛАД

### Сценарій: Користувач запитує про webhook

**1. HTTP запит:**
```bash
POST http://localhost:8080/chat
{
  "conversationId": "conv-001",
  "message": "Як налаштувати webhook для отримання повідомлень про платежі?"
}
```

**2. ChatController приймає:**
- Парсить JSON → `ChatRequest(conversationId="conv-001", message="...")`
- Передає в `orchestrator.handle("conv-001", "...")`

**3. ConversationOrchestrator:**
- Отримує історію: `[]` (перше повідомлення)
- Зберігає повідомлення користувача
- Викликає `router.route(history, message)`

**4. Router:**
- Створює промпт для OpenAI:
  ```
  Класифікуй: "Як налаштувати webhook для отримання повідомлень про платежі?"
  Правила:
  - TECH: API, webhooks, інтеграції
  - BILLING: оплата, повернення
  ```
- Викликає OpenAI → отримує: `{"route":"TECH","why":"питання про webhook"}`
- Повертає `RouteResult("TECH", "...")`

**5. ConversationOrchestrator (продовження):**
- `route = "TECH"` → викликає `handleTechRequest(...)`

**6. Retriever:**
- Запит: "webhook для отримання повідомлень про платежі"
- Токенізує: ["webhook", "отримання", "повідомлень", "платежі"]
- Шукає в chunks:
  - Chunk з `api_webhooks.md`, секція "Webhook Configuration" → score: 15.0
  - Chunk з `api_webhooks.md`, секція "Webhook Events" → score: 12.0
  - Chunk про платежі → score: 8.0
- Повертає топ-4 найрелевантніших chunks

**7. TechAgent:**
- Створює промпт:
  ```
  Система: Ти технічний спеціаліст. Відповідай ТІЛЬКИ використовуючи надані фрагменти.
  
  Користувач: Як налаштувати webhook для отримання повідомлень про платежі?
  
  Фрагменти:
  [api_webhooks:Webhook Configuration]:
  Щоб налаштувати webhook, перейдіть до Settings > Webhooks, додайте URL...
  
  [api_webhooks:Webhook Events]:
  payment.completed - повідомлення про успішний платіж...
  ```
- Викликає OpenAI → отримує JSON:
  ```json
  {
    "answer": "Для налаштування webhook перейдіть до Settings > Webhooks, додайте URL вашого сервера, виберіть події payment.completed та інші, які вас цікавлять. Збережіть webhook secret для перевірки підписів.",
    "citations": ["api_webhooks:Webhook Configuration", "api_webhooks:Webhook Events"],
    "needs_clarification": false
  }
  ```
- Парсить → повертає `TechAgentResult`

**8. ConversationOrchestrator (фінал):**
- Створює `ChatResponse`:
  ```java
  response.setResponse("Для налаштування webhook...");
  response.setCitations(["api_webhooks:Webhook Configuration", "api_webhooks:Webhook Events"]);
  response.setAgent("TECH");
  response.setConversationId("conv-001");
  ```
- Зберігає відповідь в історію
- Повертає response

**9. ChatController:**
- Конвертує в JSON → повертає HTTP 200:
```json
{
  "conversationId": "conv-001",
  "agent": "TECH",
  "response": "Для налаштування webhook перейдіть до Settings > Webhooks...",
  "citations": ["api_webhooks:Webhook Configuration", "api_webhooks:Webhook Events"],
  "toolUsed": null,
  "meta": {
    "snippetsFound": 4,
    "needsClarification": false
  }
}
```

**10. Користувач отримує відповідь!**

---

### Сценарій: Білінг з Tool Calling

**1. Користувач:** "Хочу повернути кошти"

**2. Router:** класифікує як "BILLING"

**3. BillingAgent:**
- Викликає OpenAI з інструментами
- OpenAI розуміє: потрібно `openRefundCase`, але бракує email та orderId
- OpenAI відповідає: "Мені потрібен ваш email та номер замовлення"

**4. Користувач:** "Email: user1@example.com, замовлення ORD-12345"

**5. BillingAgent:**
- Знову викликає OpenAI
- OpenAI викликає інструмент:
  ```json
  {
    "name": "openRefundCase",
    "arguments": "{\"email\":\"user1@example.com\",\"orderId\":\"ORD-12345\",\"reason\":\"повернення\"}"
  }
  ```
- Система виконує `billingTools.openRefundCase(...)`
- Створюється справу: `caseId="REF-1000"`, `formLink="https://..."`
- Результат повертається в OpenAI:
  ```json
  {"caseId": "REF-1000", "formLink": "https://...", "status": "OPEN"}
  ```

**6. OpenAI генерує фінальну відповідь:**
- "Я відкрив справу REF-1000 про повернення. Заповніть форму: https://..."

**7. ChatResponse:**
```json
{
  "agent": "BILLING",
  "response": "Я відкрив справу REF-1000...",
  "toolUsed": "openRefundCase",
  "caseId": "REF-1000",
  "meta": {
    "caseId": "REF-1000",
    "formLink": "https://...",
    "status": "OPEN"
  }
}
```

---

## 🎓 КЛЮЧОВІ КОНЦЕПЦІЇ

### 1. Spring Boot Dependency Injection

```java
@Autowired
public ConversationOrchestrator(Router router, TechAgent techAgent, ...) {
    // Spring Boot автоматично створює об'єкти та передає їх сюди
    // Не потрібно писати: Router router = new Router(...)
}
```

### 2. Component, Service, Controller

- `@Component` - загальний компонент Spring
- `@Service` - бізнес-логіка (Orchestrator)
- `@RestController` - HTTP endpoints (Controller)

### 3. Tool Calling Pattern

1. AI отримує список доступних інструментів
2. AI вирішує: чи потрібен інструмент?
3. Якщо так - повертає `tool_calls` з назвою та аргументами
4. Система виконує інструмент
5. Результат передається назад в AI
6. AI генерує фінальну відповідь

### 4. Multi-turn Conversation

- Кожна розмова має `conversationId`
- Всі повідомлення зберігаються в `InMemoryConversationStore`
- При новому повідомленні - завантажується історія
- AI бачить контекст попередніх повідомлень

---

## 🔍 ЯК ДОДАТИ НОВИЙ АГЕНТ?

**Приклад: Support Agent для загальних питань**

1. Створити клас:
```java
@Component
public class SupportAgent {
    public String answer(String message) {
        // Логіка відповіді
    }
}
```

2. Додати в Orchestrator:
```java
case "SUPPORT":
    response = handleSupportRequest(history, message);
    break;
```

3. Оновити Router правила класифікації:
```java
prompt.append("- SUPPORT: загальні питання підтримки\n");
```

---

Це вся архітектура проекту! Якщо щось незрозуміло - питайте! 🚀
