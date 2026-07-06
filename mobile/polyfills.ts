if (typeof globalThis.FinalizationRegistry === 'undefined') {
  class NoopFinalizationRegistry {
    // eslint-disable-next-line @typescript-eslint/no-useless-constructor
    constructor(_cleanupCallback: (heldValue: unknown) => void) {}

    register(_target: object, _heldValue: unknown, _unregisterToken?: object) {}

    unregister(_unregisterToken: object) {
      return false;
    }
  }

  Object.assign(globalThis, {
    FinalizationRegistry: NoopFinalizationRegistry,
  });
}

export {};
