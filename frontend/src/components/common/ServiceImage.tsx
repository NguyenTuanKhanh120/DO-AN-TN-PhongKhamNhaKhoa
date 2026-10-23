import { ImageIcon } from "lucide-react";
import { useEffect, useState } from "react";
import { cn } from "@/utils/cn";

interface ServiceImageProps {
  src?: string | null;
  alt: string;
  className?: string;
  loading?: "eager" | "lazy";
}

export function ServiceImage({ src, alt, className, loading = "lazy" }: ServiceImageProps) {
  const [failed, setFailed] = useState(false);

  useEffect(() => {
    setFailed(false);
  }, [src]);

  if (!src || failed) {
    return (
      <div
        role="img"
        aria-label={`Chưa có ảnh cho ${alt}`}
        className={cn(
          "grid place-items-center overflow-hidden bg-gradient-to-br from-primary-50 via-white to-cyan-50 text-primary-300",
          className,
        )}
      >
        <ImageIcon aria-hidden="true" className="h-9 w-9" />
      </div>
    );
  }

  return (
    <img
      src={src}
      alt={alt}
      loading={loading}
      decoding="async"
      referrerPolicy="no-referrer"
      onError={() => setFailed(true)}
      className={cn("block object-cover", className)}
    />
  );
}
