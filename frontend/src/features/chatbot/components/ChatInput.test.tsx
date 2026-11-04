import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import { ChatInput } from "./ChatInput";

describe("ChatInput", () => {
  afterEach(cleanup);

  it("gửi nội dung hợp lệ bằng phím Enter và xóa input sau thành công", async () => {
    const onSend = vi.fn().mockResolvedValue(true);
    const user = userEvent.setup();
    render(<ChatInput onSend={onSend} />);
    const input = screen.getByLabelText("Nội dung trò chuyện");

    await user.type(input, "Tôi bị đau răng{enter}");

    expect(onSend).toHaveBeenCalledWith("Tôi bị đau răng");
    expect(input).toHaveValue("");
  });

  it("không gửi tin nhắn trống", async () => {
    const onSend = vi.fn().mockResolvedValue(true);
    const user = userEvent.setup();
    render(<ChatInput onSend={onSend} />);

    await user.click(screen.getByRole("button", { name: "Gửi tin nhắn" }));

    expect(await screen.findByText("Vui lòng nhập nội dung cần hỏi")).toBeInTheDocument();
    expect(onSend).not.toHaveBeenCalled();
  });

  it("xóa input ngay khi gửi mà không chờ stream hoàn tất", async () => {
    let resolveSend: ((sent: boolean) => void) | undefined;
    const onSend = vi.fn(() => new Promise<boolean>((resolve) => {
      resolveSend = resolve;
    }));
    const user = userEvent.setup();
    render(<ChatInput onSend={onSend} />);
    const input = screen.getByLabelText("Nội dung trò chuyện");

    await user.type(input, "Bác sĩ Demo còn lịch không?");
    fireEvent.click(screen.getByRole("button", { name: "Gửi tin nhắn" }));

    await waitFor(() => expect(input).toHaveValue(""));
    resolveSend?.(true);
    await waitFor(() => expect(onSend).toHaveBeenCalledTimes(1));
  });

  it("khôi phục nội dung để thử lại khi gửi thất bại", async () => {
    const onSend = vi.fn().mockResolvedValue(false);
    const user = userEvent.setup();
    render(<ChatInput onSend={onSend} />);
    const input = screen.getByLabelText("Nội dung trò chuyện");

    await user.type(input, "Bác sĩ Demo còn lịch không?");
    await user.click(screen.getByRole("button", { name: "Gửi tin nhắn" }));

    await waitFor(() => expect(input).toHaveValue("Bác sĩ Demo còn lịch không?"));
  });
});
