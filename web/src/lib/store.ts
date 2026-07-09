import { useSyncExternalStore } from "react";

/**
 * Minimal observable store — the web equivalent of the Android app's MutableStateFlow.
 * Backs NowVoicing, the study-queue transport state, settings, and auth so React can
 * subscribe via useSyncExternalStore without pulling in a state library.
 */
export class Store<T> {
  private listeners = new Set<() => void>();
  constructor(private value: T) {}

  get = (): T => this.value;

  set = (next: T): void => {
    if (next === this.value) return;
    this.value = next;
    this.emit();
  };

  update = (fn: (prev: T) => T): void => {
    this.set(fn(this.value));
  };

  subscribe = (listener: () => void): (() => void) => {
    this.listeners.add(listener);
    return () => {
      this.listeners.delete(listener);
    };
  };

  private emit = (): void => {
    this.listeners.forEach((l) => l());
  };
}

export function useStore<T>(store: Store<T>): T {
  return useSyncExternalStore(store.subscribe, store.get, store.get);
}
