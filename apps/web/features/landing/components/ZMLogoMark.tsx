import Image from 'next/image';

type Props = { size?: number; className?: string };

export default function ZMLogoMark({ size = 16, className }: Props) {
  return (
    <span
      className={className}
      aria-hidden="true"
      style={{
        display: 'inline-block',
        width: size,
        height: size,
        overflow: 'hidden',
        borderRadius: 'inherit',
        lineHeight: 0,
      }}
    >
      <Image
        src="/images/logo.png"
        alt=""
        width={size}
        height={size}
        priority={size <= 16}
        unoptimized
        sizes={`${size}px`}
        style={{
          width: size,
          height: size,
          objectFit: 'contain',
        }}
      />
    </span>
  );
}
