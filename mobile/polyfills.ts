declare global {
  var FinalizationRegistry:
    | (new (cleanupCallback: (heldValue: unknown) => void) => {
        register(target: object, heldValue: unknown, unregisterToken?: object): void;
        unregister(unregisterToken: object): boolean;
      })
    | undefined;
}

if (typeof globalThis.FinalizationRegistry === 'undefined') {
  class NoopFinalizationRegistry {
    constructor(_cleanupCallback: (heldValue: unknown) => void) {}

    register(_target: object, _heldValue: unknown, _unregisterToken?: object) {}

    unregister(_unregisterToken: object) {
      return false;
    }
  }

  globalThis.FinalizationRegistry =
    NoopFinalizationRegistry as typeof globalThis.FinalizationRegistry;
}

export {};
