# MLX vs Ollama on Apple Silicon: A Comparison for Document Classification Pipelines

**Author:** IG Central Engineering
**Date:** April 2026
**Hardware Reference:** Mac Studio, 96GB Unified Memory, Apple Silicon

---

## Abstract

This paper compares two approaches to running large language models locally on Apple Silicon for document classification workloads: Apple's MLX framework and Ollama (backed by llama.cpp, now with an MLX backend). We evaluate inference performance, memory efficiency, tool-calling reliability, fine-tuning capabilities, and production readiness in the context of a governance-led document classification pipeline that uses MCP tool calling, BERT acceleration, and a human correction feedback loop.

The key finding is that as of Ollama 0.19 (March 2026), these are no longer competing approaches — Ollama now uses MLX as its compute backend on Apple Silicon. The question has shifted from "which one?" to "when should you use MLX directly vs through Ollama's serving layer?"

---

## 1. Architecture Overview

### 1.1 MLX

MLX is an open-source array framework developed by Apple's Machine Learning Research team, first released November 2023. It is purpose-built for Apple Silicon's unified memory architecture.

**Core properties:**
- **Unified memory**: Zero-copy sharing between CPU and GPU. No data transfer overhead — the CPU and GPU read from the same physical memory pool
- **Metal GPU backend**: All matrix operations execute natively on Apple's Metal API
- **Lazy evaluation**: Computation graphs are built declaratively and executed only when results are materialised
- **Compiled graph fusion**: `mx.compile` merges multiple GPU kernel launches into a single kernel, reducing memory bandwidth pressure and dispatch overhead
- **Neural Engine support**: On M5 chips, MLX leverages dedicated Neural Accelerators designed specifically for MLX compute patterns

MLX-LM (v0.31.2) is the dedicated LLM inference library built on MLX, providing text generation, chat, quantization, LoRA fine-tuning, and an OpenAI-compatible HTTP server.

### 1.2 Ollama

Ollama is an open-source model serving tool (169K GitHub stars) that wraps llama.cpp for inference. It provides model management (pull, run, delete), an OpenAI-compatible API, concurrent request handling, and tool/function calling.

**Key change — Ollama 0.19 (31 March 2026):** Ollama now uses MLX as its compute backend on Apple Silicon (requires 32GB+ unified memory). This means Ollama on Mac is no longer a llama.cpp wrapper — it delegates to MLX for all GPU computation while retaining Ollama's API layer, model management, and tool-calling infrastructure.

### 1.3 Convergence

```
Before Ollama 0.19:         After Ollama 0.19:

┌────────────┐              ┌────────────┐
│   Ollama   │              │   Ollama   │
│   (API)    │              │   (API)    │
├────────────┤              ├────────────┤
│ llama.cpp  │              │    MLX     │   ← Apple Silicon path
│  (GGUF)    │              │(safetensors)│
├────────────┤              ├────────────┤
│   Metal    │              │   Metal    │
└────────────┘              └────────────┘

                            ┌────────────┐
                            │  MLX-LM    │   ← Direct access
                            │  (Server)  │
                            ├────────────┤
                            │    MLX     │
                            ├────────────┤
                            │   Metal    │
                            └────────────┘
```

---

## 2. Performance Benchmarks

### 2.1 Token Generation Speed

All benchmarks on Apple Silicon with 4-bit quantized models.

| Model | MLX Direct (tok/s) | llama.cpp (tok/s) | MLX Advantage |
|-------|-------------------|-------------------|---------------|
| Qwen3-0.6B | 525.5 | 281.5 | +87% |
| Llama-3.2-1B | 461.9 | 331.3 | +39% |
| Qwen3-4B | 159.0 | 118.2 | +35% |
| Qwen3-8B | 93.3 | 76.9 | +21% |
| Qwen2.5-27B | ~14 | ~14 | Negligible |
| 70B Q4 (M3 Ultra) | ~15 | ~12 | +25% |

*Source: M4 Max 128GB benchmarks; M3 Ultra extrapolated from bandwidth ratios*

**Critical observation:** The MLX advantage diminishes as model size increases. At 27B+ parameters, both frameworks become equally bottlenecked by memory bandwidth. On the M3 Ultra's 800 GB/s bus, a 70B Q4 model generates at ~12-15 tok/s regardless of framework.

For small-to-medium models (< 14B), MLX delivers 20-90% faster inference. For the large models used in cold-start classification (70B), the difference is marginal.

