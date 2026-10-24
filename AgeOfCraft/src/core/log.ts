export type LogLevel = "debug" | "info" | "warn" | "error";

export interface LogSink {
  log(message: string): void;
  warn(message: string): void;
  error(message: string): void;
}

/**
 * Prefixed logger over the script console. In Bedrock, console output goes to
 * the content log (Settings > Creator > Content Log); warnings and errors can
 * also appear on screen when the content log GUI is enabled.
 */
export class Logger {
  debugEnabled: boolean;

  constructor(
    private readonly sink: LogSink,
    debug: boolean,
    private readonly prefix = "[AgeOfCraft]",
  ) {
    this.debugEnabled = debug;
  }

  debug(msg: string): void {
    if (this.debugEnabled) this.sink.log(`${this.prefix} ${msg}`);
  }

  info(msg: string): void {
    this.sink.log(`${this.prefix} ${msg}`);
  }

  warn(msg: string): void {
    this.sink.warn(`${this.prefix} ${msg}`);
  }

  error(msg: string, err?: unknown): void {
    this.sink.error(`${this.prefix} ${msg}${err === undefined ? "" : `: ${describeError(err)}`}`);
  }
}

export function describeError(err: unknown): string {
  if (err instanceof Error) return err.stack ? `${err.message}\n${err.stack}` : err.message;
  return String(err);
}
