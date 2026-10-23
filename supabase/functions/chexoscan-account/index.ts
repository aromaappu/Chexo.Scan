import { createClient } from "npm:@supabase/supabase-js@2";
import { createRemoteJWKSet, jwtVerify } from "npm:jose@5.9.6";

// Only Google-signed, unexpired tokens for THIS application's web OAuth
// client ID may read, create or delete a ChexoScan account.
const jwks = createRemoteJWKSet(new URL("https://www.googleapis.com/oauth2/v3/certs"));

function reply(body: Record<string, unknown>, status = 200): Response {
  return Response.json(body, {
    status,
    headers: { "Cache-Control": "no-store", "Content-Type": "application/json" },
  });
}

Deno.serve(async (req: Request): Promise<Response> => {
  if (req.method !== "POST") return reply({ error: "Method not allowed." }, 405);
  try {
    const bearer = req.headers.get("authorization") || "";
    if (!bearer.startsWith("Bearer ")) return reply({ error: "Google login required." }, 401);

    const oauthClientId = Deno.env.get("GOOGLE_WEB_CLIENT_ID");
    const supabaseUrl = Deno.env.get("SUPABASE_URL");
    const serviceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
    if (!oauthClientId || !supabaseUrl || !serviceKey)
      return reply({ error: "Account service is not configured." }, 503);

    const { payload } = await jwtVerify(bearer.slice(7), jwks, {
      issuer: ["accounts.google.com", "https://accounts.google.com"],
      audience: oauthClientId,
      algorithms: ["RS256"],
    });
    if (payload.email_verified !== true || typeof payload.email !== "string" ||
        typeof payload.sub !== "string" || !payload.sub)
      return reply({ error: "Verified Google email required." }, 401);
    const email = payload.email.trim().toLowerCase();
    const data = await req.json();
    const action = data?.action;
    const db = createClient(supabaseUrl, serviceKey, {
      auth: { autoRefreshToken: false, persistSession: false },
    });
    const { data: existing, error: readError } = await db.from("users")
      .select("username,email").eq("email", email).maybeSingle();
    if (readError) throw readError;

    if (action === "lookup") {
      return existing
        ? reply({ found: true, username: existing.username, email: existing.email })
        : reply({ found: false });
    }
    if (action === "register") {
      if (existing) return reply({ error: "This Gmail is already registered. Please log in." }, 409);
      const username = String(data.username || "").trim();
      if (username.length < 3 || username.length > 60 || !/^[\p{L}\p{N} _.-]+$/u.test(username))
        return reply({ error: "Enter a shop name (3–60 characters)." }, 400);
      const { data: duplicate, error: checkError } = await db.from("users")
        .select("username").ilike("username", username).limit(1);
      if (checkError) throw checkError;
      if (duplicate && duplicate.length) return reply({ error: "Shop name already registered." }, 409);
      const { error } = await db.from("users").insert({ username, email });
      if (error) {
        if (error.code === "23505") return reply({ error: "Account already registered." }, 409);
        throw error;
      }
      return reply({ username, email });
    }
    if (action === "delete") {
      if (!existing) return reply({ deleted: true });
      const { error } = await db.from("users").delete().eq("email", email);
      if (error) throw error;
      return reply({ deleted: true });
    }
    return reply({ error: "Unsupported action." }, 400);
  } catch (error) {
    if (error instanceof Error &&
        (/JWT|JWS|signature|expired|audience|issuer|claims/i.test(error.message))) {
      return reply({ error: "Invalid or expired Google sign-in. Sign in again." }, 401);
    }
    // Never leak database internals to clients.
    console.error("Account service error", error);
    return reply({ error: "Account service temporarily unavailable." }, 500);
  }
});
