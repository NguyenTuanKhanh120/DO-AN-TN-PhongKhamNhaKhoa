import { afterEach, describe, expect, it } from "vitest";
import { tokenStore } from "./token-store";

describe("tokenStore", () => {
  afterEach(() => tokenStore.clear());

  it("chỉ giữ access token trong memory và xóa được", () => {
    tokenStore.set("access-token");
    expect(tokenStore.get()).toBe("access-token");
    tokenStore.clear();
    expect(tokenStore.get()).toBeNull();
  });
});
