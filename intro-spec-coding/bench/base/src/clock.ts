/** Current time source. Inject a fixed clock in tests instead of reading the system time. */
export type Clock = () => Date;

export const systemClock: Clock = () => new Date();
