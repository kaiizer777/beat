import NextAuth from "next-auth";
import { PrismaAdapter } from "@auth/prisma-adapter";
import Resend from "next-auth/providers/resend";
import { prisma } from "@/lib/prisma";
import { SignJWT, jwtVerify } from "jose";

export const { handlers, auth, signIn, signOut } = NextAuth({
  adapter: PrismaAdapter(prisma),
  providers: [
    Resend({
      apiKey: process.env.AUTH_RESEND_KEY,
      from: process.env.RESEND_FROM_EMAIL || "Beat Digest <onboarding@resend.dev>",
    }),
  ],
  session: {
    strategy: "jwt",
  },
  jwt: {
    async encode({ token }) {
      if (!token) return "";
      const secret = new TextEncoder().encode(process.env.AUTH_SECRET);
      return new SignJWT({ ...token })
        .setProtectedHeader({ alg: "HS256" })
        .setIssuedAt()
        .setExpirationTime("30d")
        .sign(secret);
    },
    async decode({ token }) {
      if (!token) return null;
      try {
        const secret = new TextEncoder().encode(process.env.AUTH_SECRET);
        const { payload } = await jwtVerify(token, secret, {
          algorithms: ["HS256"],
        });
        return payload as any;
      } catch (err) {
        return null;
      }
    },
  },
  callbacks: {
    async session({ session, token }) {
      if (token && session.user) {
        session.user.id = token.sub as string;
        const secret = new TextEncoder().encode(process.env.AUTH_SECRET);
        session.accessToken = await new SignJWT({
          sub: token.sub,
          email: token.email,
        })
          .setProtectedHeader({ alg: "HS256" })
          .setIssuedAt()
          .setExpirationTime("30d")
          .sign(secret);
      }
      return session;
    },
    async jwt({ token, user }) {
      if (user) {
        token.sub = user.id;
        token.email = user.email;
      }
      return token;
    },
  },
});

