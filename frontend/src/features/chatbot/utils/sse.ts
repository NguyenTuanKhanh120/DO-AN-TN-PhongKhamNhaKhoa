export interface ParsedSseEvent {
  event: string;
  data: string;
}

export function parseSseBlock(block: string): ParsedSseEvent | null {
  let event = "message";
  const data: string[] = [];

  for (const line of block.split(/\r?\n/)) {
    if (!line || line.startsWith(":")) continue;
    const separator = line.indexOf(":");
    const field = separator >= 0 ? line.slice(0, separator) : line;
    const rawValue = separator >= 0 ? line.slice(separator + 1) : "";
    const value = rawValue.startsWith(" ") ? rawValue.slice(1) : rawValue;
    if (field === "event") event = value;
    if (field === "data") data.push(value);
  }

  return data.length > 0 ? { event, data: data.join("\n") } : null;
}

export async function consumeSseStream(
  stream: ReadableStream<Uint8Array>,
  onEvent: (event: ParsedSseEvent) => void,
  signal?: AbortSignal,
): Promise<void> {
  const reader = stream.getReader();
  const decoder = new TextDecoder();
  let buffer = "";
  let finished = false;
  const cancelReader = () => {
    // Cleanup must not replace the original cancellation or parsing error.
    void reader.cancel(signal?.reason).catch(() => undefined);
  };
  signal?.addEventListener("abort", cancelReader, { once: true });

  try {
    signal?.throwIfAborted();
    while (true) {
      const { done, value } = await reader.read();
      signal?.throwIfAborted();
      buffer += decoder.decode(value, { stream: !done });
      let boundary = buffer.search(/\r?\n\r?\n/);
      while (boundary >= 0) {
        signal?.throwIfAborted();
        const separator = buffer.slice(boundary).match(/^\r?\n\r?\n/)?.[0] ?? "\n\n";
        const parsed = parseSseBlock(buffer.slice(0, boundary));
        buffer = buffer.slice(boundary + separator.length);
        if (parsed) onEvent(parsed);
        boundary = buffer.search(/\r?\n\r?\n/);
      }
      if (done) {
        finished = true;
        break;
      }
    }

    signal?.throwIfAborted();
    const parsed = parseSseBlock(buffer);
    if (parsed) onEvent(parsed);
  } finally {
    signal?.removeEventListener("abort", cancelReader);
    if (!finished) cancelReader();
    reader.releaseLock();
  }
}
