// Transcript parsers for the three agents, and the token price.

/**
 * DeepSeek deepseek-flash price, CNY per million tokens (api-docs.deepseek.com, read 2026-10-09).
 * Peak = Beijing time Mon–Fri 9:00–12:00 and 14:00–18:00; other times cost half.
 */
// forcePeak: price every run at the peak tier, so runs started at different times compare fairly.
export function tokenCost(t, at, forcePeak = false) {
  if (!t) return null;
  const bj = new Date(at.getTime() + 8 * 3600_000);
  const h = bj.getUTCHours(), d = bj.getUTCDay();
  const peak = d >= 1 && d <= 5 && ((h >= 9 && h < 12) || (h >= 14 && h < 18));
  const k = peak || forcePeak ? 1 : 0.5;
  const yuan = ((t.cacheRead ?? 0) * 0.04 + ((t.input ?? 0) + (t.cacheCreate ?? 0)) * 2 + (t.output ?? 0) * 8) * k / 1e6;
  return +yuan.toFixed(4);
}

export function parseClaude(text) {
  let turns = 0, toolCalls = 0, askAttempts = 0, result = null, finalText = "";
  const toolNames = {};
  const filesRead = new Set();
  const perMessage = new Map(); // message id -> usage; stream-json repeats a message once per content block
  for (const line of text.split("\n")) {
    if (!line.trim()) continue;
    let m;
    try { m = JSON.parse(line); } catch { continue; }
    if (m.type === "assistant") {
      turns++;
      if (m.message?.id && m.message.usage) perMessage.set(m.message.id, m.message.usage);
      for (const c of m.message?.content ?? []) {
        if (c.type === "tool_use") {
          toolCalls++;
          toolNames[c.name] = (toolNames[c.name] ?? 0) + 1;
          if (c.name === "AskUserQuestion") askAttempts++;
          if (c.name === "Read" && c.input?.file_path) filesRead.add(c.input.file_path);
        }
        if (c.type === "text") finalText = c.text;
      }
    }
    if (m.type === "result") result = m;
  }
  // modelUsage covers every model call in the session (main loop and background helpers).
  const mu = Object.values(result?.modelUsage ?? {});
  const u = mu.length
    ? {
        input_tokens: mu.reduce((s, x) => s + (x.inputTokens ?? 0), 0),
        cache_read_input_tokens: mu.reduce((s, x) => s + (x.cacheReadInputTokens ?? 0), 0),
        cache_creation_input_tokens: mu.reduce((s, x) => s + (x.cacheCreationInputTokens ?? 0), 0),
        output_tokens: mu.reduce((s, x) => s + (x.outputTokens ?? 0), 0),
      }
    : result?.usage
      ? result.usage
      : // killed by the timeout: no result line, so add up the main loop's per-message usage
        [...perMessage.values()].reduce(
          (t, x) => ({
            input_tokens: t.input_tokens + (x.input_tokens ?? 0),
            cache_read_input_tokens: t.cache_read_input_tokens + (x.cache_read_input_tokens ?? 0),
            cache_creation_input_tokens: t.cache_creation_input_tokens + (x.cache_creation_input_tokens ?? 0),
            output_tokens: t.output_tokens + (x.output_tokens ?? 0),
          }),
          { input_tokens: 0, cache_read_input_tokens: 0, cache_creation_input_tokens: 0, output_tokens: 0 },
        );
  return {
    turns, toolCalls, toolNames, askAttempts, filesRead: filesRead.size,
    stopReason: result?.subtype ?? "no-result",
    tokensSource: mu.length || result?.usage ? "final" : "per-message (timed out; main loop only)",
    finalText: result?.result ?? finalText,
    tokens: {
      input: u.input_tokens ?? 0,
      cacheRead: u.cache_read_input_tokens ?? 0,
      cacheCreate: u.cache_creation_input_tokens ?? 0,
      output: u.output_tokens ?? 0,
    },
  };
}

export function parseCodex(text) {
  let turns = 0, toolCalls = 0, finalText = "";
  const toolNames = {};
  const tokens = { input: 0, cacheRead: 0, cacheCreate: 0, output: 0 };
  for (const line of text.split("\n")) {
    let m;
    try { m = JSON.parse(line); } catch { continue; }
    if (m.type === "item.completed") {
      const t = m.item?.type;
      if (t === "agent_message") { turns++; finalText = m.item.text; }
      if (t === "command_execution" || t === "file_change" || t === "mcp_tool_call" || t === "web_search") {
        toolCalls++;
        toolNames[t] = (toolNames[t] ?? 0) + 1;
      }
    }
    if (m.type === "turn.completed" && m.usage) {
      // OpenAI convention: input_tokens includes the cached part.
      tokens.cacheRead += m.usage.cached_input_tokens ?? 0;
      tokens.input += (m.usage.input_tokens ?? 0) - (m.usage.cached_input_tokens ?? 0);
      tokens.cacheCreate += m.usage.cache_write_input_tokens ?? 0;
      tokens.output += m.usage.output_tokens ?? 0;
    }
  }
  const completed = /"type":"turn.completed"/.test(text);
  const failed = /"type":"turn.failed"/.test(text);
  return {
    turns, toolCalls, toolNames, askAttempts: 0, filesRead: 0,
    stopReason: failed ? "error" : completed ? "success" : "no-result",
    // `codex exec --json` reports usage only when the turn completes; a run killed by the timeout has none.
    finalText, tokens: completed ? tokens : null, tokensSource: completed ? "final" : "unknown (timed out)",
  };
}

export function parseOpenCode(text) {
  let turns = 0, toolCalls = 0, finalText = "";
  const toolNames = {};
  const tokens = { input: 0, cacheRead: 0, cacheCreate: 0, output: 0 };
  let lastReason = "no-result";
  for (const line of text.split("\n")) {
    let m;
    try { m = JSON.parse(line); } catch { continue; }
    if (m.type === "tool_use") {
      toolCalls++;
      const n = m.part?.tool ?? "?";
      toolNames[n] = (toolNames[n] ?? 0) + 1;
    }
    if (m.type === "text" && m.part?.text) finalText = m.part.text;
    if (m.type === "step_finish") {
      turns++;
      lastReason = m.part?.reason ?? lastReason;
      const t = m.part?.tokens ?? {};
      tokens.input += t.input ?? 0;
      tokens.output += (t.output ?? 0) + (t.reasoning ?? 0);
      tokens.cacheRead += t.cache?.read ?? 0;
      tokens.cacheCreate += t.cache?.write ?? 0;
    }
  }
  return { turns, toolCalls, toolNames, askAttempts: toolNames.question ?? 0, filesRead: toolNames.read ?? 0, stopReason: lastReason, finalText, tokens, tokensSource: "per-step" };
}

