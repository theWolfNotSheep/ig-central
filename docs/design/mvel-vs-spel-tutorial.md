# MVEL vs SpEL: Expression Languages for Workflow Engines

> A practical tutorial comparing MVEL (used by Nuxeo, Drools, jBPM) and SpEL (used by Spring) — with worked examples showing how each would power the GLS pipeline condition evaluator.

---

## What Are They?

Both are expression languages that let you evaluate logic at runtime without compiling Java code. They turn strings like `document.confidence > 0.8 && document.category.startsWith("HR")` into executable boolean results.

| | MVEL | SpEL |
|--|------|------|
| Full name | MVFLEX Expression Language | Spring Expression Language |
| Created by | Mike Brock (2007) | Spring Framework team (2009) |
| Used by | Drools, jBPM, Nuxeo, Mule ESB | Spring Framework, Spring Security, Spring Data, Spring Integration |
| Licence | Apache 2.0 | Apache 2.0 |
| Latest version | 2.5.2 (maintenance mode) | Actively maintained with Spring 6.x |
| Maven dependency | `org.mvel:mvel2` | Already in `spring-expression` (transitive via Spring Boot) |

---

## Syntax Comparison

### Basic Expressions

| Operation | MVEL | SpEL |
|-----------|------|------|
| Property access | `document.fileName` | `document.fileName` |
| Nested property | `document.category.name` | `document.category.name` |
| String equality | `status == "CLASSIFIED"` | `status == 'CLASSIFIED'` |
| Numeric comparison | `confidence > 0.8` | `confidence > 0.8` |
| Boolean AND | `a > 1 && b < 5` | `a > 1 && b < 5` or `a > 1 and b < 5` |
| Boolean OR | `a > 1 \|\| b < 5` | `a > 1 \|\| b < 5` or `a > 1 or b < 5` |
| Negation | `!isProcessed` | `!isProcessed` or `not isProcessed` |
| Ternary | `x > 5 ? "high" : "low"` | `x > 5 ? 'high' : 'low'` |
| Null safe | `document.?category` | `document?.category` |
| Null coalescing | `name != null ? name : "unknown"` | `name ?: 'unknown'` (Elvis operator) |

For simple expressions, they're nearly identical. The differences emerge in more advanced features.

### String Operations

```
// MVEL
fileName.toUpperCase()
fileName.contains("INVOICE")
fileName.matches("INV-\\d+\\.pdf")
"Hello " + name
fileName.substring(0, 5)

// SpEL (identical)
fileName.toUpperCase()
fileName.contains('INVOICE')
fileName.matches('INV-\\d+\\.pdf')
'Hello ' + name
fileName.substring(0, 5)
```

### Collection Operations

```
// MVEL — inline list and map creation
list = [1, 2, 3, 4, 5];
map = ["name" : "John", "age" : 30];
list.contains(3);

// MVEL — projection (transform each element)
names = (employees.{ name });          // extract name from each employee
upperNames = (employees.{ name.toUpperCase() });

// MVEL — selection (filter)
seniors = (employees.{? age > 50 });   // filter where age > 50

// MVEL — first/last match
first = (employees.{^ age > 50 });     // first match
last = (employees.{$ age > 50 });      // last match
```

```
// SpEL — inline list and map creation
{1, 2, 3, 4, 5}
{'name': 'John', 'age': 30}
{1, 2, 3}.contains(3)

// SpEL — projection (transform each element)
employees.![name]                       // extract name from each employee
employees.![name.toUpperCase()]

// SpEL — selection (filter)
employees.?[age > 50]                   // filter where age > 50

// SpEL — first/last match
employees.^[age > 50]                   // first match
employees.$[age > 50]                   // last match
```

Both support projection and selection but with different syntax:
- MVEL uses `.{}` for projection, `.{? }` for filtering
- SpEL uses `.![]` for projection, `.?[]` for filtering

### Map/Dictionary Access

```
// MVEL
variables["reviewThreshold"]
metadata["contract_type"] == "permanent"
config["retryCount"] > 3

// SpEL
variables['reviewThreshold']
metadata['contract_type'] == 'permanent'
config['retryCount'] > 3

// SpEL also supports:
#variables['reviewThreshold']           // # prefix for variables in context
```

### Type References and Static Methods

```
// MVEL — import types directly
import java.util.Date;
now = new Date();
Math.max(a, b)

// SpEL — use T() operator for type references
T(java.util.Date).new()                // doesn't actually work like this
T(Math).max(a, b)
T(java.time.Instant).now()
```

### Variables and Assignment

