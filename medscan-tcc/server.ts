import { config } from "dotenv";
import express from "express";
import http from "http";
import https from "https";
import path from "path";
import { createServer as createViteServer } from "vite";

config({ path: ".env.local" });
config();

async function startServer() {
  const app = express();
  const PORT = 3000;
  const backendUrl = process.env.BACKEND_URL || "http://localhost:8080";

  app.use("/api", (req, res) => {
    const target = new URL(req.originalUrl.replace(/^\/api/, ""), backendUrl);
    const proxyClient = target.protocol === "https:" ? https : http;

    const headers = { ...req.headers };
    delete headers.host;
    delete headers.connection;

    const proxyReq = proxyClient.request(
      {
        protocol: target.protocol,
        hostname: target.hostname,
        port: target.port,
        path: `${target.pathname}${target.search}`,
        method: req.method,
        headers,
      },
      (proxyRes) => {
        res.status(proxyRes.statusCode || 502);

        Object.entries(proxyRes.headers).forEach(([header, value]) => {
          if (value !== undefined && header.toLowerCase() !== "transfer-encoding") {
            res.setHeader(header, value);
          }
        });

        proxyRes.pipe(res);
      }
    );

    proxyReq.on("error", () => {
      if (!res.headersSent) {
        res.status(502).json({
          message: `Não foi possível conectar ao backend em ${backendUrl}`,
        });
      }
    });

    req.pipe(proxyReq);
  });

  if (process.env.NODE_ENV !== "production") {
    const vite = await createViteServer({
      server: { middlewareMode: true },
      appType: "spa",
    });
    app.use(vite.middlewares);
  } else {
    const distPath = path.join(process.cwd(), 'dist');
    app.use(express.static(distPath));
    app.get('*', (req, res) => {
      res.sendFile(path.join(distPath, 'index.html'));
    });
  }

  app.listen(PORT, "0.0.0.0", () => {
    console.log(`Server running on http://localhost:${PORT}`);
    console.log(`Proxying /api/* to ${backendUrl}`);
  });
}

startServer();
