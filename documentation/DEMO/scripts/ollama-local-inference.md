# Ollama Local Inference & Hybrid Routing — Demo Script

**Duration: 2:30**

**Goal:** Show that sensitive or high-volume workloads can be classified by a local LLM running in your own infrastructure — with selective fallback to Anthropic only when needed.

## Script

1. **Open with the data-sovereignty concern.** *"Public sector, healthcare, and legal customers almost always ask the same question — 'can we classify without our documents ever leaving our perimeter?' The answer is yes, and you don't have to choose between local-only and the best models."*

2. **Navigate to Settings → AI Models.**

3. **Show the inference providers.** *"Three options — Anthropic for cloud, Ollama for local, or Hybrid. Pick Ollama and every call goes to a model running inside your own network. Nothing leaves."*

4. **Walk the Ollama configuration.** *"Point it at your Ollama endpoint — a Docker container, a dedicated GPU box, whatever you've provisioned. Pick the model — Llama 3.1 70B, Mistral, whatever you've loaded. Test the connection."*

5. **Demonstrate Hybrid routing.** *"Hybrid is where it gets clever. You set rules — 'if the document is CONFIDENTIAL or RESTRICTED, use Ollama. Everything else, use Claude Haiku for cost efficiency.' You get data sovereignty where it matters and top-tier accuracy where it doesn't."*

6. **Show a document flow through Hybrid.** Upload a sensitive document. Open the pipeline trace. *"You can see the routing decision in the trace — 'document flagged RESTRICTED, routed to local Ollama'. No cloud call was made for this one."*

7. **Discuss the accuracy trade-off.** *"Open-source local models are getting remarkably good. For documents within your established taxonomy, with your correction history and metadata schemas guiding them, a 70B local model classifies in the mid-90s percent accuracy. For the edge cases, Hybrid routes up to Anthropic."*

8. **Close with the compliance angle.** *"Your data protection officer gets the story they need — personal data stays on-premise, classification accuracy stays high, cost stays controlled."*

**Key message:** *"Data sovereignty and world-class AI aren't mutually exclusive. Route by sensitivity, keep the sensitive stuff local, only pay cloud rates for the rest."*