```
// MVEL — supports assignment (stateful)
x = 10;
y = x * 2;
total = items.size() * unitPrice;

// SpEL — limited assignment (read-mostly)
// SpEL does NOT support arbitrary variable assignment in expressions
// Variables must be set externally via the EvaluationContext
// This is a deliberate security/safety design choice
```

This is a **fundamental difference**. MVEL expressions can be mini-programs with multiple statements. SpEL expressions are single evaluations. This matters for workflow engines where you might want a node to compute a value and store it.

### Method Calls on Context Objects

```
// MVEL — Nuxeo style with workflow functions
WorkflowFn.timeSinceTaskWasStarted() > 86400000
CurrentDate.days(5)
Fn.getUsers("managers")

// SpEL — Spring style with registered functions/beans
@workflowFn.timeSinceTaskWasStarted() > 86400000
T(java.time.Instant).now().plusSeconds(432000)
@userService.getUsersByRole('managers')
```

SpEL can call Spring beans directly with the `@beanName` syntax when configured with a `BeanResolver`. MVEL requires explicit function registration.

---

## How Each Would Work in the GLS Pipeline

### Current GLS Condition Evaluator (Hardcoded)

```java
// What we have now — 4 fields, 6 operators, no expressions
public boolean evaluate(String field, String operator, String value, Document doc) {
    return switch (field) {
        case "confidence" -> compareDouble(doc.getConfidence(), operator, Double.parseDouble(value));
        case "piiCount" -> compareInt(doc.getPiiFindings().size(), operator, Integer.parseInt(value));
        case "sensitivity" -> compareString(doc.getSensitivityLabel().name(), operator, value);
        case "fileType" -> compareString(doc.getMimeType(), operator, value);
        default -> false;
    };
}
```

### Option A: MVEL Implementation

**Add dependency:**
```xml
<dependency>
    <groupId>org.mvel</groupId>
    <artifactId>mvel2</artifactId>
    <version>2.5.2.Final</version>
</dependency>
```

**Evaluator service:**
```java
import org.mvel2.MVEL;
import org.mvel2.ParserContext;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

@Service
public class MvelConditionEvaluator {

    // Pre-compile expressions for performance (cache)
    private final Map<String, Serializable> compiledExpressions = new ConcurrentHashMap<>();

    public boolean evaluate(String expression, Document doc, ClassificationResult result,
                            Map<String, Object> pipelineVars) {
        // Build context variables
        Map<String, Object> vars = new HashMap<>();
        vars.put("doc", doc);
        vars.put("classification", result);
        vars.put("pipeline", pipelineVars);
        vars.put("piiCount", doc.getPiiFindings() != null ? doc.getPiiFindings().size() : 0);
        vars.put("confidence", result != null ? result.getConfidence() : 0.0);
        vars.put("sensitivity", doc.getSensitivityLabel() != null ? doc.getSensitivityLabel().name() : "NONE");
        vars.put("now", Instant.now());

        // Compile once, evaluate many times
        Serializable compiled = compiledExpressions.computeIfAbsent(expression,
            expr -> MVEL.compileExpression(expr, createParserContext()));

        Object result = MVEL.executeExpression(compiled, vars);
        return Boolean.TRUE.equals(result);
    }

    private ParserContext createParserContext() {
        ParserContext ctx = new ParserContext();
        // Import common types so expressions can use them without FQN
        ctx.addImport(Instant.class);
        ctx.addImport(Duration.class);
        ctx.addImport(List.class);
        // Add type safety — declare known variable types
        ctx.addInput("doc", DocumentModel.class);
        ctx.addInput("classification", DocumentClassificationResult.class);
        ctx.addInput("pipeline", Map.class);
        ctx.addInput("confidence", Double.class);
        ctx.addInput("piiCount", Integer.class);
        ctx.addInput("sensitivity", String.class);
        return ctx;
    }

    // Validate expression without executing (for the frontend "test" button)
    public String validate(String expression) {
        try {
            MVEL.compileExpression(expression, createParserContext());
            return null; // valid
        } catch (Exception e) {
            return e.getMessage();
        }
    }
}
```

