// The ABI of crates/frameboost-wasm: one JSON-in, JSON-out entry point.

export function bind(exports) {
  const enc = new TextEncoder();
  const dec = new TextDecoder();
  return function call(request) {
    const bytes = enc.encode(JSON.stringify(request));
    const ptr = exports.fb_alloc(bytes.length);
    new Uint8Array(exports.memory.buffer, ptr, bytes.length).set(bytes);
    const packed = exports.fb_invoke(ptr, bytes.length); // frees the request
    const outPtr = Number(packed >> 32n);
    const outLen = Number(packed & 0xffffffffn);
    const text = dec.decode(new Uint8Array(exports.memory.buffer, outPtr, outLen));
    exports.fb_free(outPtr, outLen);
    const reply = JSON.parse(text);
    if ("error" in reply) throw new Error(reply.error);
    return reply.ok;
  };
}

export async function instantiate(module) {
  const instance = await WebAssembly.instantiate(module, {});
  return bind(instance.exports);
}
