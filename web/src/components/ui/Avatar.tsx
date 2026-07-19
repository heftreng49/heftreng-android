import Image from "next/image"

interface Props {
  src?:  string
  name?: string
  size?: number
}

export function Avatar({ src, name, size = 40 }: Props) {
  const initials = name
    ?.split(" ")
    .map((w) => w[0])
    .join("")
    .toUpperCase()
    .slice(0, 2) ?? "?"

  if (src) {
    return (
      <Image
        src={src}
        alt={name ?? ""}
        width={size}
        height={size}
        className="rounded-full object-cover"
        style={{ width: size, height: size }}
      />
    )
  }

  return (
    <div
      className="rounded-full flex items-center justify-center font-semibold shrink-0"
      style={{
        width:      size,
        height:     size,
        fontSize:   size * 0.38,
        background: "linear-gradient(135deg, var(--grad-start), var(--grad-end))",
        color:      "#fff",
      }}
    >
      {initials}
    </div>
  )
}