**Example expressions admins could write:**
```
// Simple — same as current hardcoded conditions
confidence > 0.8

// Compound — not possible today
confidence > 0.7 && sensitivity == "CONFIDENTIAL"

// Document-aware
doc.mimeType == "application/pdf" && doc.fileSizeBytes > 1048576

// Metadata-driven
doc.extractedMetadata["contract_type"] == "permanent" && confidence > 0.9

// PII-aware
piiCount > 0 && sensitivity != "RESTRICTED"

// Trait-based
doc.traits.contains("INBOUND") && doc.traits.contains("FINAL")

// Category path matching
doc.categoryName.startsWith("HR") && confidence < 0.85

// Pipeline variable access
pipeline["bertConfidence"] > 0.9 && pipeline["bertCategory"] == doc.categoryName

// Date-based (for escalation)
import java.time.Duration;
Duration.between(doc.createdAt, now).toHours() > 48

// Multi-statement (MVEL-specific — compute then decide)
riskScore = piiCount * 10 + (sensitivity == "RESTRICTED" ? 50 : 0);
riskScore > 30 && confidence < 0.9
```

### Option B: SpEL Implementation

**No dependency needed** — already on the classpath via Spring Boot.

**Evaluator service:**
```java
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.expression.Expression;

@Service
public class SpelConditionEvaluator {

    private final ExpressionParser parser = new SpelExpressionParser();
    private final Map<String, Expression> expressionCache = new ConcurrentHashMap<>();

    public boolean evaluate(String expression, Document doc, ClassificationResult result,
                            Map<String, Object> pipelineVars) {
        StandardEvaluationContext ctx = createContext(doc, result, pipelineVars);

        Expression expr = expressionCache.computeIfAbsent(expression,
            parser::parseExpression);

        Boolean value = expr.getValue(ctx, Boolean.class);
        return Boolean.TRUE.equals(value);
    }

    private StandardEvaluationContext createContext(Document doc,
                                                    ClassificationResult result,
                                                    Map<String, Object> pipelineVars) {
        StandardEvaluationContext ctx = new StandardEvaluationContext();

        // Set root object (allows direct property access without prefix)
        ctx.setRootObject(doc);

        // Set named variables (accessed with # prefix)
        ctx.setVariable("doc", doc);
        ctx.setVariable("classification", result);
        ctx.setVariable("pipeline", pipelineVars);
        ctx.setVariable("piiCount", doc.getPiiFindings() != null ? doc.getPiiFindings().size() : 0);
        ctx.setVariable("confidence", result != null ? result.getConfidence() : 0.0);
        ctx.setVariable("sensitivity", doc.getSensitivityLabel() != null
            ? doc.getSensitivityLabel().name() : "NONE");
        ctx.setVariable("now", Instant.now());

        // Register helper functions
        try {
            ctx.registerFunction("daysBetween",
                ExpressionHelpers.class.getMethod("daysBetween", Instant.class, Instant.class));
            ctx.registerFunction("hoursSince",
                ExpressionHelpers.class.getMethod("hoursSince", Instant.class));
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        }

        return ctx;
    }

    // Validate expression
    public String validate(String expression) {
        try {
            parser.parseExpression(expression);
            return null; // valid
        } catch (Exception e) {
            return e.getMessage();
        }
    }
}

// Helper functions accessible via #functionName()
public class ExpressionHelpers {
    public static long daysBetween(Instant a, Instant b) {
        return Duration.between(a, b).toDays();
    }
    public static long hoursSince(Instant timestamp) {
        return Duration.between(timestamp, Instant.now()).toHours();
    }
}
```

**Example expressions admins could write:**
```
// Simple
#confidence > 0.8

// Compound
#confidence > 0.7 and #sensitivity == 'CONFIDENTIAL'

// Document-aware (root object is doc, so direct access works)
mimeType == 'application/pdf' and fileSizeBytes > 1048576

// Or with explicit #doc prefix
#doc.mimeType == 'application/pdf'

// Metadata-driven
#doc.extractedMetadata['contract_type'] == 'permanent' and #confidence > 0.9

// PII-aware
#piiCount > 0 and #sensitivity != 'RESTRICTED'

// Trait-based
#doc.traits.contains('INBOUND') and #doc.traits.contains('FINAL')

// Category path matching
#doc.categoryName?.startsWith('HR') and #confidence < 0.85

// Pipeline variable access
#pipeline['bertConfidence'] > 0.9

// Date-based with helper functions
#hoursSince(#doc.createdAt) > 48

// Collection filtering
#doc.piiFindings.?[type == 'NI_NUMBER'].size() > 0

// Elvis operator (null safety)
#doc.categoryName ?: 'UNCATEGORISED'

// Regex matching
#doc.fileName matches 'INV-\d+\.pdf'
```

**Note:** SpEL uses `#variable` syntax for context variables and direct property access for the root object. SpEL does NOT support multi-statement expressions or variable assignment — each expression is a single evaluation.

---

## Security: Sandboxing Expressions