### 2.2 Ollama 0.19 MLX Backend vs Previous llama.cpp Backend

Measured on M4 Max 64GB with Qwen3.5-35B-A3B (MoE, 3B active):

| Metric | Ollama 0.18 (llama.cpp) | Ollama 0.19 (MLX) | Change |
|--------|------------------------|-------------------|--------|
| Prefill speed | 1,147 tok/s | 1,804 tok/s | **+57%** |
| Decode speed | 57.8 tok/s | 111.4 tok/s | **+93%** |
| Total duration | 4.2s | 2.3s | **-45%** |

The MLX backend nearly doubles decode speed. This is significant for classification workloads where the model generates structured JSON responses of 200-500 tokens.

### 2.3 Prefill Latency Warning

MLX performs full prefill before emitting any tokens. For long documents (the primary input in classification pipelines), this creates high time-to-first-token:

| Context Length | Prefill Time (35B MoE, M1 Max) | % of Total Time |
|---------------|-------------------------------|-----------------|
| 1K tokens | ~2s | 40% |
| 4K tokens | ~15s | 75% |
| 8.5K tokens | ~49s | 94% |

**Implication:** For document classification where input text can be 2,000-100,000 characters (500-25,000 tokens), prefill dominates. The M3 Ultra's higher bandwidth (800 vs 400 GB/s) roughly halves these times, but long documents will still see 10-30 second prefill.

### 2.4 M3 Ultra Memory Bandwidth Budget

The M3 Ultra's 800 GB/s memory bandwidth is the ceiling for inference speed. Theoretical maximum decode rates:

| Model (Q4) | Weight Size | Theoretical Max | Practical (~70%) |
|------------|------------|-----------------|------------------|
| 7-8B | 4.6 GB | 174 tok/s | ~120 tok/s |
| 14B | 8 GB | 100 tok/s | ~70 tok/s |
| 32B | 18 GB | 44 tok/s | ~30 tok/s |
| 70B | 40 GB | 20 tok/s | ~14 tok/s |

The practical figure accounts for KV cache reads, attention computation, and kernel dispatch overhead.

---

## 3. Memory Efficiency

### 3.1 Unified Memory Model

Both MLX and Ollama (0.19+) exploit Apple Silicon's unified memory — the CPU and GPU access the same physical memory with no copy overhead. This is fundamentally different from NVIDIA setups where model weights must fit in dedicated VRAM.

The 96GB Mac Studio can hold:
- A 70B model at Q4 (40 GB) with ~50 GB remaining for KV cache, OS, Docker, and other services
- A 32B model at Q4 (18 GB) comfortably alongside all Docker services
- Two 32B Q4 models simultaneously if needed

**Rule of thumb:** Keep model weights under 60% of total unified memory (~58 GB on 96GB) to leave room for KV cache, Docker containers (16GB allocated), and macOS.

### 3.2 RAM Requirements

| Model Size | Q4 Weight Size | KV Cache (8K ctx) | Total Needed |
|------------|---------------|-------------------|-------------|
| 7-8B | 4.6 GB | ~0.5 GB | ~6 GB |
| 14B | 8 GB | ~1 GB | ~10 GB |
| 32B | 18 GB | ~2 GB | ~22 GB |
| 70B | 40 GB | ~4 GB | ~48 GB |
| 70B (FP16) | 140 GB | ~4 GB | ~150 GB |

### 3.3 MLX vs Ollama Memory Overhead

| Aspect | MLX Direct | Ollama (MLX backend) |
|--------|-----------|---------------------|
| Model loading | Safetensors, mmap'd | Converted from GGUF to MLX on first run |
| Runtime overhead | ~200 MB (Python + MLX) | ~500 MB (Go runtime + API layer + MLX) |
| KV cache | Unified, lazy-allocated | Same (delegated to MLX) |
| Multi-model | Manual management | Automatic model swapping with configurable keep-alive |

Ollama adds ~300 MB overhead for its Go runtime and API server. Negligible on 96GB.

---

## 4. Quantization

### 4.1 Format Comparison

| Aspect | MLX (safetensors) | GGUF (llama.cpp/Ollama) |
|--------|-------------------|------------------------|
| Quant levels | 4, 5, 6, 8-bit, FP16/BF16 | Q2_K through Q8_0, 15+ variants |
| Advanced quants | HLWQ (Hadamard-Lloyd) | K-quants (grouped quantization) |
| Quality at 4-bit | Comparable | Comparable (Q4_K_M) |
| Model availability | 4,553 on HuggingFace | First format published for new models |
| Conversion | `mlx_lm.convert --quantize` | `llama-quantize` or community uploads |
| Cross-format | Reads Q4_0/Q4_1/Q8_0 GGUF only | GGUF is the universal format |

