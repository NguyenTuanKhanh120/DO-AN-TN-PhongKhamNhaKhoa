export function TypingIndicator() {
  return (
    <div className="flex items-center gap-1 px-1 py-2" role="status" aria-label="Trợ lý đang trả lời">
      {[0, 1, 2].map((index) => (
        <span
          key={index}
          className="h-2 w-2 animate-bounce rounded-full bg-primary-500"
          style={{ animationDelay: `${index * 120}ms` }}
        />
      ))}
    </div>
  );
}