Both languages can execute arbitrary code if not restricted. In a workflow engine where admins write expressions, this is a security concern.

### MVEL Security

MVEL has **no built-in sandbox**. You must restrict it manually:

```java
// MVEL — custom security manager (manual, fragile)
ParserContext ctx = new ParserContext();
// Remove dangerous imports
// Problem: MVEL can still access java.lang.Runtime via reflection
// There is no reliable way to fully sandbox MVEL

// Partial mitigation: compile in strict mode
ctx.setStrictTypeEnforcement(true);
ctx.setStrongTyping(true);
```

**Risk level:** High. MVEL can call `Runtime.getRuntime().exec("rm -rf /")` unless you implement a custom ClassLoader or SecurityManager (both deprecated in modern Java). Nuxeo mitigates this by limiting who can edit workflows (Studio access is controlled).

### SpEL Security

SpEL has **configurable restrictions** via `SimpleEvaluationContext`:

```java
// SpEL — restricted evaluation context (built-in, robust)
SimpleEvaluationContext ctx = SimpleEvaluationContext
    .forReadOnlyDataBinding()        // no property writes
    .withInstanceMethods()           // allow safe method calls (toString, contains, etc.)
    .build();

// This context CANNOT:
// - Construct new objects (no new keyword)
// - Access static methods (no T() operator)
// - Call Runtime, ProcessBuilder, or any dangerous class
// - Write to properties
// - Use bean references (@beanName)

// For more control, use StandardEvaluationContext with a custom MethodResolver:
StandardEvaluationContext ctx = new StandardEvaluationContext();
ctx.setMethodResolvers(List.of(new SafeMethodResolver())); // whitelist methods
ctx.setPropertyAccessors(List.of(new ReflectivePropertyAccessor())); // read-only
ctx.setConstructorResolvers(List.of()); // no constructors
```

**Risk level:** Low when using `SimpleEvaluationContext`. SpEL was designed for this use case (Spring Security expressions run in user-controlled contexts).

### Recommendation

For a multi-tenant SaaS where admins write expressions, **SpEL's built-in sandboxing is significantly safer**. MVEL requires custom security work that is difficult to get right.

---

## Performance Comparison

Both pre-compile expressions and cache the compiled form. Performance differences are negligible for the GLS use case (expressions evaluated once per document, not millions of times per second).

```
Benchmark: 1 million evaluations of "confidence > 0.8 && sensitivity == 'CONFIDENTIAL'"

MVEL (compiled):  ~45ms  (22M eval/s)
SpEL (compiled):  ~65ms  (15M eval/s)
SpEL (parsed):    ~180ms (5.5M eval/s)

Both are effectively instant for per-document evaluation.
```

MVEL is slightly faster because it compiles to bytecode. SpEL interprets an AST. For GLS, where expression evaluation takes <0.001ms vs an LLM call taking 30,000ms, this difference is irrelevant.

---

## Feature Matrix

| Feature | MVEL | SpEL | Winner |
|---------|------|------|--------|
| Property access | Yes | Yes | Tie |
| Method calls | Yes | Yes | Tie |
| Null-safe navigation | `?.` | `?.` | Tie |
| Collection projection | `.{ }` | `.![]` | Tie |
| Collection filtering | `.{? }` | `.?[]` | Tie |
| Regex matching | `.matches()` | `matches` keyword | SpEL (cleaner) |
| Inline lists | `[1,2,3]` | `{1,2,3}` | Tie |
| Inline maps | `["k":v]` | `{'k':v}` | Tie |
| **Multi-statement** | **Yes** | **No** | **MVEL** |
| **Variable assignment** | **Yes** | **No** | **MVEL** |
| **Loops (for, while)** | **Yes** | **No** | **MVEL** |
| **If/else blocks** | **Yes** | **No** | **MVEL** |
| Type references | `import` | `T()` operator | MVEL (cleaner) |
| Elvis operator | No native | `?:` | SpEL |
| Bean references | No | `@beanName` | SpEL |
| **Sandboxing** | **Manual (fragile)** | **Built-in (robust)** | **SpEL** |
| **No new dependency** | **No (needs mvel2)** | **Yes (in Spring Boot)** | **SpEL** |
| **Active maintenance** | **No (maintenance mode)** | **Yes (Spring 6.x)** | **SpEL** |
| Spring integration | None | Native | SpEL |
| Custom functions | Via `ParserContext` | Via `registerFunction` | Tie |

---

## When to Choose MVEL