### 4.2 Quality Impact (Llama-3.1-8B-Instruct, WikiText-2 Perplexity)

| Quantization | Perplexity Delta | Assessment |
|-------------|-----------------|------------|
| FP16 | Baseline | Reference |
| Q8_0 | +0.14% | Essentially lossless |
| Q5_K_M | +1.09% | Quality sweet spot |
| Q4_K_M | +3.28% | Best size/quality tradeoff |
| Q3_K_M | +8.74% | Meaningful degradation — avoid for classification |

**Recommendation for classification:** Q4_K_M (GGUF) or 4-bit (MLX) is the floor. Classification requires precise category selection and structured JSON output — aggressive quantization below 4-bit measurably degrades structured output reliability.

---

## 5. Tool Calling and Function Calling

This is the most consequential difference for our classification pipeline, which requires 8-10 MCP tool calls per document.

### 5.1 Ollama

- **Mature, built-in** tool/function calling via `/api/chat`
- Dedicated "tools" model category in the model library
- Works with Spring AI natively via `spring-ai-starter-model-ollama`
- Tested with Llama 3.1+, Qwen 2.5+, Command-R, Mistral v0.3+
- **Known limitation:** Model-dependent reliability. Our codebase documents that Qwen 2.5:32b frequently fails to make the final `save_classification_result` tool call after 4-5 preceding calls

### 5.2 MLX

- **Built-in mlx_lm.server:** Explicitly states **no tool/function calling support**
- **mlx-openai-server** (third-party): Adds tool-call parsers for Qwen3, GLM4, MiniMax
- **vllm-mlx** (third-party): Full MCP tool calling, OpenAI and Anthropic API compatible, continuous batching

### 5.3 Assessment

| Capability | Ollama | MLX (built-in) | MLX (vllm-mlx) |
|-----------|--------|----------------|-----------------|
| Basic tool calling | Yes | No | Yes |
| Multi-tool chains | Yes (model-dependent) | No | Yes |
| Spring AI integration | Native | Via OpenAI compat | Via OpenAI compat |
| MCP support | Via Spring AI MCP client | No | Native |
| Production stability | Proven | N/A | Early |

**For our pipeline:** Ollama remains the practical choice for tool-calling workloads. The alternative — restructuring classification to pre-inject all context and reduce tool calls from 8-10 down to 1 (`save_classification_result`) — would unlock MLX direct serving and smaller models.

---

## 6. Fine-Tuning

This is where MLX has a decisive, uncontested advantage.

### 6.1 MLX Fine-Tuning Capabilities

- **LoRA** and **QLoRA** fine-tuning natively via `mlx_lm.lora` and `mlx_lm.train`
- Full fine-tuning for smaller models
- Distributed fine-tuning via `mx.distributed`
- Fine-tune then export: MLX weights can be converted to GGUF for Ollama serving
- On 96GB M3 Ultra: can LoRA fine-tune models up to 32B parameters comfortably (70B Q4 is tight)

**Benchmark:** LoRA fine-tuning Mistral 7B on a 16GB M2 MacBook Pro completes in under 30 minutes. On the M3 Ultra with significantly higher bandwidth, this is substantially faster.

### 6.2 Ollama Fine-Tuning Capabilities

**None.** Ollama is inference-only. No fine-tuning, no adapter training, no weight modification.

### 6.3 Fine-Tuning Workflow for Our Pipeline

```
Training Data (MongoDB)          MLX Fine-Tuning           Deployment
┌─────────────────────┐         ┌──────────────┐         ┌──────────────┐
│ bert_training_data   │────────►│ mlx_lm.lora  │────────►│ Export to    │
│ classification_      │         │              │         │ GGUF format  │
│   corrections        │         │ LoRA adapter │         ├──────────────┤
│ ai_usage_log         │         │ on base model│         │ Serve via    │
│ (prompts + results)  │         └──────────────┘         │ Ollama       │
└─────────────────────┘                                   └──────────────┘
```

**Why this matters:** We could fine-tune an Ollama-served model to be better at:
1. **Tool calling discipline** — train it to always call `save_classification_result`
2. **Our specific taxonomy** — bake category knowledge into weights instead of MCP context
3. **Our classification style** — learn from human corrections directly

