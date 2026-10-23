export function compactParams<T extends object>(params: T): Record<string, string | number | boolean> {
  const result: Record<string, string | number | boolean> = {};
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== "") {
      result[key] = value as string | number | boolean;
    }
  });
  return result;
}