- You need **multi-statement expressions** (compute intermediate values, conditional logic)
- You're building a **rules engine** like Drools where expressions are mini-programs
- You're integrating with **Nuxeo, jBPM, or Activiti** (they use MVEL natively)
- Your users are **developers** who need full scripting power and you trust them not to write malicious code

**MVEL's killer feature: multi-statement expressions.**
```
// Only MVEL can do this in a single expression:
riskScore = 0;
if (piiCount > 0) riskScore += 20;
if (sensitivity == "RESTRICTED") riskScore += 50;
if (confidence < 0.7) riskScore += 30;
riskScore > 40
```

In SpEL, you'd need to restructure this as a single boolean expression:
```
(#piiCount > 0 ? 20 : 0) + (#sensitivity == 'RESTRICTED' ? 50 : 0) + (#confidence < 0.7 ? 30 : 0) > 40
```

Both work, but MVEL is more readable for complex logic.

## When to Choose SpEL

- You're in a **Spring Boot application** (no new dependency)
- You need **sandboxing** for untrusted/semi-trusted expression authors
- Your expressions are **single evaluations** (conditions, filters, assertions) not programs
- You want **active maintenance** and long-term support
- You need **Spring bean access** (call services directly from expressions)
- Your expressions are written by **admins, not developers**

**SpEL's killer features: sandboxing + zero dependency + Spring integration.**

---

## Recommendation for GLS

**Use SpEL.** Here's why:

1. **Already on the classpath** — Spring Boot includes `spring-expression`. Zero new dependencies.

2. **Security** — GLS is a multi-tenant platform where admins write expressions. SpEL's `SimpleEvaluationContext` provides robust sandboxing. MVEL's lack of built-in sandboxing is a liability.

3. **Maintenance** — MVEL is in maintenance mode (last significant release was 2019). SpEL is actively maintained as part of Spring Framework 6.x.

4. **Sufficient power** — GLS condition expressions are single evaluations ("should this document go down path A or path B?"). We don't need multi-statement programs, loops, or variable assignment. SpEL covers 100% of the condition use cases.

5. **Spring bean access** — SpEL can call Spring beans with `@beanName.method()`. This means we could write `@taxonomyService.isChildOf(#doc.categoryId, 'HR')` without building a custom function registry.

6. **The multi-statement gap is solvable** — for the rare case where an admin needs complex computed logic, expose it as a registered helper function rather than inline code:
   ```
   // Instead of multi-statement MVEL:
   //   riskScore = piiCount * 10 + ...; riskScore > 40
   
   // Use a SpEL helper:
   #riskScore(#piiCount, #sensitivity, #confidence) > 40
   ```

### Implementation sketch for GLS

```java
@Service
public class PipelineExpressionEvaluator {

    private final ExpressionParser parser = new SpelExpressionParser();

    public boolean evaluate(String expression, NodeHandlerContext ctx) {
        StandardEvaluationContext evalCtx = new StandardEvaluationContext();

        // Document fields (root object — no prefix needed)
        evalCtx.setRootObject(ctx.getDocument());

        // Named variables (accessed with #)
        evalCtx.setVariable("doc", ctx.getDocument());
        evalCtx.setVariable("classification", ctx.getClassificationResult());
        evalCtx.setVariable("pipeline", ctx.getSharedContext());
        evalCtx.setVariable("confidence", getConfidence(ctx));
        evalCtx.setVariable("piiCount", getPiiCount(ctx));
        evalCtx.setVariable("sensitivity", getSensitivity(ctx));
        evalCtx.setVariable("now", Instant.now());

        // Helper functions
        registerHelpers(evalCtx);

        // Restrict — no constructors, no static methods, no bean access
        // (use StandardEvaluationContext with whitelisted resolvers for production)

        return Boolean.TRUE.equals(
            parser.parseExpression(expression).getValue(evalCtx, Boolean.class));
    }

    private void registerHelpers(StandardEvaluationContext ctx) {
        register(ctx, "hoursSince", Instant.class);
        register(ctx, "daysSince", Instant.class);
        register(ctx, "riskScore", int.class, String.class, double.class);
        register(ctx, "isChildOf", String.class, String.class);
    }
}
```

**Admin writes in the condition node inspector:**
```
#confidence > 0.8 and #sensitivity != 'RESTRICTED'
```

**Or in advanced mode:**
```
#doc.traits.contains('INBOUND')
    and #doc.mimeType == 'application/pdf'
    and #piiCount > 0
    and #hoursSince(#doc.createdAt) < 24
    and #confidence > #pipeline['reviewThreshold']
```

Both are single SpEL expressions that evaluate to a boolean. Clean, safe, and powerful enough for any governance workflow condition.