A published guide exists for exactly this: fine-tuning a model for function calling with MLX-LM, then deploying through Ollama.

### 6.4 Fine-Tuning vs BERT Training

| Aspect | LLM LoRA Fine-Tuning (MLX) | BERT Training (Current) |
|--------|---------------------------|------------------------|
| What it improves | Classification reasoning, tool discipline, taxonomy knowledge | Fast inference on established categories |
| Training data needed | 100-500 examples | 10+ per category |
| Training time (M3 Ultra 96GB) | 30-120 minutes | 5-15 minutes |
| Inference cost | Same as base LLM | Near-zero (ONNX) |
| Use case | Better LLM for cold-start + edge cases | Production acceleration |

They are complementary: fine-tune the LLM to produce better labels, train BERT on those labels for fast inference.

---

## 7. Model Availability

### 7.1 Ecosystem Size

| Aspect | Ollama | MLX (mlx-community) |
|--------|--------|-------------------|
| Total models | Hundreds (curated library) | 4,553 (HuggingFace) |
| GitHub stars | 169,497 | 25,578 (MLX) + 4,869 (MLX-LM) |
| Model pull UX | `ollama pull llama3.3:70b` | HuggingFace model ID |
| New model lag | Hours (GGUF published first) | Hours-days (community converts) |
| Categories | General, coding, vision, embedding, tools | All of the above + audio |

### 7.2 Models Relevant to Our Classification Pipeline

| Model | Ollama | MLX | Best For |
|-------|--------|-----|----------|
| Llama 3.3:70b | Yes | Yes | Cold-start classification (best reasoning) |
| Command-R:35b | Yes | Yes | Multi-tool workflows (best tool discipline) |
| Qwen 2.5:32b | Yes | Yes | Structured JSON output (current default) |
| Qwen 2.5:72b | Yes | Yes | Maximum quality classification |
| Qwen 2.5:14b | Yes | Yes | Mature phase, fast inference |
| Qwen 2.5:7b | Yes | Yes | Fully-trained phase, minimal resources |
| Qwen 3:32b | Yes | Yes | Improved tool calling over 2.5 |

All models relevant to our pipeline are available in both ecosystems.

---

## 8. Production Readiness

### 8.1 Comparison

| Aspect | Ollama | MLX (mlx_lm.server) | MLX (vllm-mlx) |
|--------|--------|---------------------|-----------------|
| Concurrent requests | Yes | Limited | Yes (continuous batching) |
| Security | Basic auth, reverse-proxy ready | "Not recommended for production" | Moderate |
| Model hot-swapping | Yes (automatic) | Manual restart | Manual |
| Docker support | Official images | Host-only (Mac) | Host-only (Mac) |
| Spring AI native | Yes | Via OpenAI compat | Via OpenAI compat |
| Health checks | `/api/tags` | None built-in | `/health` |
| Logging/monitoring | Structured logs | Minimal | Moderate |
| Keep-alive/unloading | Configurable | Manual | Manual |

### 8.2 Our Architecture Fit

Our pipeline runs in Docker Compose:

```
nginx → web (Next.js) → api (Spring Boot) → llm-worker (Spring AI)
                                                    │
                                                    ├──► Anthropic API (cloud)
                                                    └──► Ollama (local) ← MLX backend on Mac
```

**Ollama fits cleanly** because:
1. Spring AI has a native Ollama integration — no adapter code
2. Ollama runs on the host, accessed via `host.docker.internal:11434`
3. Model management (pull, delete, list) is handled by Ollama's CLI
4. The existing `BertModelController` already manages Ollama models via its API

**MLX direct would require:**
1. Running mlx_lm.server or vllm-mlx on the host
2. Pointing Spring AI's OpenAI client at the MLX server
3. Handling model loading/unloading manually
4. Adding tool-calling middleware (vllm-mlx) or restructuring classification to pre-inject context

---

## 9. Recommendations for IG Central

### 9.1 Immediate (No Architecture Changes)

**Use Ollama 0.19+ and get MLX performance for free.**

Update Ollama to 0.19+ on the Mac Studio. The MLX backend activates automatically on Apple Silicon with 32GB+ unified memory. This delivers the MLX performance improvements (up to 93% faster decode) through the existing Spring AI integration with zero code changes.

### 9.2 Short-Term (Pre-Injection Optimisation)

**Reduce MCP tool calls from 8-10 to 1.**

