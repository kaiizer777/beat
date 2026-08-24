import type { Metadata } from "next";
import { Outfit, Syne } from "next/font/google";
import "./globals.css";
import { Providers } from "@/components/Providers";

const outfit = Outfit({
  subsets: ["latin"],
  variable: "--font-outfit",
  display: "swap",
});

const syne = Syne({
  subsets: ["latin"],
  variable: "--font-brand",
  display: "swap",
  weight: ["700", "800"],
});

export const metadata: Metadata = {
  title: "BEAT — AI News Research Digest",
  description: "Personalized Multi-Cron News & Intelligence Pipeline",
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="en" className={`${outfit.variable} ${syne.variable}`}>
      <body className="font-sans bg-black text-slate-100 antialiased selection:bg-cyan-500 selection:text-white">
        <Providers>{children}</Providers>
      </body>
    </html>
  );
}
