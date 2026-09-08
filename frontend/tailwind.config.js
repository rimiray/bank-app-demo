/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{js,ts,jsx,tsx}'],
  theme: {
    // Replace defaults so font-extrabold / sub-12px sizes cannot leak in.
    fontFamily: {
      sans: ['var(--font-sans)', 'system-ui', 'sans-serif'],
      mono: ['var(--font-mono)', 'ui-monospace', 'monospace'],
    },
    fontSize: {
      xs: ['var(--text-xs)', { lineHeight: 'var(--leading-xs)' }],
      sm: ['var(--text-sm)', { lineHeight: 'var(--leading-sm)' }],
      base: ['var(--text-base)', { lineHeight: 'var(--leading-base)' }],
      lg: ['var(--text-lg)', { lineHeight: 'var(--leading-lg)' }],
      xl: ['var(--text-xl)', { lineHeight: 'var(--leading-xl)' }],
      '2xl': ['var(--text-2xl)', { lineHeight: 'var(--leading-2xl)' }],
    },
    fontWeight: {
      normal: '400',
      medium: '500',
      semibold: '600',
      bold: '700',
    },
    extend: {
      colors: {
        bank: {
          teal: '#00A0C8',
          'teal-dark': '#007A9A',
          ink: '#0B1F2A',
          mist: '#E8F4F8',
          sand: '#F5F7F6',
          line: '#C5D5DC',
          success: '#1B8A5A',
          danger: '#C0392B',
          warn: '#C47A12',
        },
      },
      boxShadow: {
        card: '0 18px 40px -24px rgba(11, 31, 42, 0.45)',
        soft: '0 10px 30px -18px rgba(0, 160, 200, 0.35)',
      },
      backgroundImage: {
        mesh: 'radial-gradient(ellipse 80% 60% at 10% -10%, rgba(0,160,200,0.18), transparent 55%), radial-gradient(ellipse 60% 50% at 100% 0%, rgba(11,31,42,0.08), transparent 50%), linear-gradient(180deg, #F5F7F6 0%, #EEF3F5 100%)',
        plastic:
          'linear-gradient(135deg, #0B1F2A 0%, #123447 40%, #007A9A 78%, #00A0C8 100%)',
      },
      keyframes: {
        'fade-up': {
          '0%': { opacity: '0', transform: 'translateY(12px)' },
          '100%': { opacity: '1', transform: 'translateY(0)' },
        },
        'pulse-dot': {
          '0%, 100%': { transform: 'scale(1)', opacity: '1' },
          '50%': { transform: 'scale(1.35)', opacity: '0.55' },
        },
        shimmer: {
          '0%': { backgroundPosition: '200% 0' },
          '100%': { backgroundPosition: '-200% 0' },
        },
      },
      animation: {
        'fade-up': 'fade-up 0.45s ease-out both',
        'pulse-dot': 'pulse-dot 1.6s ease-in-out infinite',
        shimmer: 'shimmer 2.2s linear infinite',
      },
    },
  },
  plugins: [],
}