The classification prompt already supports `injectTaxonomy`, `injectSensitivities`, `injectTraits`, and `injectPiiTypes`. Pre-loading this context into the system prompt eliminates 7+ tool calls, leaving only `save_classification_result`. This:
- Eliminates the tool-calling reliability problem that plagues Ollama + Qwen
- Reduces classification latency by 40-60% (fewer round trips)
- Enables smaller, faster models (tool discipline is no longer a constraint)
- Works with both Ollama and MLX direct serving

### 9.3 Medium-Term (MLX Fine-Tuning Pipeline)

**Fine-tune a classification specialist model with MLX.**

Use the M3 Ultra's 96GB to LoRA fine-tune a base model (Qwen 2.5:14b or 32b) on:
1. Historical classification results from `classification_results` collection
2. Human corrections from `classification_corrections` collection
3. Tool-calling examples from `ai_usage_log` collection

Export to GGUF and serve through Ollama. A domain-specific 14B model that knows our taxonomy could outperform a general-purpose 70B model while running 5x faster.

### 9.4 Long-Term (Hybrid Architecture)

```
Document arrives
       │
       ▼
┌──────────────┐
│ BERT (ONNX)  │──── Confident (>0.85) ────► Done
│ Accelerator  │
└──────┬───────┘
       │ Uncertain / _OTHER
       ▼
┌──────────────┐
│ Fine-tuned   │──── Confident (>0.7) ─────► Done
│ Qwen 14B     │     (via Ollama/MLX)
│ (local)      │
└──────┬───────┘
       │ Still uncertain
       ▼
┌──────────────┐
│ Claude Haiku │──── Full MCP chain ───────► Done
│ (API)        │     (reliable tools)
└──────────────┘
```

Three tiers: BERT (free, instant), fine-tuned local model (free, fast), cloud API (paid, reliable). Each tier only handles what the tier above couldn't.

---

## 10. Summary Matrix

| Dimension | Ollama (0.19+) | MLX Direct | Winner |
|-----------|---------------|------------|--------|
| **Inference speed (< 14B)** | Good (MLX backend) | Best (+20-90%) | MLX |
| **Inference speed (70B)** | ~14 tok/s | ~15 tok/s | Tie |
| **Tool calling** | Mature, built-in | Requires third-party | Ollama |
| **Spring AI integration** | Native | Via OpenAI compat | Ollama |
| **Fine-tuning** | None | LoRA, QLoRA, full | MLX |
| **Model management** | Excellent (pull/run/delete) | Manual | Ollama |
| **Memory efficiency** | Good (MLX backend) | Best (zero overhead) | MLX |
| **Production serving** | Ready | "Not recommended" | Ollama |
| **Docker integration** | Clean | Host-only | Ollama |
| **Quantization options** | 15+ GGUF variants | 4/5/6/8-bit + HLWQ | Ollama |
| **Community size** | 169K stars | 30K stars | Ollama |
| **Apple investment** | Indirect (MLX backend) | Direct (WWDC, research) | MLX |
| **Vision/multimodal** | Basic (LLaVA) | Advanced (mlx-vlm) | MLX |

### Bottom Line

**Ollama 0.19 is the right serving layer for our pipeline.** It gives us MLX performance, mature tool calling, native Spring AI support, and production-ready model management — all through an API we already integrate with.

**MLX is the right fine-tuning and experimentation layer.** Use it to build domain-specific models, evaluate quantization tradeoffs, and prototype with the Python API. Export results to Ollama for production serving.

They are no longer alternatives. They are layers of the same stack.

---

## Appendix A: Version Reference

| Component | Version | Date |
|-----------|---------|------|
| MLX | 0.31.1 | March 2026 |
| MLX-LM | 0.31.2 | April 2026 |
| Ollama | 0.21.0 | April 2026 |
| mlx-community models | 4,553 | April 2026 |
| Spring AI | 2.0.0-SNAPSHOT | Current |

## Appendix B: Sources

- Apple MLX GitHub: github.com/ml-explore/mlx
- MLX-LM GitHub: github.com/ml-explore/mlx-lm
- Ollama Blog — MLX Backend: ollama.com/blog/mlx
- ArXiv 2511.05502 — Apple Silicon LLM Benchmark Study
- WWDC 2025 Sessions 298, 315 — MLX for LLMs
- Apple ML Research — Neural Accelerators for MLX on M5
- ArXiv 2601.14277 — Quantization Impact Study
- IG Central failure-considerations.md — Ollama Tool-Calling Issues
