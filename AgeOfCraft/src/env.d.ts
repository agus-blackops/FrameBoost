// Globals the Bedrock script runtime provides. The project compiles without
// DOM or Node typings so browser/Node-only APIs cannot slip into game code.
declare const console: {
  log(...data: unknown[]): void;
  info(...data: unknown[]): void;
  warn(...data: unknown[]): void;
  error(...data: unknown[]): void;
};
