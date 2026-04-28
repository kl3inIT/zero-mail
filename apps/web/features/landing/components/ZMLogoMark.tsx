type Props = { size?: number; className?: string };

export default function ZMLogoMark({ size = 16, className }: Props) {
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 24 24"
      fill="none"
      shapeRendering="geometricPrecision"
      className={className}
      aria-hidden="true"
    >
      <path
        d="M6.2 7.2 H17.8 L8.0 16.8 H17.8"
        stroke="currentColor"
        strokeWidth="2.6"
        strokeLinecap="square"
        strokeLinejoin="miter"
        strokeMiterlimit="2"
      />
      <circle cx="18.6" cy="7.2" r="1.9" fill="currentColor" />
    </svg>
  );
}
